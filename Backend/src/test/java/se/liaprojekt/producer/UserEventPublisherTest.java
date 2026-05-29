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
import org.springframework.test.util.ReflectionTestUtils;
import se.liaprojekt.event.UserCreatedEvent;
import se.liaprojekt.exception.EventPublishException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserEventPublisherTest {

    @Mock
    private ServiceBusSenderClient senderClient;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private UserEventPublisher publisher;

    private UserCreatedEvent event;

    @BeforeEach
    void setUp() {

        event = new UserCreatedEvent("entra-123");

        ReflectionTestUtils.setField(
                publisher,
                "eventsEnabled",
                true
        );
    }

    @Test
    void shouldPublishUserCreatedEvent() throws Exception {

        // Arrange
        when(objectMapper.writeValueAsString(event))
                .thenReturn("{\"entraId\":\"entra-123\"}");

        // Act
        publisher.publish(event);

        // Assert
        ArgumentCaptor<ServiceBusMessage> captor =
                ArgumentCaptor.forClass(ServiceBusMessage.class);

        verify(senderClient).sendMessage(captor.capture());

        ServiceBusMessage message = captor.getValue();

        assertEquals(
                "USER_CREATED",
                message.getSubject()
        );

        assertEquals(
                "application/json",
                message.getContentType()
        );

        assertNotNull(message.getMessageId());

        String body =
                message.getBody().toString();

        assertTrue(body.contains("entra-123"));
    }

    @Test
    void shouldNotPublishWhenEventsDisabled() {

        // Arrange
        ReflectionTestUtils.setField(
                publisher,
                "eventsEnabled",
                false
        );

        // Act
        publisher.publish(event);

        // Assert
        verifyNoInteractions(senderClient);

        verifyNoInteractions(objectMapper);
    }

    @Test
    void shouldThrowEventPublishExceptionWhenSerializationFails()
            throws Exception {

        // Arrange
        when(objectMapper.writeValueAsString(event))
                .thenThrow(new RuntimeException("Serialization failed"));

        // Act + Assert
        EventPublishException ex =
                assertThrows(
                        EventPublishException.class,
                        () -> publisher.publish(event)
                );

        assertEquals(
                "Failed to publish USER_CREATED event",
                ex.getMessage()
        );

        verify(senderClient, never())
                .sendMessage(any(ServiceBusMessage.class));
    }

    @Test
    void shouldThrowEventPublishExceptionWhenSendFails()
            throws Exception {

        // Arrange
        when(objectMapper.writeValueAsString(event))
                .thenReturn("{\"entraId\":\"entra-123\"}");

        doThrow(new RuntimeException("Service Bus unavailable"))
                .when(senderClient)
                .sendMessage(any(ServiceBusMessage.class));

        // Act + Assert
        EventPublishException ex =
                assertThrows(
                        EventPublishException.class,
                        () -> publisher.publish(event)
                );

        assertEquals(
                "Failed to publish USER_CREATED event",
                ex.getMessage()
        );
    }
}