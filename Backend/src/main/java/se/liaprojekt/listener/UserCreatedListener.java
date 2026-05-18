package se.liaprojekt.listener;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.event.TransactionPhase;
import se.liaprojekt.event.UserCreatedEvent;
import se.liaprojekt.producer.UserEventPublisher;

@Component
@RequiredArgsConstructor
public class UserCreatedListener {

    private final UserEventPublisher userEventPublisher;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUserCreated(UserCreatedEvent event) {

        userEventPublisher.publish(event);
    }
}