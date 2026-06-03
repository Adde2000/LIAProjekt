package se.liaprojekt.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import se.liaprojekt.controller.util.Roles;
import se.liaprojekt.controller.util.SupportedMediaTypeResolver;
import se.liaprojekt.exception.BadRequestException;
import se.liaprojekt.model.Course;
import se.liaprojekt.service.BlobStorageService;
import se.liaprojekt.service.BlobStorageService.FileEntry;
import se.liaprojekt.service.CourseService;
import se.liaprojekt.service.StreamTokenService;

import java.io.IOException;
import org.springframework.web.bind.annotation.RequestParam;
import se.liaprojekt.service.ai.VectorStoreService;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * REST controller exposing endpoints for managing course material files in Azure Blob Storage.
 *
 * <p>Supports PDF and video files. All endpoints are served under {@code /api/material}.
 *
 * <p>Files are identified by an opaque {@code fileId} (a UUID) returned at upload time.
 * Callers must store this ID and use it for all subsequent operations (download, stream,
 * delete, tags). The original filename is preserved in blob tags for display purposes only
 * and is never used as an identifier.
 *
 * <p>Upload and delete operations go directly to blob storage via SAS tokens.
 * Download issues a redirect to Azure Front Door CDN to avoid proxying bytes
 * through the application server.
 * Video streaming is served directly from the app using HTTP range requests
 * to support seeking and partial buffering in browser video players.
 */
@RestController
@RequestMapping("/api/material")
public class BlobStorageController {

    private static final Logger log = LoggerFactory.getLogger(BlobStorageController.class);

    /** Default chunk size served per range request: 10MB. */
    private static final long CHUNK_SIZE = 10 * 1024 * 1024;

    private final BlobStorageService blobStorageService;
    private final SupportedMediaTypeResolver mediaTypeResolver;
    private final StreamTokenService streamTokenService;
    private final VectorStoreService vectorStoreService;   // ADD
    private final CourseService courseService;

    public BlobStorageController(BlobStorageService blobStorageService,
                                 SupportedMediaTypeResolver mediaTypeResolver,
                                 StreamTokenService streamTokenService,
                                 VectorStoreService vectorStoreService,    // ADD
                                 CourseService courseService) {
        this.blobStorageService = blobStorageService;
        this.mediaTypeResolver = mediaTypeResolver;
        this.streamTokenService = streamTokenService;
        this.vectorStoreService = vectorStoreService;      // ADD
        this.courseService = courseService;
    }

    // -------------------------------------------------------------------------
    // Upload
    // -------------------------------------------------------------------------

