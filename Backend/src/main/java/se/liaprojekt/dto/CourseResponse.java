package se.liaprojekt.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import se.liaprojekt.model.Course;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class CourseResponse {
    private Long id;
    private String title;
    private String description;
    private String createdBy;
    private LocalDateTime createdAt;
}
