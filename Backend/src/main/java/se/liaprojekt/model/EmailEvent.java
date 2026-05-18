package se.liaprojekt.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Detta är payloaden som skickas till Azure Service Bus.
 * Azure Function tar sedan hand om att skicka email via Graph.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailEvent {

    private String to;
    private String subject;
    private String body;

    // används för routing/logik i Azure Function
    private String type;
}