    /**
     * Uploads a file to Azure Blob Storage and returns the opaque {@code fileId}
     * that must be used for all subsequent operations on this file.
     *
     * <p>Only PDF and video files are accepted. The container is determined
     * automatically from the file extension. If a {@code sectionId} is provided,
     * it is stored as a blob tag so the file can be retrieved by section later.
     *
     * @param file      Multipart file from the request (PDF or video)
     * @param sectionId Optional section identifier to tag the file with
     * @return 200 with {@code fileId} and {@code originalName} on success,
     *         400 if the file type is unsupported
     */
    //(Admin/CourseAdmin)
    @PostMapping("/upload")
    @PreAuthorize(Roles.ADMIN_OR_COURSE_ADMIN)
    public ResponseEntity<Map<String, String>> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam String sectionId) throws IOException {

        String originalFileName = file.getOriginalFilename();
        if (originalFileName == null || !mediaTypeResolver.isSupported(originalFileName)) {
            throw new BadRequestException("Unsupported file type. Allowed: pdf, mp4, mov, avi, mkv.");
        }

        log.debug("Uploading file '{}' with sectionId '{}'", originalFileName, sectionId);
        String fileId = blobStorageService.uploadFile(
                originalFileName, file.getInputStream(), file.getSize(), sectionId);

        // Synka PDF till vector store via sectionId → course
        if (originalFileName.toLowerCase().endsWith(".pdf")) {
            try {
                Course course = courseService.getCourseBySection(Long.parseLong(sectionId));
                vectorStoreService.uploadFileToVectorStore(course, fileId);
            } catch (Exception ex) {
                log.warn("Could not sync PDF {} to vector store — blob upload succeeded", fileId);
            }
        }

        return ResponseEntity.ok(Map.of(
                "fileId", fileId,
                "originalName", originalFileName
        ));
    }

    // -------------------------------------------------------------------------
    // Download
    // -------------------------------------------------------------------------

    /**
     * Serves a file for display or download.
     *
     * <p>PDFs are proxied through the app server with {@code Content-Disposition: inline}
     * so the browser renders them directly in an {@code <iframe>} or tab rather than
     * triggering a file download. Proxying is necessary because Azure Front Door does not
     * forward SAS response-header overrides (rscd), making it impossible to control
     * Content-Disposition via a CDN redirect for PDFs.
     *
     * <p>Non-PDF files (videos, etc.) are not expected to use this endpoint — they are
     * handled by {@link #streamToken} and {@link #stream}. If a non-PDF fileId is passed,
     * a CDN URL is returned as JSON for the caller to handle.
     *
     * @param fileId Opaque file identifier returned at upload time
     * @return For PDFs: 200 with the file bytes and {@code Content-Disposition: inline}.
     *         For other types: 200 with {@code {"url": "<CDN SAS URL>"}}.
     */
    //ALL
    @GetMapping("/download/{fileId}")
    public ResponseEntity<?> download(@PathVariable String fileId) {
        log.debug("Downloading fileId '{}'", fileId);
        String blobName = blobStorageService.resolveBlobName(fileId);

        //TODO Fix so that the pdf can be displayed on the frontend
//        if (getExtension(blobName).equals("pdf")) {
//            String originalName = blobStorageService.getFileTags(blobName)
//                    .getOrDefault("originalName", blobName);
//
//            StreamingResponseBody body = outputStream ->
//                    blobStorageService.streamFile(blobName, outputStream, 0,
//                            blobStorageService.getBlobSize(blobName));
//
//            return ResponseEntity.ok()
//                    .contentType(MediaType.APPLICATION_PDF)
//                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + originalName + "\"")
//                    .header(HttpHeaders.CONTENT_LENGTH,
//                            String.valueOf(blobStorageService.getBlobSize(blobName)))
//                    .body(body);
//        }

        // Non-PDF fallback — return CDN URL for the caller to handle
        String url = blobStorageService.generateDownloadUrl(blobName).toString();
        return ResponseEntity.ok(Map.of("url", url));
    }

    // -------------------------------------------------------------------------
    // Stream
    // -------------------------------------------------------------------------

    /**
     * Issues a short-lived signed token that authorises one streaming session for a video file.
     *
     * <p>Because the browser's {@code <video src="...">} element cannot attach an
     * {@code Authorization} header to its range requests, the normal bearer-token auth
     * flow does not work for video streaming. The frontend must therefore:
     * <ol>
     *   <li>Call this endpoint (with the normal auth header) to obtain a stream token.</li>
     *   <li>Append the token as {@code ?streamToken=<value>} to the stream URL.</li>
     *   <li>Set that URL as the {@code src} of the {@code <video>} element.</li>
     * </ol>
     *
     * <p>Tokens are valid for a short window (default 5 minutes, configurable via
     * {@code stream.token.ttl-seconds}) and are scoped to a single {@code fileId}.
     *
     * @param fileId Opaque file identifier returned at upload time
     * @return 200 with {@code {"streamToken": "<token>", "streamUrl": "...", "expiresIn": <seconds>}}
     */
    //ALL
    @GetMapping("/stream-token/{fileId}")
    public ResponseEntity<Map<String, Object>> streamToken(@PathVariable String fileId) {
        // Resolve early so we return 404 immediately if the file doesn't exist
        String blobName = blobStorageService.resolveBlobName(fileId);
        if (!mediaTypeResolver.isVideo(blobName)) {
            throw new BadRequestException("Stream tokens can only be issued for video files. Use /download for PDFs.");
        }
        String token = streamTokenService.issue(fileId);
        String url = "/api/material/stream/%s?streamToken=%s".formatted(fileId, token);
        log.debug("Issued stream token for fileId '{}'", fileId);
        return ResponseEntity.ok(Map.of(
                "streamToken", token,
                "streamUrl",   url,
                "expiresIn",   streamTokenService.getTtlSeconds()
        ));
    }

    /**
     * Streams a video file with HTTP range request support.
     *
     * <p>This endpoint is intentionally excluded from Spring Security's bearer-token
     * filter chain (configured in {@code SecurityConfig}) because the browser's
     * {@code <video>} element cannot attach an {@code Authorization} header to range
     * requests. Authentication is instead provided by the {@code streamToken} query
     * parameter, which must be a valid token previously issued by
     * {@link #streamToken(String)}.
     *
     * <p>The endpoint responds with {@code 206 Partial Content} for range requests and
     * {@code 200 OK} when no {@code Range} header is present.
     *
     * <p>Only video files are accepted — PDF requests return {@code 400 Bad Request}.
     *
     * @param fileId      Opaque file identifier returned at upload time
     * @param streamToken Signed token issued by {@link #streamToken(String)}
     * @param rangeHeader Optional HTTP {@code Range} header (e.g. "bytes=0-10485760")
     * @return 206 Partial Content with the requested byte range, or 400/401 on error
     */
    //ALL
    @GetMapping("/stream/{fileId}")
    public ResponseEntity<StreamingResponseBody> stream(
            @PathVariable String fileId,
            @RequestParam String streamToken,
            @RequestHeader(value = HttpHeaders.RANGE, required = false) String rangeHeader) {

        streamTokenService.validate(streamToken, fileId);
        String blobName = blobStorageService.resolveBlobName(fileId);

        if (!mediaTypeResolver.isVideo(blobName)) {
            return ResponseEntity.badRequest().build();
        }

        MediaType contentType = mediaTypeResolver.resolve(blobName);
        long fileSize = blobStorageService.getBlobSize(blobName);

        // Default to full file if no Range header is provided
        long start = 0;
        long end = fileSize - 1;

        if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
            String[] parts = rangeHeader.substring(6).split("-");
            start = Long.parseLong(parts[0]);

            // If end is omitted, serve one chunk from the start position
            end = parts.length > 1 && !parts[1].isEmpty()
                    ? Long.parseLong(parts[1])
                    : Math.min(start + CHUNK_SIZE - 1, fileSize - 1);
        }

        final long rangeStart = start;
        final long rangeLength = end - start + 1;

        log.debug("Streaming fileId '{}' ({}) bytes {}-{}/{}", fileId, blobName, rangeStart, end, fileSize);

        StreamingResponseBody body = outputStream ->
                blobStorageService.streamFile(blobName, outputStream, rangeStart, rangeLength);

        return ResponseEntity
                .status(rangeHeader != null ? HttpStatus.PARTIAL_CONTENT : HttpStatus.OK)
                .header(HttpHeaders.CONTENT_RANGE,
                        "bytes %d-%d/%d".formatted(rangeStart, end, fileSize))
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(rangeLength))
                .contentType(contentType)
                .body(body);
    }

    // -------------------------------------------------------------------------
    // Delete
    // -------------------------------------------------------------------------

    /**
     * Deletes a file from its container in Azure Blob Storage.
     *
     * @param fileId Opaque file identifier returned at upload time
     * @return 200 on success, 404 if no file with that ID exists
     */
    //(Admin/CourseAdmin)
    @DeleteMapping("/{fileId}")
    @PreAuthorize(Roles.ADMIN_OR_COURSE_ADMIN)
    public ResponseEntity<String> delete(@PathVariable String fileId) {
        log.debug("Deleting fileId '{}'", fileId);
        String blobName = blobStorageService.resolveBlobName(fileId);

        // Ta bort från vector store om filen är en PDF
        Map<String, String> tags = blobStorageService.getFileTags(blobName);
        log.info("Blob tags: {}", tags);
        String sectionId = tags.get("sectionId");

        if (sectionId != null) {
            try {
                Course course = courseService.getCourseBySection(Long.parseLong(sectionId));
                String vectorStoreId = course.getVectorStoreId();

                if (vectorStoreId != null) {
                    String openAiFileId = tags.get("openAiFileId_" + vectorStoreId);

                    if (openAiFileId != null) {
                        log.info("Removing OpenAI file {} from vector store {}", openAiFileId, vectorStoreId);
                        vectorStoreService.removeFileFromVectorStore(vectorStoreId, openAiFileId);
                    }
                }
            } catch (Exception ex) {
                log.error("Failed removing OpenAI file from vector store", ex);
            }
        }

        blobStorageService.deleteFile(blobName);
        return ResponseEntity.ok("Deleted: " + fileId);
    }

    // -------------------------------------------------------------------------
    // List
    // -------------------------------------------------------------------------

    /**
     * Lists files across both containers, optionally filtered by type.
     * Each entry contains the {@code fileId} and the original filename.
     *
     * @param type Optional filter: {@code pdf}, {@code video}, or omit for all files
     * @return 200 with list of {@link FileEntry} objects
     */
    //TODO Should this exist?
    @GetMapping("/list")
    public ResponseEntity<List<FileEntry>> list(
            @RequestParam(required = false, defaultValue = "all") String type) {
        Set<String> extensions = mediaTypeResolver.extensionsForType(type);
        return ResponseEntity.ok(blobStorageService.listFiles(extensions));
    }

    /**
     * Lists all files tagged with the given {@code sectionId} across both containers.
     *
     * <p>Uses Azure's server-side blob tag index for efficient filtering —
     * no client-side iteration required.
     *
     * @param sectionId Section identifier to filter by
     * @return 200 with list of {@link FileEntry} objects belonging to the section
     */
    //ALL
    @GetMapping("/list/section/{sectionId}")
    public ResponseEntity<List<FileEntry>> listBySection(@PathVariable String sectionId) {
        return ResponseEntity.ok(blobStorageService.listFilesBySectionId(sectionId));
    }

    // -------------------------------------------------------------------------
    // Tags
    // -------------------------------------------------------------------------

    /**
     * Retrieves all blob tags for a file as a key-value map.
     *
     * @param fileId Opaque file identifier returned at upload time
     * @return 200 with tag map (e.g. {@code {"sectionId": "42", "originalName": "lecture.pdf"}})
     */
    //Admin TODO behövs denna?
    @GetMapping("/{fileId}/tags")
    @PreAuthorize(Roles.ADMIN)
    public ResponseEntity<Map<String, String>> getTags(@PathVariable String fileId) {
        log.debug("Fetching tags for fileId '{}'", fileId);
        String blobName = blobStorageService.resolveBlobName(fileId);
        return ResponseEntity.ok(blobStorageService.getFileTags(blobName));
    }

    /**
     * Updates the {@code sectionId} tag on an existing file.
     * All other tags on the blob are preserved.
     *
     * @param fileId    Opaque file identifier returned at upload time
     * @param sectionId New section identifier
     * @return 200 with confirmation message
     */
    //(Admin/CourseAdmin)
    @PatchMapping("/{fileId}/tags/section")
    @PreAuthorize(Roles.ADMIN_OR_COURSE_ADMIN)
    public ResponseEntity<String> updateSection(
            @PathVariable String fileId,
            @RequestParam String sectionId) {
        log.debug("Updating sectionId tag for fileId '{}' to '{}'", fileId, sectionId);
        String blobName = blobStorageService.resolveBlobName(fileId);
        blobStorageService.updateSectionId(blobName, sectionId);
        return ResponseEntity.ok("Updated sectionId for: " + fileId);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private String getExtension(String blobName) {
        int dot = blobName.lastIndexOf('.');
        return dot >= 0 ? blobName.substring(dot + 1).toLowerCase() : "";
    }
}