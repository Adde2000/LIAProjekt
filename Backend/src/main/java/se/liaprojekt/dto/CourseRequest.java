package se.liaprojekt.dto;

public record CourseRequest(
        String title,
        String description,
        Integer aiSessionTtlWeeks,
        Long courseAdminId
){
}
