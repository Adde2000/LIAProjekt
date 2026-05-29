package se.liaprojekt.service.ai;

import com.azure.core.credential.TokenCredential;
import com.azure.core.credential.TokenRequestContext;
import com.azure.core.util.BinaryData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
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

    private static final String AZURE_SCOPE =
            "https://cognitiveservices.azure.com/.default";

    private final BlobStorageService blobStorageService;
    private final CourseRepository courseRepository;

    private final RestTemplate restTemplate;
    private final TokenCredential credential;

    @Value("${azure.openai.endpoint}")
    private String endpoint;

    @Value("${azure.openai.api-version}")
    private String apiVersion;

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

        headers.setBearerAuth(getToken());

        headers.setContentType(
                MediaType.APPLICATION_JSON
        );

        Map<String, Object> body = Map.of(
                "name", courseName
        );

        HttpEntity<?> entity =
                new HttpEntity<>(body, headers);

        ResponseEntity<Map> response =
                restTemplate.exchange(
                        url,
                        HttpMethod.POST,
                        entity,
                        Map.class
                );

        Map<?, ?> responseBody =
                response.getBody();

        if (responseBody == null ||
                responseBody.get("id") == null) {

            throw new RuntimeException(
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

            log.info(
                    "Added file {} to vector store {}",
                    openAiFileId,
                    course.getVectorStoreId()
            );

        } catch (Exception ex) {

            log.error(
                    "Failed to upload PDF {}",
                    fileId,
                    ex
            );

            throw new RuntimeException(
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

        headers.setBearerAuth(getToken());

        headers.setContentType(
                MediaType.MULTIPART_FORM_DATA
        );

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

        ResponseEntity<Map> response =
                restTemplate.exchange(
                        url,
                        HttpMethod.POST,
                        entity,
                        Map.class
                );

        Map<?, ?> responseBody =
                response.getBody();

        if (responseBody == null ||
                responseBody.get("id") == null) {

            throw new RuntimeException(
                    "Failed to upload file to OpenAI"
            );
        }

        return responseBody.get("id").toString();
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

        headers.setBearerAuth(getToken());

        headers.setContentType(
                MediaType.APPLICATION_JSON
        );

        Map<String, Object> body = Map.of(
                "file_id", fileId
        );

        HttpEntity<?> entity =
                new HttpEntity<>(body, headers);

        restTemplate.exchange(
                url,
                HttpMethod.POST,
                entity,
                Void.class
        );

        log.info(
                "Added file {} to vector store {}",
                fileId,
                vectorStoreId
        );
    }

    // =========================
    // TOKEN
    // =========================

    private String getToken() {

        return credential.getToken(

                new TokenRequestContext()
                        .addScopes(AZURE_SCOPE)

        ).block().getToken();
    }
}
