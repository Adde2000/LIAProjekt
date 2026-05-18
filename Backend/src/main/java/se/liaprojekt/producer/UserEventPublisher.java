package se.liaprojekt.producer;

import com.azure.messaging.servicebus.ServiceBusMessage;
import com.azure.messaging.servicebus.ServiceBusSenderClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import se.liaprojekt.event.UserCreatedEvent;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserEventPublisher {

    private final ServiceBusSenderClient senderClient;
    private final ObjectMapper objectMapper;

    public void publish(UserCreatedEvent event) {

        try {

            // =========================
            // SERIALIZE EVENT
            // =========================
            String json = objectMapper.writeValueAsString(event);

            // =========================
            // CREATE SERVICE BUS MESSAGE
            // =========================
            ServiceBusMessage message = new ServiceBusMessage(json);

            message.setSubject("USER_CREATED");
            message.setContentType("application/json");
            message.setMessageId(UUID.randomUUID().toString());

            // =========================
            // LOG BEFORE SEND
            // =========================
            log.info("PUBLISH_USER_CREATED_EVENT | entraId={}",
                    event.entraId()
            );

            // =========================
            // SEND TO SERVICE BUS
            // =========================
            senderClient.sendMessage(message);

        } catch (Exception e) {

            log.error("FAILED_PUBLISH_USER_CREATED_EVENT | entraId={}",
                    event.entraId(),
                    e
            );

            throw new RuntimeException(
                    "Failed to publish USER_CREATED event",
                    e
            );
        }
    }
}