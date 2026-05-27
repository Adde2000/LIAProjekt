package se.liaprojekt.controller.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SupportedMediaTypeResolverTest {

    private SupportedMediaTypeResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new SupportedMediaTypeResolver();
    }

    @Test
    void shouldReturnTrueForSupportedPdf() {

        boolean result = resolver.isSupported("document.pdf");

        assertTrue(result);
    }

    @Test
    void shouldReturnTrueForSupportedVideo() {

        assertTrue(resolver.isSupported("movie.mp4"));
        assertTrue(resolver.isSupported("clip.mov"));
        assertTrue(resolver.isSupported("video.avi"));
        assertTrue(resolver.isSupported("series.mkv"));
    }

    @Test
    void shouldReturnFalseForUnsupportedFile() {

        boolean result = resolver.isSupported("notes.txt");

        assertFalse(result);
    }

    @Test
    void shouldReturnFalseWhenNoExtensionExists() {

        boolean result = resolver.isSupported("README");

        assertFalse(result);
    }

    @Test
    void shouldDetectVideoFiles() {

        assertTrue(resolver.isVideo("movie.mp4"));
        assertTrue(resolver.isVideo("clip.mov"));
        assertTrue(resolver.isVideo("video.avi"));
        assertTrue(resolver.isVideo("series.mkv"));
    }

    @Test
    void shouldReturnFalseForNonVideoFiles() {

        assertFalse(resolver.isVideo("document.pdf"));
        assertFalse(resolver.isVideo("notes.txt"));
    }

    @Test
    void shouldResolvePdfMediaType() {

        MediaType mediaType =
                resolver.resolve("document.pdf");

        assertEquals(
                MediaType.APPLICATION_PDF,
                mediaType
        );
    }

    @Test
    void shouldResolveMp4MediaType() {

        MediaType mediaType =
                resolver.resolve("movie.mp4");

        assertEquals(
                MediaType.parseMediaType("video/mp4"),
                mediaType
        );
    }

    @Test
    void shouldResolveMovMediaType() {

        MediaType mediaType =
                resolver.resolve("clip.mov");

        assertEquals(
                MediaType.parseMediaType("video/quicktime"),
                mediaType
        );
    }

    @Test
    void shouldResolveAviMediaType() {

        MediaType mediaType =
                resolver.resolve("video.avi");

        assertEquals(
                MediaType.parseMediaType("video/x-msvideo"),
                mediaType
        );
    }

    @Test
    void shouldResolveMkvMediaType() {

        MediaType mediaType =
                resolver.resolve("series.mkv");

        assertEquals(
                MediaType.parseMediaType("video/x-matroska"),
                mediaType
        );
    }

    @Test
    void shouldFallbackToOctetStreamForUnknownExtension() {

        MediaType mediaType =
                resolver.resolve("archive.zip");

        assertEquals(
                MediaType.APPLICATION_OCTET_STREAM,
                mediaType
        );
    }

    @Test
    void shouldReturnPdfExtensions() {

        Set<String> result =
                resolver.extensionsForType("pdf");

        assertEquals(Set.of("pdf"), result);
    }

    @Test
    void shouldReturnVideoExtensions() {

        Set<String> result =
                resolver.extensionsForType("video");

        assertEquals(
                Set.of("mp4", "mov", "avi", "mkv"),
                result
        );
    }

    @Test
    void shouldReturnAllSupportedExtensions() {

        Set<String> result =
                resolver.extensionsForType("all");

        assertTrue(result.contains("pdf"));
        assertTrue(result.contains("mp4"));
        assertTrue(result.contains("mov"));
        assertTrue(result.contains("avi"));
        assertTrue(result.contains("mkv"));
    }

    @Test
    void shouldReturnAllExtensionsForUnknownType() {

        Set<String> result =
                resolver.extensionsForType("unknown");

        assertTrue(result.contains("pdf"));
        assertTrue(result.contains("mp4"));
        assertTrue(result.contains("mov"));
        assertTrue(result.contains("avi"));
        assertTrue(result.contains("mkv"));
    }

    @Test
    void shouldHandleUppercaseExtensions() {

        assertTrue(resolver.isSupported("VIDEO.MP4"));
        assertTrue(resolver.isVideo("MOVIE.MKV"));

        MediaType mediaType =
                resolver.resolve("FILE.PDF");

        assertEquals(
                MediaType.APPLICATION_PDF,
                mediaType
        );
    }
}