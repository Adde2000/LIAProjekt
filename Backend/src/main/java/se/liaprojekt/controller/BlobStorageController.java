package se.liaprojekt.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import se.liaprojekt.controller.util.SupportedMediaTypeResolver;
import se.liaprojekt.exception.BadRequestException;
import se.liaprojekt.service.BlobStorageService;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * REST controller exposing endpoints for managing course material files in Azure Blob Storage.
 *
 * <p>Supports PDF and video files. All endpoints are served under {@code /api/material}.
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
     * Uploads a file to Azure Blob Storage under a server-generated UUID filename.
     * The caller receives the generated filename in the response and must use it
     * for all subsequent operations (download, delete, tags).
     *
     * <p>Only PDF and video files are accepted. The container is determined
     * automatically from the file extension. If a {@code sectionId} is provided,
     * it is stored as a blob tag so the file can be retrieved by section later.
     *
     * @param file      Multipart file from the request (PDF or video)
     * @param sectionId Optional section identifier to tag the file with
     * @return 200 with filename on success, 400 if the file type is unsupported
     */
    @PostMapping("/upload")
    public ResponseEntity<Map<String, String>> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String sectionId) throws IOException {

        String originalFileName = file.getOriginalFilename();
        if (originalFileName == null || !mediaTypeResolver.isSupported(originalFileName)) {
            throw new BadRequestException("Unsupported file type. Allowed: pdf, mp4, mov, avi, mkv.");
        }

        log.debug("Uploading file '{}' with sectionId '{}'", originalFileName, sectionId);
        blobStorageService.uploadFile(
                originalFileName, file.getInputStream(), file.getSize(), sectionId);

        // Return both names so the caller can display the original and reference by generated
        return ResponseEntity.ok(Map.of(
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
     * @param fileName Filename including extension (e.g. "lecture.pdf")
     * @return 302 redirect to the Front Door SAS URL
     */
    @GetMapping("/download/{fileName:.+}")
    public ResponseEntity<Void> download(@PathVariable String fileName) {
        log.debug("Generating download URL for '{}'", fileName);
        String generatedFileName = blobStorageService.resolveFileName(fileName);
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(blobStorageService.generateDownloadUrl(generatedFileName))
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
     * @param fileName    Filename including extension (e.g. "lecture.mp4")
     * @param rangeHeader Optional HTTP {@code Range} header (e.g. "bytes=0-10485760")
     * @return 206 Partial Content with the requested byte range, or 400 for non-video files
     */
    @GetMapping("/stream/{fileName:.+}")
    public ResponseEntity<StreamingResponseBody> stream(
            @PathVariable String fileName,
            @RequestHeader(value = HttpHeaders.RANGE, required = false) String rangeHeader) {

        String generatedFileName = blobStorageService.resolveFileName(fileName);

        if (!mediaTypeResolver.isVideo(generatedFileName)) {
            return ResponseEntity.badRequest().build();
        }

        MediaType contentType = mediaTypeResolver.resolve(generatedFileName);
        long fileSize = blobStorageService.getBlobSize(generatedFileName);

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

        log.debug("Streaming '{}' bytes {}-{}/{}", generatedFileName, rangeStart, end, fileSize);

        StreamingResponseBody body = outputStream ->
                blobStorageService.streamFile(generatedFileName, outputStream, rangeStart, rangeLength);

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
     * @param fileName Filename including extension (e.g. "lecture.pdf")
     * @return 200 with filename on success
     */
    @DeleteMapping("/{fileName:.+}")
    public ResponseEntity<String> delete(@PathVariable String fileName) {
        log.debug("Deleting file '{}'", fileName);
        String generatedFileName = blobStorageService.resolveFileName(fileName);
        blobStorageService.deleteFile(generatedFileName);
        return ResponseEntity.ok("Deleted: " + fileName);
    }

    // -------------------------------------------------------------------------
    // List
    // -------------------------------------------------------------------------

    /**
     * Lists files across both containers, optionally filtered by type.
     *
     * @param type Optional filter: {@code pdf}, {@code video}, or omit for all files
     * @return 200 with list of matching filenames
     */
    @GetMapping("/list")
    public ResponseEntity<List<String>> list(
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
     * @return 200 with list of filenames belonging to the section
     */
    @GetMapping("/list/section/{sectionId}")
    public ResponseEntity<List<String>> listBySection(@PathVariable String sectionId) {
        return ResponseEntity.ok(blobStorageService.listFilesBySectionId(sectionId));
    }

    // -------------------------------------------------------------------------
    // Tags
    // -------------------------------------------------------------------------

    /**
     * Retrieves all blob tags for a file as a key-value map.
     *
     * @param fileName Filename including extension
     * @return 200 with tag map (e.g. {@code {"sectionId": "42"}})
     */
    @GetMapping("/{fileName:.+}/tags")
    public ResponseEntity<Map<String, String>> getTags(@PathVariable String fileName) {
        log.debug("Fetching tags for '{}'", fileName);
        String generatedFileName = blobStorageService.resolveFileName(fileName);
        return ResponseEntity.ok(blobStorageService.getFileTags(generatedFileName));
    }

    /**
     * Updates the {@code sectionId} tag on an existing file.
     * All other tags on the blob are preserved.
     *
     * @param fileName  Filename including extension
     * @param sectionId New section identifier
     * @return 200 with confirmation message
     */
    @PatchMapping("/{fileName:.+}/tags/section")
    public ResponseEntity<String> updateSection(
            @PathVariable String fileName,
            @RequestParam String sectionId) {
        log.debug("Updating sectionId tag for '{}' to '{}'", fileName, sectionId);
        String generatedFileName = blobStorageService.resolveFileName(fileName);
        blobStorageService.updateSectionId(generatedFileName, sectionId);
        return ResponseEntity.ok("Updated sectionId for: " + fileName);
    }
}