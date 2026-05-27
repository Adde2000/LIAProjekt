package se.liaprojekt.dto;

public record UserProgressResponse(
        UserResponse userResponse,
        int completedSections,
        int progressPercentage
) {}
