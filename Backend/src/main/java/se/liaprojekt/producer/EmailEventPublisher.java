package se.liaprojekt.producer;

import com.azure.messaging.servicebus.ServiceBusMessage;
import com.azure.messaging.servicebus.ServiceBusSenderClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import se.liaprojekt.exception.EventPublishException;
import se.liaprojekt.event.EmailEvent;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailEventPublisher {

    private final ServiceBusSenderClient senderClient;
    private final ObjectMapper objectMapper;

    public void publish(EmailEvent event) {

        try {
            String json = objectMapper.writeValueAsString(event);

            ServiceBusMessage message = new ServiceBusMessage(json);

            message.setSubject(event.type().name());
            message.setContentType("application/json");
            message.setMessageId(UUID.randomUUID().toString());

            log.info("PUBLISH_EMAIL_EVENT | type={} to={}",
                    event.type(),
                    event.to());

            senderClient.sendMessage(message);

        } catch (Exception e) {
            log.error("FAILED_PUBLISH_EMAIL_EVENT | type={} to={}",
                    event.type(),
                    event.to(), e);

            throw new EventPublishException(
                    "Failed to publish email event",
                    e
            );
        }
    }
}