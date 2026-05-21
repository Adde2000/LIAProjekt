package se.liaprojekt.service;

import com.azure.core.util.Context;
import com.azure.storage.blob.*;
import com.azure.storage.blob.models.*;
import com.azure.storage.blob.sas.BlobSasPermission;
import com.azure.storage.blob.sas.BlobServiceSasSignatureValues;
import com.azure.storage.common.StorageSharedKeyCredential;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import se.liaprojekt.exception.BlobOperationException;
import se.liaprojekt.exception.BlobStorageExceptionHandler;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class BlobStorageService {

    private static final Logger log = LoggerFactory.getLogger(BlobStorageService.class);

    /** Default chunk size for streaming responses: 10MB. */
    private static final long CHUNK_SIZE = 10 * 1024 * 1024;

    private final BlobContainerClient pdfContainerClient;
    private final BlobContainerClient videoContainerClient;
    private final String accountName;
    private final String pdfContainerName;
    private final String videoContainerName;
    private final long sasExpiryMinutes;
    private final String frontDoorEndpoint;

    /**
     * Constructs the service, initialises both container clients, and validates
     * that both containers exist in Azure before the application starts accepting requests.
     *
     * @param accountName       Azure storage account name
     * @param accountKey        Azure storage account key (injected from environment variable)
     * @param pdfContainerName  Name of the container holding PDF files
     * @param videoContainerName Name of the container holding video files
     * @param sasExpiryMinutes  Lifetime of generated SAS tokens in minutes
     * @param frontDoorEndpoint Absolute HTTPS URL of the Azure Front Door endpoint
     * @throws IllegalStateException if the Front Door endpoint is not absolute HTTPS,
     *                               or if either container does not exist
     */
    public BlobStorageService(
            @Value("${spring.cloud.azure.storage.blob.account-name}") String accountName,
            @Value("${spring.cloud.azure.storage.account-key}") String accountKey,
            @Value("${spring.cloud.azure.storage.container-name-pdf}") String pdfContainerName,
            @Value("${spring.cloud.azure.storage.container-name-video}") String videoContainerName,
            @Value("${spring.cloud.azure.storage.sas-expiry-minutes}") long sasExpiryMinutes,
            @Value("${spring.cloud.azure.storage.frontdoor-endpoint}") String frontDoorEndpoint,
            @Value("${test.properties}") boolean isTesting) {

        if (!frontDoorEndpoint.startsWith("https://")) {
            throw new IllegalStateException(
                    "azure.storage.frontdoor-endpoint must be an absolute HTTPS URL, got: '%s'"
                            .formatted(frontDoorEndpoint));
        }

        this.accountName = accountName;
        this.pdfContainerName = pdfContainerName;
        this.videoContainerName = videoContainerName;
        this.sasExpiryMinutes = sasExpiryMinutes;

        // Strip any accidental trailing slash to avoid double-slash URLs
        this.frontDoorEndpoint = frontDoorEndpoint.replaceAll("/+$", "");
        StorageSharedKeyCredential sharedKeyCredential = new StorageSharedKeyCredential(accountName, accountKey);

        // A single BlobServiceClient is reused to build both container clients
        BlobServiceClient serviceClient = new BlobServiceClientBuilder()
                .endpoint("https://%s.blob.core.windows.net".formatted(accountName))
                .credential(sharedKeyCredential)
                .buildClient();

        this.pdfContainerClient = serviceClient.getBlobContainerClient(pdfContainerName);
        this.videoContainerClient = serviceClient.getBlobContainerClient(videoContainerName);

        // Fail fast on startup if either container is misconfigured or missing
        //Dont't validate if running tests
        if (!isTesting) {
            validateContainer(pdfContainerClient, pdfContainerName);
            validateContainer(videoContainerClient, videoContainerName);
        }

    }

    // -------------------------------------------------------------------------
    // Public operations
    // -------------------------------------------------------------------------

    /**
     * Uploads a file to the appropriate container based on its extension under a server-generated UUID filename.
     * The original filename is preserved as a blob tag ({@code originalName}) so it can
     * be displayed in the UI without being used for storage or retrieval.
     * If a {@code sectionId} is provided, it is written as a blob tag after upload,
     * allowing files to be queried by section later.
     *
     * @param originalFileName  Original filename from the multipart request, used only
     *                          for extension extraction and tagging
     * @param data      Input stream of file bytes
     * @param length    Total byte length of the file
     * @param sectionId Optional section identifier to tag the blob with; may be null
     */
    public void uploadFile(String originalFileName, InputStream data,
                           long length, String sectionId) {
        try {
            String extension = getExtension(originalFileName);
            // Generate a unique filename — UUID ensures no collisions and no guessable names
            String generatedFileName = UUID.randomUUID() + "." + extension;

            // SAS token requires both write and tag permissions for this operation
            BlobClient client = sasClient(generatedFileName,
                    new BlobSasPermission()
                            .setWritePermission(true)
                            .setTagsPermission(true));

            client.upload(data, length, true); // true = overwrite if exists

            // Store both the original name and sectionId as tags
            Map<String, String> tags = new HashMap<>();
            tags.put("originalName", originalFileName);
            if (sectionId != null) {
                tags.put("sectionId", sectionId);
            }
            client.setTags(tags);

            log.debug("Stored '{}' as '{}'", originalFileName, generatedFileName);
        } catch (BlobStorageException ex) {
            throw translateException(ex);
        }
    }

    /**
     * Generates a short-lived SAS URL pointing to the Azure Front Door CDN endpoint.
     * The client is redirected to this URL and retrieves the file directly from the CDN,
     * avoiding proxying bytes through the application server.
     *
     * @param fileName Filename including extension
     * @return Absolute URI the client should be redirected to
     * @throws IllegalStateException if the generated URL is not absolute
     */
    public URI generateDownloadUrl(String fileName) {
        try {
            String sasToken = generateSasToken(
                    fileName, new BlobSasPermission().setReadPermission(true));
            String containerName = resolveContainerName(fileName);

            String rawUrl = "%s/%s/%s?%s"
                    .formatted(frontDoorEndpoint, containerName, fileName, sasToken);

            URI uri = URI.create(rawUrl);
            if (!uri.isAbsolute()) {
                throw new IllegalStateException(
                        "Generated download URL is not absolute: '%s'. Check frontdoor-endpoint config."
                                .formatted(rawUrl));
            }
            return uri;
        } catch (BlobStorageException ex) {
            throw translateException(ex);
        }
    }

    /**
     * Streams a byte range of a video file directly from blob storage to the output stream.
     * Used to support HTTP range requests, enabling seeking and partial buffering in video players.
     *
     * @param fileName     Filename including extension
     * @param outputStream Stream to write the byte range into
     * @param offset       Start byte position (inclusive)
     * @param length       Number of bytes to read from the offset
     */
    public void streamFile(String fileName, OutputStream outputStream,
                           long offset, long length) {
        try {
            BlobRange range = new BlobRange(offset, length);

            // Retry up to 3 times on transient network errors mid-stream
            DownloadRetryOptions retryOptions = new DownloadRetryOptions()
                    .setMaxRetryRequests(3);

            resolveContainer(fileName)
                    .getBlobClient(fileName)
                    .downloadStreamWithResponse(
                            outputStream, range, retryOptions, null, false, null, Context.NONE);
        } catch (BlobStorageException ex) {
            throw translateException(ex);
        }
    }

    /**
     * Returns the total size in bytes of a blob. Used by the stream endpoint
     * to build the {@code Content-Range} response header.
     *
     * @param fileName Filename including extension
     * @return Blob size in bytes
     */
    public long getBlobSize(String fileName) {
        try {
            return resolveContainer(fileName)
                    .getBlobClient(fileName)
                    .getProperties()
                    .getBlobSize();
        } catch (BlobStorageException ex) {
            throw translateException(ex);
        }
    }

    /**
     * Deletes a blob from its container.
     *
     * @param fileName Filename including extension
     */
    public void deleteFile(String fileName) {
        try {
            sasClient(fileName, new BlobSasPermission().setDeletePermission(true))
                    .delete();
        } catch (BlobStorageException ex) {
            throw translateException(ex);
        }
    }

    /**
     * Lists blobs across both containers, filtered to only include files whose
     * extension is present in {@code allowedExtensions}. Returning the
     * user-facing original filename from each blob's {@code originalName} tag.
     * Falls back to the generated filename if the tag is missing.
     *
     * <p>Only the containers relevant to the requested extensions are queried —
     * e.g. {@code ?type=pdf} will not hit the video container at all.
     *
     * @param allowedExtensions Set of lowercase extensions to include (e.g. "pdf", "mp4")
     * @return Combined list of matching filenames from both containers
     */
    public List<String> listFiles(Set<String> allowedExtensions) {
        try {
            Stream<BlobItem> pdfs = allowedExtensions.contains("pdf")
                    ? pdfContainerClient.listBlobs().stream()
                    : Stream.empty();

            Stream<BlobItem> videos = allowedExtensions.stream()
                    .anyMatch(e -> Set.of("mp4", "mov", "avi", "mkv").contains(e))
                    ? videoContainerClient.listBlobs().stream()
                    : Stream.empty();

            return Stream.concat(pdfs, videos)
                    .filter(blob -> allowedExtensions.contains(getExtension(blob.getName())))
                    .map(blob -> fetchOriginalName(resolveContainer(blob.getName()), blob.getName()))
                    .collect(Collectors.toList());
        } catch (BlobStorageException ex) {
            throw translateException(ex);
        }
    }

    /**
     * Queries both containers for blobs tagged with the given {@code sectionId}.
     * Uses Azure's server-side tag index — only blobs matching the tag are returned,
     * no client-side filtering needed.
     *
     * <p>Note: tag indexing is eventually consistent; a file uploaded with a sectionId
     * tag may not appear in results for a few seconds after upload.
     *
     * @param sectionId The section identifier to filter by
     * @return Filenames of all blobs tagged with this sectionId, across both containers
     */
    public List<String> listFilesBySectionId(String sectionId) {
        try {
            // Azure tag query syntax uses double-quoted key and single-quoted value
            String query = "\"sectionId\" = '%s'".formatted(sectionId);

            Stream<TaggedBlobItem> fromPdf = pdfContainerClient
                    .findBlobsByTags(query).stream();

            Stream<TaggedBlobItem> fromVideo = videoContainerClient
                    .findBlobsByTags(query).stream();

            return Stream.concat(fromPdf, fromVideo)
                    .map(blob -> fetchOriginalName(resolveContainer(blob.getName()), blob.getName()))
                    .collect(Collectors.toList());
        } catch (BlobStorageException ex) {
            throw translateException(ex);
        }
    }

    /**
     * Retrieves all tags for a given blob as a key-value map.
     *
     * @param fileName Filename including extension
     * @return Map of tag key-value pairs (e.g. {"sectionId": "42"})
     */
    public Map<String, String> getFileTags(String fileName) {
        try {
            return resolveContainer(fileName)
                    .getBlobClient(fileName)
                    .getTags();
        } catch (BlobStorageException ex) {
            throw translateException(ex);
        }
    }

    /**
     * Updates the {@code sectionId} tag on an existing blob.
     * All other existing tags are preserved.
     *
     * @param fileName  Filename including extension
     * @param sectionId New section identifier value
     */
    public void updateSectionId(String fileName, String sectionId) {
        try {
            BlobClient client = resolveContainer(fileName).getBlobClient(fileName);

            // Fetch and merge existing tags to avoid overwriting unrelated tag entries
            Map<String, String> existing = client.getTags();
            existing.put("sectionId", sectionId);
            client.setTags(existing);
        } catch (BlobStorageException ex) {
            throw translateException(ex);
        }
    }

    /**
     * Resolves a user-facing original filename to the internal UUID-based filename
     * by querying the blob tag index for a matching {@code originalName} tag.
     *
     * <p>Searches both containers and returns the first match. Throws a
     * {@link BlobOperationException} with status 404 if no match is found.
     *
     * @param originalName The original filename as supplied by the user
     * @return The internal UUID-based filename the blob is stored under
     * @throws BlobOperationException if no blob with the given originalName tag exists
     */
    public String resolveFileName(String originalName) {
        try {
            String query = "\"originalName\" = '%s'".formatted(originalName);

            return Stream.of(pdfContainerClient, videoContainerClient)
                    .flatMap(container -> container.findBlobsByTags(query).stream())
                    .map(TaggedBlobItem::getName)
                    .findFirst()
                    .orElseThrow(() -> new BlobOperationException(
                            "No file found with name: " + originalName, 404, "BlobNotFound"));
        } catch (BlobStorageException ex) {
            throw translateException(ex);
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Validates that a container exists in Azure, failing fast on startup if not.
     *
     * @param client Container client to check
     * @param name   Container name used in the error message
     */
    private void validateContainer(BlobContainerClient client, String name) {
        if (!client.exists()) {
            throw new IllegalStateException(
                    "Container '%s' does not exist.".formatted(name));
        }
    }

    /**
     * Resolves the correct {@link BlobContainerClient} for the given filename
     * based on its file extension.
     *
     * @param fileName Filename including extension
     * @return The PDF or video container client
     * @throws BlobOperationException if the extension is not supported
     */
    private BlobContainerClient resolveContainer(String fileName) {
        return switch (getExtension(fileName)) {
            case "pdf"                    -> pdfContainerClient;
            case "mp4", "mov", "avi",
                 "mkv"                   -> videoContainerClient;
            default -> throw new BlobOperationException(
                    "Unsupported file type: " + getExtension(fileName), 400, "UnsupportedFileType");
        };
    }

    /**
     * Resolves the container name string for a filename, used when constructing
     * SAS URLs where the name is needed as a path segment rather than a client object.
     *
     * @param fileName Filename including extension
     * @return Container name string
     */
    private String resolveContainerName(String fileName) {
        return switch (getExtension(fileName)) {
            case "pdf"                    -> pdfContainerName;
            case "mp4", "mov", "avi",
                 "mkv"                   -> videoContainerName;
            default -> throw new BlobOperationException(
                    "Unsupported file type: " + getExtension(fileName), 400, "UnsupportedFileType");
        };
    }

    /**
     * Generates a SAS token scoped to a single blob with the specified permissions.
     * A 5-minute start-time buffer is applied to tolerate clock skew between
     * the application server and Azure.
     *
     * @param fileName    Filename the token is scoped to
     * @param permissions Permissions to grant (read, write, delete, tags, etc.)
     * @return SAS token query string
     */
    private String generateSasToken(String fileName, BlobSasPermission permissions) {
        BlobServiceSasSignatureValues sasValues = new BlobServiceSasSignatureValues(
                OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(sasExpiryMinutes),
                permissions)
                .setStartTime(OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(5));

        return resolveContainer(fileName).getBlobClient(fileName).generateSas(sasValues);
    }

    /**
     * Builds a {@link BlobClient} authenticated via a pre-signed SAS URL.
     * The client itself holds no credential — Azure validates the SAS token
     * embedded in the URL on each request.
     *
     * @param fileName    Filename to build the client for
     * @param permissions Permissions to include in the SAS token
     * @return SAS-authenticated blob client
     */
    private BlobClient sasClient(String fileName, BlobSasPermission permissions) {
        String sasToken = generateSasToken(fileName, permissions);
        String containerName = resolveContainerName(fileName);
        String sasUrl = "https://%s.blob.core.windows.net/%s/%s?%s"
                .formatted(accountName, containerName, fileName, sasToken);
        return new BlobClientBuilder().endpoint(sasUrl).buildClient();
    }

    /**
     * Extracts the lowercase file extension from a filename.
     *
     * @param fileName Filename including extension
     * @return Lowercase extension without leading dot, or empty string if none found
     */
    private String getExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot >= 0 ? fileName.substring(dot + 1).toLowerCase() : "";
    }

    /**
     * Translates a {@link BlobStorageException} from the Azure SDK into a
     * {@link BlobOperationException} that Spring's {@link BlobStorageExceptionHandler}
     * can intercept and map to an appropriate HTTP response.
     *
     * @param ex The Azure SDK exception
     * @return A Spring-friendly runtime exception with status code and error code
     */
    private BlobOperationException translateException(BlobStorageException ex) {
        log.error("Azure Blob Storage error — HTTP {}: {}", ex.getStatusCode(), ex.getMessage());
        return new BlobOperationException(
                ex.getMessage(),
                ex.getStatusCode(),
                ex.getErrorCode() != null ? ex.getErrorCode().toString() : "unknown"
        );
    }

    /**
     * Fetches the {@code originalName} tag for a blob, falling back to the
     * generated filename if the tag is absent or the fetch fails.
     *
     * @param container  The container client the blob lives in
     * @param generatedName The internal UUID-based blob filename
     * @return The original filename, or generatedName if the tag is unavailable
     */
    private String fetchOriginalName(BlobContainerClient container, String generatedName) {
        try {
            Map<String, String> tags = container.getBlobClient(generatedName).getTags();
            return tags.getOrDefault("originalName", generatedName);
        } catch (BlobStorageException ex) {
            log.warn("Could not fetch tags for '{}', falling back to generated name. Error: {}",
                    generatedName, ex.getMessage());
            return generatedName;
        }
    }
}