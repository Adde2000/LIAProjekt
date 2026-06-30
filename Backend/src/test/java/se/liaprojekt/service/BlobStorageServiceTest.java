package se.liaprojekt.service;

import com.azure.core.http.rest.PagedIterable;
import com.azure.core.util.Context;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobClientBuilder;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.models.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import se.liaprojekt.exception.BlobOperationException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link BlobStorageService}.
 *
 * SETUP REQUIRED — place this file in:
 *   src/test/resources/mockito-extensions/org.mockito.plugins.MockMaker
 * with the single line:
 *   mock-maker-inline
 *
 * This enables Mockito to mock final classes (BlobStorageException, PagedIterable, BlobItem).
 * Without it, mocking those classes causes UnfinishedStubbingException.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BlobStorageServiceTest {

    private static final String ACCOUNT_NAME    = "testaccount";
    private static final String PDF_CONTAINER   = "pdfs";
    private static final String VIDEO_CONTAINER = "videos";
    private static final long   SAS_EXPIRY      = 60L;
    private static final String FRONT_DOOR      = "https://cdn.example.com";

    @Mock BlobContainerClient pdfContainerClient;
    @Mock BlobContainerClient videoContainerClient;
    @Mock BlobClient          blobClient;

    BlobStorageService service;

    @BeforeEach
    void setUp() {
        service = new BlobStorageService(
                pdfContainerClient,
                videoContainerClient,
                ACCOUNT_NAME,
                PDF_CONTAINER,
                VIDEO_CONTAINER,
                SAS_EXPIRY,
                FRONT_DOOR);
    }

    // =========================================================================
    // uploadFile
    // =========================================================================

    @Nested @DisplayName("uploadFile")
    class UploadFileTests {

        @Test @DisplayName("Returns a UUID-formatted fileId")
        void returnsUuidFileId() {
            when(pdfContainerClient.getBlobClient(anyString())).thenReturn(blobClient);
            when(blobClient.generateSas(any())).thenReturn("token=abc");

            try (MockedConstruction<BlobClientBuilder> ignored = stubbedBuilder()) {
                String fileId = service.uploadFile("doc.pdf",
                        new ByteArrayInputStream(new byte[]{1}), 1L, null, false);
                assertThat(fileId).matches(
                        "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
            }
        }

        @Test @DisplayName("Sets originalName (Base64-encoded) and sectionId tags")
        void setsTagsWithSectionId() {
            when(pdfContainerClient.getBlobClient(anyString())).thenReturn(blobClient);
            when(blobClient.generateSas(any())).thenReturn("token=abc");

            try (MockedConstruction<BlobClientBuilder> ignored = stubbedBuilder()) {
                service.uploadFile("rapport.pdf",
                        new ByteArrayInputStream(new byte[]{1}), 1L, "42", false);
                verify(blobClient).setTags(argThat(tags ->
                        tags.containsKey("originalName") && "42".equals(tags.get("sectionId"))));
            }
        }

        @Test @DisplayName("Omits sectionId tag when sectionId is null")
        void omitsSectionIdTagWhenNull() {
            when(pdfContainerClient.getBlobClient(anyString())).thenReturn(blobClient);
            when(blobClient.generateSas(any())).thenReturn("token=abc");

            try (MockedConstruction<BlobClientBuilder> ignored = stubbedBuilder()) {
                service.uploadFile("rapport.pdf",
                        new ByteArrayInputStream(new byte[]{1}), 1L, null, false);
                verify(blobClient).setTags(argThat(tags -> !tags.containsKey("sectionId")));
            }
        }

        @Test @DisplayName("Base64-encodes non-ASCII originalName")
        void encodesOriginalNameAsBase64() {
            when(pdfContainerClient.getBlobClient(anyString())).thenReturn(blobClient);
            when(blobClient.generateSas(any())).thenReturn("token=abc");

            try (MockedConstruction<BlobClientBuilder> ignored = stubbedBuilder()) {
                String name = "på_12_v_.pdf";
                service.uploadFile(name, new ByteArrayInputStream(new byte[]{1}), 1L, null, false);
                String expected = Base64.getEncoder()
                        .encodeToString(name.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                verify(blobClient).setTags(argThat(tags -> expected.equals(tags.get("originalName"))));
            }
        }

        @Test @DisplayName("Routes video uploads to video container")
        void routesVideoToVideoContainer() {
            when(videoContainerClient.getBlobClient(anyString())).thenReturn(blobClient);
            when(blobClient.generateSas(any())).thenReturn("token=abc");

            try (MockedConstruction<BlobClientBuilder> ignored = stubbedBuilder()) {
                service.uploadFile("clip.mp4", new ByteArrayInputStream(new byte[]{1}), 1L, null, false);
                verify(pdfContainerClient, never()).getBlobClient(anyString());
            }
        }
    }

    // =========================================================================
    // generateDownloadUrl
    // =========================================================================

    @Nested @DisplayName("generateDownloadUrl")
    class GenerateDownloadUrlTests {

        @Test @DisplayName("Returns absolute HTTPS URI for a PDF blob via Front Door")
        void absoluteUriForPdf() {
            when(pdfContainerClient.getBlobClient("file.pdf")).thenReturn(blobClient);
            when(blobClient.generateSas(any())).thenReturn("sv=2024&sig=abc");

            var uri = service.generateDownloadUrl("file.pdf");

            assertThat(uri.isAbsolute()).isTrue();
            assertThat(uri.toString()).startsWith(FRONT_DOOR + "/" + PDF_CONTAINER + "/file.pdf?");
        }

        @Test @DisplayName("Returns absolute URI for a video blob")
        void absoluteUriForVideo() {
            when(videoContainerClient.getBlobClient("clip.mp4")).thenReturn(blobClient);
            when(blobClient.generateSas(any())).thenReturn("token=xyz");

            assertThat(service.generateDownloadUrl("clip.mp4").toString())
                    .startsWith(FRONT_DOOR + "/" + VIDEO_CONTAINER + "/clip.mp4?");
        }

        @Test @DisplayName("Throws 400 for unsupported extension")
        void throwsForUnsupportedExtension() {
            assertThatThrownBy(() -> service.generateDownloadUrl("file.exe"))
                    .isInstanceOf(BlobOperationException.class)
                    .satisfies(e -> assertThat(((BlobOperationException) e).getStatusCode()).isEqualTo(400));
        }

        @Test @DisplayName("No double slashes when Front Door endpoint had trailing slash")
        void noDoubleSlash() {
            BlobStorageService svc = new BlobStorageService(
                    pdfContainerClient, videoContainerClient,
                    ACCOUNT_NAME, PDF_CONTAINER, VIDEO_CONTAINER,
                    SAS_EXPIRY, "https://cdn.example.com///");
            when(pdfContainerClient.getBlobClient("file.pdf")).thenReturn(blobClient);
            when(blobClient.generateSas(any())).thenReturn("token=x");

            assertThat(svc.generateDownloadUrl("file.pdf").toString()).doesNotContain("//pdfs");
        }
    }

    // =========================================================================
    // streamFile
    // =========================================================================

    @Nested @DisplayName("streamFile")
    class StreamFileTests {

        @Test @DisplayName("Calls downloadStreamWithResponse with correct range and 3 retries")
        void correctRangeAndRetries() {
            when(videoContainerClient.getBlobClient("clip.mp4")).thenReturn(blobClient);

            service.streamFile("clip.mp4", new ByteArrayOutputStream(), 0L, 512L);

            verify(blobClient).downloadStreamWithResponse(
                    any(),
                    argThat(r -> r.getOffset() == 0L && Long.valueOf(512L).equals(r.getCount())),
                    argThat(retry -> retry.getMaxRetryRequests() == 3),
                    isNull(), eq(false), isNull(), eq(Context.NONE));
        }

        @Test @DisplayName("Uses PDF container for .pdf blobs")
        void usesPdfContainerForPdf() {
            when(pdfContainerClient.getBlobClient("doc.pdf")).thenReturn(blobClient);

            service.streamFile("doc.pdf", new ByteArrayOutputStream(), 100L, 200L);

            verify(blobClient).downloadStreamWithResponse(
                    any(),
                    argThat(r -> r.getOffset() == 100L && Long.valueOf(200L).equals(r.getCount())),
                    any(), isNull(), eq(false), isNull(), eq(Context.NONE));
        }

        @Test @DisplayName("Throws 400 for unsupported extension")
        void throwsForUnsupportedExtension() {
            assertThatThrownBy(() -> service.streamFile("file.exe",
                    new ByteArrayOutputStream(), 0L, 100L))
                    .isInstanceOf(BlobOperationException.class)
                    .satisfies(e -> assertThat(((BlobOperationException) e).getStatusCode()).isEqualTo(400));
        }

        @Test @DisplayName("Translates Azure exception")
        void translatesAzureException() {
            when(videoContainerClient.getBlobClient("clip.mp4")).thenReturn(blobClient);
            doThrow(fakeAzureEx(503)).when(blobClient)
                    .downloadStreamWithResponse(any(), any(), any(), any(), anyBoolean(), any(), any());

            assertThatThrownBy(() -> service.streamFile("clip.mp4",
                    new ByteArrayOutputStream(), 0L, 100L))
                    .isInstanceOf(BlobOperationException.class)
                    .satisfies(e -> assertThat(((BlobOperationException) e).getStatusCode()).isEqualTo(503));
        }
    }

    // =========================================================================
    // getBlobSize
    // =========================================================================

    @Nested @DisplayName("getBlobSize")
    class GetBlobSizeTests {

        @Test @DisplayName("Returns size for a PDF blob")
        void returnsSizeForPdf() {
            BlobProperties props = mock(BlobProperties.class);
            when(props.getBlobSize()).thenReturn(1024L);
            when(pdfContainerClient.getBlobClient("file.pdf")).thenReturn(blobClient);
            when(blobClient.getProperties()).thenReturn(props);

            assertThat(service.getBlobSize("file.pdf")).isEqualTo(1024L);
        }

        @Test @DisplayName("Returns size for a video blob")
        void returnsSizeForVideo() {
            BlobProperties props = mock(BlobProperties.class);
            when(props.getBlobSize()).thenReturn(50_000_000L);
            when(videoContainerClient.getBlobClient("clip.mp4")).thenReturn(blobClient);
            when(blobClient.getProperties()).thenReturn(props);

            assertThat(service.getBlobSize("clip.mp4")).isEqualTo(50_000_000L);
        }

        @Test @DisplayName("Throws 400 for unsupported extension")
        void throwsForUnsupportedExtension() {
            assertThatThrownBy(() -> service.getBlobSize("file.exe"))
                    .isInstanceOf(BlobOperationException.class)
                    .satisfies(e -> assertThat(((BlobOperationException) e).getStatusCode()).isEqualTo(400));
        }
    }

    // =========================================================================
    // deleteFile
    // =========================================================================

    @Nested @DisplayName("deleteFile")
    class DeleteFileTests {

        @Test @DisplayName("Generates SAS with delete permission and calls delete()")
        void deletesSasClient() {
            when(pdfContainerClient.getBlobClient(anyString())).thenReturn(blobClient);
            when(blobClient.generateSas(any())).thenReturn("token=del");

            try (MockedConstruction<BlobClientBuilder> ignored = stubbedBuilder()) {
                service.deleteFile("file.pdf");
                verify(blobClient).generateSas(argThat(v -> v.getPermissions().contains("d")));
                verify(blobClient).delete();
            }
        }

        @Test @DisplayName("Throws 400 for unsupported extension")
        void throwsForUnsupportedExtension() {
            assertThatThrownBy(() -> service.deleteFile("archive.zip"))
                    .isInstanceOf(BlobOperationException.class)
                    .satisfies(e -> assertThat(((BlobOperationException) e).getStatusCode()).isEqualTo(400));
        }
    }

    // =========================================================================
    // getFileTags
    // =========================================================================

    @Nested @DisplayName("getFileTags")
    class GetFileTagsTests {

        @Test @DisplayName("Returns all tags for a PDF blob")
        void returnsTagsForPdf() {
            Map<String, String> expected = Map.of("sectionId", "7", "originalName", "report.pdf");
            when(pdfContainerClient.getBlobClient("file.pdf")).thenReturn(blobClient);
            when(blobClient.getTags()).thenReturn(new HashMap<>(expected));
            assertThat(service.getFileTags("file.pdf")).containsAllEntriesOf(expected);
        }

        @Test @DisplayName("Returns all tags for a video blob")
        void returnsTagsForVideo() {
            when(videoContainerClient.getBlobClient("clip.mp4")).thenReturn(blobClient);
            when(blobClient.getTags()).thenReturn(Map.of("sectionId", "3"));
            assertThat(service.getFileTags("clip.mp4")).containsEntry("sectionId", "3");
        }
    }

    // =========================================================================
    // updateSectionId
    // =========================================================================

    @Nested @DisplayName("updateSectionId")
    class UpdateSectionIdTests {

        @Test @DisplayName("Merges new sectionId without losing other tags")
        void mergesTag() {
            when(pdfContainerClient.getBlobClient("file.pdf")).thenReturn(blobClient);
            when(blobClient.getTags()).thenReturn(new HashMap<>(Map.of("originalName", "doc.pdf")));

            service.updateSectionId("file.pdf", "42");

            verify(blobClient).setTags(argThat(tags ->
                    "42".equals(tags.get("sectionId")) && "doc.pdf".equals(tags.get("originalName"))));
        }

        @Test @DisplayName("Overwrites an existing sectionId")
        void overwritesExisting() {
            when(pdfContainerClient.getBlobClient("file.pdf")).thenReturn(blobClient);
            when(blobClient.getTags()).thenReturn(new HashMap<>(Map.of("sectionId", "1")));

            service.updateSectionId("file.pdf", "99");

            verify(blobClient).setTags(argThat(tags -> "99".equals(tags.get("sectionId"))));
        }
    }

    // =========================================================================
    // addTag
    // =========================================================================

    @Nested @DisplayName("addTag")
    class AddTagTests {

        @Test @DisplayName("Adds a new tag while preserving existing tags")
        void addsTagPreservingExisting() {
            when(videoContainerClient.getBlobClient("vid.mp4")).thenReturn(blobClient);
            when(blobClient.getTags()).thenReturn(
                    new HashMap<>(Map.of("originalName", "vid.mp4", "sectionId", "3")));

            service.addTag("vid.mp4", "customKey", "customVal");

            verify(blobClient).setTags(argThat(tags ->
                    "customVal".equals(tags.get("customKey")) &&
                            "vid.mp4".equals(tags.get("originalName")) &&
                            "3".equals(tags.get("sectionId"))));
        }

        @Test @DisplayName("Overwrites a tag with same key")
        void overwritesExistingTag() {
            when(pdfContainerClient.getBlobClient("file.pdf")).thenReturn(blobClient);
            when(blobClient.getTags()).thenReturn(new HashMap<>(Map.of("myKey", "oldVal")));

            service.addTag("file.pdf", "myKey", "newVal");

            verify(blobClient).setTags(argThat(tags -> "newVal".equals(tags.get("myKey"))));
        }
    }

    // =========================================================================
    // downloadFileBytes
    // =========================================================================

    @Nested @DisplayName("downloadFileBytes")
    class DownloadFileBytesTests {

        @Test @DisplayName("Returns correct bytes for a PDF blob")
        void returnsBytesForPdf() {
            when(pdfContainerClient.getBlobClient("file.pdf")).thenReturn(blobClient);
            doAnswer(inv -> {
                ((ByteArrayOutputStream) inv.getArgument(0)).write(new byte[]{1, 2, 3});
                return null;
            }).when(blobClient).downloadStream(any(ByteArrayOutputStream.class));

            assertThat(service.downloadFileBytes("file.pdf")).containsExactly(1, 2, 3);
        }

        @Test @DisplayName("Returns correct bytes for a video blob")
        void returnsBytesForVideo() {
            when(videoContainerClient.getBlobClient("clip.mp4")).thenReturn(blobClient);
            doAnswer(inv -> {
                ((ByteArrayOutputStream) inv.getArgument(0)).write(new byte[]{10, 20});
                return null;
            }).when(blobClient).downloadStream(any(ByteArrayOutputStream.class));

            assertThat(service.downloadFileBytes("clip.mp4")).containsExactly(10, 20);
        }

        @Test @DisplayName("Returns empty byte array when blob is empty")
        void returnsEmptyForEmptyBlob() {
            when(pdfContainerClient.getBlobClient("empty.pdf")).thenReturn(blobClient);
            doNothing().when(blobClient).downloadStream(any(ByteArrayOutputStream.class));
            assertThat(service.downloadFileBytes("empty.pdf")).isEmpty();
        }

        @Test @DisplayName("Throws 400 for unsupported extension")
        void throwsForUnsupportedExtension() {
            assertThatThrownBy(() -> service.downloadFileBytes("file.exe"))
                    .isInstanceOf(BlobOperationException.class)
                    .satisfies(e -> assertThat(((BlobOperationException) e).getStatusCode()).isEqualTo(400));
        }

        @Test @DisplayName("Translates Azure exception")
        void translatesAzureException() {
            when(pdfContainerClient.getBlobClient("file.pdf")).thenReturn(blobClient);
            doThrow(fakeAzureEx(404))
                    .when(blobClient).downloadStream(any(ByteArrayOutputStream.class));

            assertThatThrownBy(() -> service.downloadFileBytes("file.pdf"))
                    .isInstanceOf(BlobOperationException.class)
                    .satisfies(e -> assertThat(((BlobOperationException) e).getStatusCode()).isEqualTo(404));
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    /**
     * BlobStorageException is final — mock-maker-inline lets us mock it.
     * Only getStatusCode(), getMessage(), and getErrorCode() are called by translateException().
     */
    private BlobStorageException fakeAzureEx(int statusCode) {
        BlobStorageException ex = mock(BlobStorageException.class);
        when(ex.getStatusCode()).thenReturn(statusCode);
        when(ex.getMessage()).thenReturn("Azure error HTTP " + statusCode);
        when(ex.getErrorCode()).thenReturn(null);
        return ex;
    }

    /**
     * PagedIterable is final — mock-maker-inline lets us mock it.
     */
    @SuppressWarnings("unchecked")
    private PagedIterable<BlobItem> pagedBlobs(BlobItem... items) {
        PagedIterable<BlobItem> paged = mock(PagedIterable.class);
        when(paged.stream()).thenReturn(Arrays.stream(items));
        when(paged.iterator()).thenReturn(Arrays.asList(items).iterator());
        return paged;
    }

    @SuppressWarnings("unchecked")
    private PagedIterable<TaggedBlobItem> pagedTagged(TaggedBlobItem... items) {
        PagedIterable<TaggedBlobItem> paged = mock(PagedIterable.class);
        when(paged.stream()).thenReturn(Arrays.stream(items));
        when(paged.iterator()).thenReturn(Arrays.asList(items).iterator());
        return paged;
    }

    /**
     * Intercepts new BlobClientBuilder() calls made inside uploadFile/deleteFile
     * so they return our blobClient mock instead of making real HTTP calls.
     */
    private MockedConstruction<BlobClientBuilder> stubbedBuilder() {
        return mockConstruction(BlobClientBuilder.class, (m, ctx) -> {
            when(m.endpoint(anyString())).thenReturn(m);
            when(m.buildClient()).thenReturn(blobClient);
        });
    }
}