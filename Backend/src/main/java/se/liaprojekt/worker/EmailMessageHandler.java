package se.liaprojekt.worker;

import com.azure.messaging.servicebus.ServiceBusErrorContext;
import com.azure.messaging.servicebus.ServiceBusReceivedMessage;
import com.azure.messaging.servicebus.ServiceBusReceivedMessageContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import se.liaprojekt.exception.EmailProcessingException;
import se.liaprojekt.event.EmailEvent;
import se.liaprojekt.service.GraphService;

@Component
public class EmailMessageHandler {

    private static final Logger log =
            LoggerFactory.getLogger(EmailMessageHandler.class);

    private final GraphService graphService;
    private final ObjectMapper objectMapper;

    public EmailMessageHandler(GraphService graphService,
                               ObjectMapper objectMapper) {

        this.graphService = graphService;
        this.objectMapper = objectMapper;
    }

    /**
     * Hanterar inkommande messages
     */
    public void handleMessage(ServiceBusReceivedMessageContext context) {

        ServiceBusReceivedMessage message = context.getMessage();

        log.info("📩 Received message from Service Bus");
        log.info("MessageId: {}", message.getMessageId());
        log.info("Body: {}", message.getBody());

        try {

            String body = message.getBody().toString();

            EmailEvent event =
                    objectMapper.readValue(body, EmailEvent.class);

            graphService.sendEmail(
                    event.to(),
                    event.subject(),
                    event.body()
            );

            log.info("✅ Email sent to {}", event.to());

        } catch (Exception e) {

            log.error("❌ Failed processing Service Bus message", e);

            throw new EmailProcessingException(
                    "Failed processing email message",
                    e
            );
        }
    }

    /**
     * Hanterar fel (important i production)
     */
    public void handleError(ServiceBusErrorContext context) {

        Throwable error = context.getException();

        log.error("❌ Service Bus error");

        log.error("Namespace: {}", context.getFullyQualifiedNamespace());
        log.error("Entity: {}", context.getEntityPath());

        if (error != null) {
            log.error("Error type: {}", error.getClass().getSimpleName());
            log.error("Message: {}", error.getMessage(), error);
        }
    }
}