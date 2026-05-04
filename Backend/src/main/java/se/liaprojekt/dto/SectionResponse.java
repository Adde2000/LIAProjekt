package se.liaprojekt.dto;

public record SectionResponse(
        Long id,
        String title,
        int orderIndex,
        Long courseId
) {

}
