package se.liaprojekt.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import se.liaprojekt.controller.util.SupportedMediaTypeResolver;
import se.liaprojekt.exception.BadRequestException;
import se.liaprojekt.service.BlobStorageService;
import se.liaprojekt.service.BlobStorageService.FileEntry;

import java.io.IOException;
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

    public BlobStorageController(BlobStorageService blobStorageService,
                                 SupportedMediaTypeResolver mediaTypeResolver) {
        this.blobStorageService = blobStorageService;
        this.mediaTypeResolver = mediaTypeResolver;
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
    @PostMapping("/upload")
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

        return ResponseEntity.ok(Map.of(
                "fileId", fileId,
                "originalName", originalFileName
        ));
    }

    // -------------------------------------------------------------------------
    // Download
    // -------------------------------------------------------------------------

    /**
     * Redirects the client to a short-lived SAS URL served via Azure Front Door CDN.
     *
     * <p>The application does not proxy the file bytes — the client fetches
     * directly from the CDN edge node. Suitable for PDFs and direct file downloads.
     * For video playback use {@link #stream} instead.
     *
     * @param fileId Opaque file identifier returned at upload time
     * @return 302 redirect to the Front Door SAS URL, or 404 if no file with that ID exists
     */
    @GetMapping("/download/{fileId}")
    public ResponseEntity<Void> download(@PathVariable String fileId) {
        log.debug("Generating download URL for fileId '{}'", fileId);
        String blobName = blobStorageService.resolveBlobName(fileId);
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(blobStorageService.generateDownloadUrl(blobName))
                .build();
    }

    // -------------------------------------------------------------------------
    // Stream
    // -------------------------------------------------------------------------

    /**
     * Streams a video file with HTTP range request support.
     *
     * <p>The browser's {@code <video>} element sends a {@code Range} header automatically,
     * and this endpoint responds with {@code 206 Partial Content} containing only the
     * requested byte range. This enables seeking, resumable playback, and efficient buffering.
     *
     * <p>If no {@code Range} header is present the full file is streamed with {@code 200 OK}.
     *
     * <p>Only video files are accepted — PDF requests return {@code 400 Bad Request}.
     *
     * @param fileId      Opaque file identifier returned at upload time
     * @param rangeHeader Optional HTTP {@code Range} header (e.g. "bytes=0-10485760")
     * @return 206 Partial Content with the requested byte range, or 400 for non-video files
     */
    @GetMapping("/stream/{fileId}")
    public ResponseEntity<StreamingResponseBody> stream(
            @PathVariable String fileId,
            @RequestHeader(value = HttpHeaders.RANGE, required = false) String rangeHeader) {

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
    @DeleteMapping("/{fileId}")
    public ResponseEntity<String> delete(@PathVariable String fileId) {
        log.debug("Deleting fileId '{}'", fileId);
        String blobName = blobStorageService.resolveBlobName(fileId);
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
    @GetMapping("/{fileId}/tags")
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
    @PatchMapping("/{fileId}/tags/section")
    public ResponseEntity<String> updateSection(
            @PathVariable String fileId,
            @RequestParam String sectionId) {
        log.debug("Updating sectionId tag for fileId '{}' to '{}'", fileId, sectionId);
        String blobName = blobStorageService.resolveBlobName(fileId);
        blobStorageService.updateSectionId(blobName, sectionId);
        return ResponseEntity.ok("Updated sectionId for: " + fileId);
    }
}