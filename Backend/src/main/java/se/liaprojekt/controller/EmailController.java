package se.liaprojekt.controller;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import se.liaprojekt.controller.util.Roles;
import se.liaprojekt.event.EmailEvent;
import se.liaprojekt.producer.EmailEventPublisher;

import static se.liaprojekt.model.EmailType.WELCOME_EMAIL;

/**
 * API skickar email-jobb till Azure Service Bus (async).
 */
@RestController
@RequestMapping("/api/email")
@RequiredArgsConstructor
public class EmailController {
    private static final Logger log = LoggerFactory.getLogger(EmailController.class);

    private final EmailEventPublisher publisher;

    //Admin
    @PostMapping("/welcome")
    @PreAuthorize(Roles.ROLE_ADMIN)
    public String sendWelcome(
            @RequestParam String email,
            @RequestParam String name) {
        log.info("Sending welcome email");
        EmailEvent event = new EmailEvent(
                email,
                "Välkommen " + name,
                "<h1>Hej " + name + "</h1><p>Välkommen!</p>",
                WELCOME_EMAIL
        );

        publisher.publish(event);
        log.info("Welcome email queued");

        return "Email queued for " + email;
    }
}