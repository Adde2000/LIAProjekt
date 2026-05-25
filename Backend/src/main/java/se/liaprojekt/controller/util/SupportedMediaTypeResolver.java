package se.liaprojekt.controller.util;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * Utility component that maps file extensions to their corresponding {@link MediaType},
 * and provides helpers for validating and categorising supported file types.
 *
 * <p>Acts as the single source of truth for which file types the application accepts.
 * Adding support for a new format requires only adding an entry to
 * {@code EXTENSION_TYPE_MAP} and updating {@link #extensionsForType} if it belongs
 * to a named category.
 */
@Component
public class SupportedMediaTypeResolver {

    /**
     * Map of supported file extensions to their MIME type.
     * All keys are lowercase without a leading dot.
     */
    private static final Map<String, MediaType> EXTENSION_TYPE_MAP = Map.of(
            "pdf", MediaType.APPLICATION_PDF,
            "mp4", MediaType.parseMediaType("video/mp4"),
            "mov", MediaType.parseMediaType("video/quicktime"),
            "avi", MediaType.parseMediaType("video/x-msvideo"),
            "mkv", MediaType.parseMediaType("video/x-matroska")
    );

    /** Full set of supported extensions derived from the map keys. */
    private static final Set<String> SUPPORTED_EXTENSIONS = EXTENSION_TYPE_MAP.keySet();

    /** Extensions considered to be video formats. */
    private static final Set<String> VIDEO_EXTENSIONS = Set.of("mp4", "mov", "avi", "mkv");

    /**
     * Returns {@code true} if the given filename has a supported extension.
     *
     * @param fileName Filename including extension
     * @return {@code true} if the file type is accepted, {@code false} otherwise
     */
    public boolean isSupported(String fileName) {
        return SUPPORTED_EXTENSIONS.contains(getExtension(fileName));
    }

    /**
     * Returns {@code true} if the given filename is a supported video format.
     * Used by the stream endpoint to reject non-video requests.
     *
     * @param fileName Filename including extension
     * @return {@code true} if the file is a video
     */
    public boolean isVideo(String fileName) {
        return VIDEO_EXTENSIONS.contains(getExtension(fileName));
    }

    /**
     * Resolves the {@link MediaType} for the given filename based on its extension.
     * Falls back to {@code application/octet-stream} for unknown extensions.
     *
     * @param fileName Filename including extension
     * @return Resolved media type
     */
    public MediaType resolve(String fileName) {
        return EXTENSION_TYPE_MAP.getOrDefault(
                getExtension(fileName),
                MediaType.APPLICATION_OCTET_STREAM
        );
    }

    /**
     * Returns the set of file extensions corresponding to a named type category.
     *
     * <p>Used by the {@code /list?type=} query parameter to determine which
     * extensions to include when filtering blobs.
     *
     * @param type Category name: {@code "pdf"}, {@code "video"}, or {@code "all"} (default)
     * @return Set of lowercase extensions for the requested category
     */
    public Set<String> extensionsForType(String type) {
        return switch (type.toLowerCase()) {
            case "pdf"   -> Set.of("pdf");
            case "video" -> VIDEO_EXTENSIONS;
            default      -> SUPPORTED_EXTENSIONS;
        };
    }

    /**
     * Extracts the lowercase file extension from a filename.
     *
     * @param fileName Filename including extension
     * @return Lowercase extension without leading dot, or empty string if none present
     */
    private String getExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot >= 0 ? fileName.substring(dot + 1).toLowerCase() : "";
    }
}