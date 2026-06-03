package se.liaprojekt.service.ai;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import se.liaprojekt.exception.AzureAssistantException;
import se.liaprojekt.model.Course;
import se.liaprojekt.model.Section;
import se.liaprojekt.repository.CourseRepository;
import se.liaprojekt.service.BlobStorageService;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class VectorStoreService {

    private final BlobStorageService blobStorageService;
    private final CourseRepository courseRepository;

    private final RestTemplate restTemplate;

    @Value("${azure.openai.endpoint}")
    private String endpoint;

    @Value("${azure.openai.api-version}")
    private String apiVersion;

    @Value("${azure.openai.api-key}")
    private String apiKey;

    // =========================
    // INIT VECTOR STORE
    // =========================

    public void initializeCourseVectorStore(Course course) {

        // =========================
        // ALREADY EXISTS
        // =========================

        if (course.getVectorStoreId() != null &&
                !course.getVectorStoreId().isBlank()) {

            log.info(
                    "Vector store already exists for course {}",
                    course.getId()
            );

            return;
        }

        // =========================
        // CREATE VECTOR STORE
        // =========================

        String vectorStoreId =
                createVectorStore(course.getTitle());

        // =========================
        // SAVE VECTOR STORE ID
        // =========================

        course.setVectorStoreId(vectorStoreId);

        courseRepository.save(course);

        log.info(
                "Created vector store {} for course {}",
                vectorStoreId,
                course.getId()
        );

        // =========================
        // UPLOAD PDFs
        // =========================

        for (Section section : course.getSections()) {

            List<BlobStorageService.FileEntry> files =

                    blobStorageService.listFilesBySectionId(
                            section.getId().toString()
                    );

            for (BlobStorageService.FileEntry file : files) {

                uploadSectionPdfToVectorStore(
                        course,
                        file.fileId()
                );
            }
        }
    }

    // =========================
    // CREATE VECTOR STORE
    // =========================

    public String createVectorStore(String courseName) {

        String url =
                endpoint +
                        "/openai/vector_stores?api-version=" +
                        apiVersion;

        HttpHeaders headers = new HttpHeaders();

        headers.set("api-key", apiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = Map.of(
                "name", courseName
        );

        HttpEntity<?> entity =
                new HttpEntity<>(body, headers);

        ResponseEntity<Map<String, Object>> response =
                restTemplate.exchange(
                        url,
                        HttpMethod.POST,
                        entity,
                        new ParameterizedTypeReference<Map<String, Object>>() {}
                );

        Map<String, Object> responseBody =
                response.getBody();

        if (responseBody == null ||
                responseBody.get("id") == null) {

            throw new AzureAssistantException(
                    "Failed to create vector store"
            );
        }

        return responseBody.get("id").toString();
    }

    // =========================
    // UPLOAD PDF
    // =========================

    private void uploadSectionPdfToVectorStore(
            Course course,
            String fileId
    ) {

        try {

            log.info(
                    "Uploading PDF {} to vector store {}",
                    fileId,
                    course.getVectorStoreId()
            );

            // =========================
            // RESOLVE REAL BLOB NAME
            // =========================

            String blobName =
                    blobStorageService.resolveBlobName(
                            fileId
                    );

            // =========================
            // DOWNLOAD FILE FROM BLOB
            // =========================

            byte[] fileBytes =
                    blobStorageService.downloadFileBytes(
                            blobName
                    );

            Map<String, String> tags =
                    blobStorageService.getFileTags(blobName);

            String vectorStoreTagKey = "openAiFileId_" + course.getVectorStoreId();
            String existingOpenAiFileId = tags.get(vectorStoreTagKey);

            if (existingOpenAiFileId != null && !existingOpenAiFileId.isBlank()) {
                log.info("Blob {} already synced with vector store {}", blobName, course.getVectorStoreId());
                return;
            }

            // =========================
            // UPLOAD FILE TO OPENAI
            // =========================

            String openAiFileId =
                    uploadFileToOpenAI(
                            fileBytes,
                            blobName
                    );

            log.info(
                    "Uploaded OpenAI file {}",
                    openAiFileId
            );

            // =========================
            // ADD FILE TO VECTOR STORE
            // =========================

            addFileToVectorStore(
                    course.getVectorStoreId(),
                    openAiFileId
            );

            blobStorageService.addTag(blobName, "openAiFileId_" + course.getVectorStoreId(), openAiFileId);

        } catch (Exception ex) {

            log.error(
                    "Failed to upload PDF {}",
                    fileId,
                    ex
            );

            throw new AzureAssistantException(
                    "Failed to upload PDF to vector store",
                    ex
            );
        }
    }

    private String uploadFileToOpenAI(
            byte[] fileBytes,
            String filename
    ) {

        String url =
                endpoint +
                        "/openai/files?api-version=" +
                        apiVersion;

        HttpHeaders headers = new HttpHeaders();

        headers.set("api-key", apiKey);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        ByteArrayResource resource =
                new ByteArrayResource(fileBytes) {

                    @Override
                    public String getFilename() {
                        return filename;
                    }
                };

        MultiValueMap<String, Object> body =
                new LinkedMultiValueMap<>();

        body.add("purpose", "assistants");
        body.add("file", resource);

        HttpEntity<?> entity =
                new HttpEntity<>(body, headers);

        ResponseEntity<Map<String, Object>> response =
                restTemplate.exchange(
                        url,
                        HttpMethod.POST,
                        entity,
                        new ParameterizedTypeReference<Map<String, Object>>() {}
                );

        Map<String, Object> responseBody =
                response.getBody();

        if (responseBody == null ||
                responseBody.get("id") == null) {

            throw new AzureAssistantException(
                    "Failed to upload file to OpenAI"
            );
        }

        return responseBody.get("id").toString();
    }

    public void removeFileFromVectorStore(
            String vectorStoreId,
            String openAiFileId
    ) {

        String url = endpoint +
                "/openai/vector_stores/" +
                vectorStoreId +
                "/files/" +
                openAiFileId +
                "?api-version=" +
                apiVersion;

        HttpHeaders headers = new HttpHeaders();
        headers.set("api-key", apiKey);

        try {

            restTemplate.exchange(
                    url,
                    HttpMethod.DELETE,
                    new HttpEntity<>(headers),
                    Void.class
            );

            log.info(
                    "Removed file {} from vector store {}",
                    openAiFileId,
                    vectorStoreId
            );

        } catch (Exception ex) {

            log.error(
                    "Failed removing file {} from vector store {}",
                    openAiFileId,
                    vectorStoreId,
                    ex
            );

            throw new AzureAssistantException("Failed to remove file from vector store: " + ex.getMessage());
        }
    }

    // =========================
    // ADD FILE TO VECTOR STORE
    // =========================

    private void addFileToVectorStore(
            String vectorStoreId,
            String fileId
    ) {

        String url =
                endpoint +
                        "/openai/vector_stores/" +
                        vectorStoreId +
                        "/files?api-version=" +
                        apiVersion;

        HttpHeaders headers = new HttpHeaders();

        headers.set("api-key", apiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = Map.of(
                "file_id", fileId
        );

        HttpEntity<?> entity =
                new HttpEntity<>(body, headers);

        ResponseEntity<Map<String, Object>> response =
                restTemplate.exchange(
                        url,
                        HttpMethod.POST,
                        entity,
                        new ParameterizedTypeReference<Map<String, Object>>() {}
                );

        log.info(
                "Vector store response: {}",
                response.getBody()
        );

        log.info(
                "Added file {} to vector store {}",
                fileId,
                vectorStoreId
        );
    }

    public void uploadFileToVectorStore(Course course, String fileId) {

        if (course.getVectorStoreId() == null || course.getVectorStoreId().isBlank()) {
            log.warn("Course {} has no vector store — skipping file upload", course.getId());
            return;
        }

        uploadSectionPdfToVectorStore(course, fileId);
    }
}
