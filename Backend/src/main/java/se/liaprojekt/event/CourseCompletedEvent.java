package se.liaprojekt.event;

public record CourseCompletedEvent(
        String entraId,
        Long courseId
) {}
