package se.liaprojekt.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ChatHistoryMessage {

    private String role;
    private String content;
}