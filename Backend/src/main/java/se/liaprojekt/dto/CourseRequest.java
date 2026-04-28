package se.liaprojekt.dto;

import java.time.LocalDateTime;

import static org.springframework.data.jpa.domain.AbstractAuditable_.createdBy;

public record CourseRequest(
        Long id,
        String title,
        String description){
}
