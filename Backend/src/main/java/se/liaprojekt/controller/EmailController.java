package se.liaprojekt.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import se.liaprojekt.model.EmailEvent;
import se.liaprojekt.producer.EmailEventPublisher;

/**
 * API skickar email-jobb till Azure Service Bus (async).
 */
@RestController
@RequestMapping("/api/email")
@RequiredArgsConstructor
public class EmailController {

    private final EmailEventPublisher publisher;

    @PostMapping("/welcome")
    public String sendWelcome(
            @RequestParam String email,
            @RequestParam String name) {

        EmailEvent event = EmailEvent.builder()
                .to(email)
                .subject("Välkommen " + name)
                .body("<h1>Hej " + name + "</h1><p>Välkommen!</p>")
                .type("WELCOME_EMAIL")
                .build();

        publisher.publish(event);

        return "Email queued for " + email;
    }
}