package se.liaprojekt.producer;

import com.azure.messaging.servicebus.ServiceBusMessage;
import com.azure.messaging.servicebus.ServiceBusSenderClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import se.liaprojekt.event.EmailEvent;
import se.liaprojekt.exception.EventPublishException;
import se.liaprojekt.model.EmailType;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailEventPublisherTest {

    @Mock
    private ServiceBusSenderClient senderClient;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private EmailEventPublisher emailEventPublisher;

    private EmailEvent emailEvent;

    @BeforeEach
    void setUp() {

        emailEvent = new EmailEvent(
                "test@test.com",
                "Welcome",
                "Hello user",
                EmailType.WELCOME_EMAIL
        );
    }

    @Test
    void shouldPublishEmailEvent() throws Exception {

        // Arrange
        String json = """
                {
                    "to":"test@test.com",
                    "subject":"Welcome"
                }
                """;

        when(objectMapper.writeValueAsString(emailEvent))
                .thenReturn(json);

        // Act
        emailEventPublisher.publish(emailEvent);

        // Assert
        ArgumentCaptor<ServiceBusMessage> captor =
                ArgumentCaptor.forClass(ServiceBusMessage.class);

        verify(senderClient).sendMessage(captor.capture());

        ServiceBusMessage message = captor.getValue();

        assertNotNull(message);

        assertEquals(
                json,
                new String(message.getBody().toBytes())
        );

        assertEquals(
                "WELCOME_EMAIL",
                message.getSubject()
        );

        assertEquals(
                "application/json",
                message.getContentType()
        );

        assertNotNull(message.getMessageId());
    }

    @Test
    void shouldThrowEventPublishExceptionWhenSerializationFails() throws Exception {

        // Arrange
        when(objectMapper.writeValueAsString(emailEvent))
                .thenThrow(new RuntimeException("Serialization failed"));

        // Act + Assert
        EventPublishException exception = assertThrows(
                EventPublishException.class,
                () -> emailEventPublisher.publish(emailEvent)
        );

        assertEquals(
                "Failed to publish email event",
                exception.getMessage()
        );

        verify(senderClient, never()).sendMessage(any());
    }

    @Test
    void shouldThrowEventPublishExceptionWhenSendFails() throws Exception {

        // Arrange
        when(objectMapper.writeValueAsString(emailEvent))
                .thenReturn("{\"test\":\"json\"}");

        doThrow(new RuntimeException("Service Bus down"))
                .when(senderClient)
                .sendMessage(any(ServiceBusMessage.class));

        // Act + Assert
        EventPublishException exception = assertThrows(
                EventPublishException.class,
                () -> emailEventPublisher.publish(emailEvent)
        );

        assertEquals(
                "Failed to publish email event",
                exception.getMessage()
        );

        verify(senderClient).sendMessage(any(ServiceBusMessage.class));
    }
}