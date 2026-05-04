package se.liaprojekt.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class CourseResponse {
    private Long id;
    private String title;
    private String description;
    private String createdBy;
}
