package se.liaprojekt.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class AssistantAdminResponse {

    private String id;
    private String name;
    private String instructions;
    private String model;
}
