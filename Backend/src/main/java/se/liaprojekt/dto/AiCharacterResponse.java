package se.liaprojekt.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class AiCharacterResponse {

    private Long id;
    private String name;
    private String description;
}