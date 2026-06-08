package se.liaprojekt.listener;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.event.TransactionPhase;
import se.liaprojekt.event.UserCreatedEvent;
import lombok.extern.slf4j.Slf4j;
import se.liaprojekt.service.EmailService;
import se.liaprojekt.service.GraphService;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserCreatedListener {

    private final EmailService emailService;
    private final GraphService graphService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUserCreated(UserCreatedEvent event) {

        String email = graphService
                .getUserByEntraId(event.entraId())
                .mail();

        emailService.sendWelcomeEmail(email);
    }
}