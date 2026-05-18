package se.liaprojekt.producer;

import com.azure.messaging.servicebus.ServiceBusMessage;
import com.azure.messaging.servicebus.ServiceBusSenderClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import se.liaprojekt.exception.EventPublishException;
import se.liaprojekt.model.EmailEvent;

/**
 * Skickar email-events till Azure Service Bus.
 */
@Service
@RequiredArgsConstructor
public class EmailEventPublisher {

    private final ServiceBusSenderClient senderClient;
    private final ObjectMapper objectMapper;

    public void publish(EmailEvent event) {

        try {
            String json = objectMapper.writeValueAsString(event);

            ServiceBusMessage message = new ServiceBusMessage(json);

            senderClient.sendMessage(message);

        } catch (Exception e) {
            throw new EventPublishException(
                    "Failed to publish email event",
                    e
            );
        }
    }
}