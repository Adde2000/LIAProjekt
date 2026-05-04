package se.liaprojekt.dto;

public record MaterialResponse(
        Long id,
        String title,
        String contentType,
        String contentUrl,
        Long sectionId
) {

}
