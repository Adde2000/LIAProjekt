package se.liaprojekt.event;

import se.liaprojekt.model.EmailType;

/**
 * Payload som skickas till Azure Service Bus.
 * Azure Function skickar sedan email via Graph.
 */
public record EmailEvent(

        String to,
        String subject,
        String body,

        // används för routing/logik i Azure Function
        EmailType type

) {}