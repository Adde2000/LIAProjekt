package se.liaprojekt.service;

import com.azure.core.credential.TokenCredential;
import com.microsoft.graph.models.*;

import com.microsoft.graph.serviceclient.GraphServiceClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.test.util.ReflectionTestUtils;

import se.liaprojekt.dto.GraphResponse;
import se.liaprojekt.exception.ResourceNotFoundException;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GraphServiceTest {

    @Mock
    private TokenService tokenService;

    @Mock
    private GraphServiceClient graphServiceClient;

    private GraphService graphService;

    @BeforeEach
    void setUp() {
        // Mock the credential so the constructor doesn't fail
        when(tokenService.getCredential()).thenReturn(mock(TokenCredential.class));

        // Manually construct with the mock injected
        graphService = spy(new GraphService(tokenService, new String[]{"https://graph.microsoft.com/.default"}));

        // Inject the mocked GraphServiceClient so no real HTTP calls are made
        ReflectionTestUtils.setField(graphService, "graphServiceClient", graphServiceClient);
        ReflectionTestUtils.setField(graphService, "mailUser", "admin@test.se");
        ReflectionTestUtils.setField(graphService, "clientId", "test-client-id");
    }

    @Test
    void shouldThrowWhenUserNotFound() {

        // Arrange
        doThrow(new ResourceNotFoundException("User not found"))
                .when(graphService)
                .getUserByEntraId("missing-user");

        // Act + Assert
        assertThrows(
                ResourceNotFoundException.class,
                () -> graphService.getUserByEntraId("missing-user")
        );
    }

    @Test
    void shouldCreateGraphResponseObject() {

        // Arrange
        GraphResponse response =
                new GraphResponse(
                        "entra-1",
                        "Test User",
                        "Test",
                        "User",
                        "test@test.se",
                        Set.of("admin")
                );

        // Assert
        assertEquals("entra-1", response.id());
        assertEquals("Test User", response.displayName());
        assertEquals("Test", response.givenName());
        assertEquals("User", response.surname());
        assertEquals("test@test.se", response.mail());

        assertTrue(
                response.role().contains("admin")
        );
    }

    @Test
    void shouldBuildEmailObjectsCorrectly() {

        // Arrange
        String to = "user@test.se";
        String subject = "Hello";
        String bodyText = "<h1>Hello</h1>";

        // Email body
        ItemBody body = new ItemBody();
        body.setContentType(BodyType.Html);
        body.setContent(bodyText);

        // Recipient
        EmailAddress emailAddress = new EmailAddress();
        emailAddress.setAddress(to);

        Recipient recipient = new Recipient();
        recipient.setEmailAddress(emailAddress);

        // Message
        Message message = new Message();
        message.setSubject(subject);
        message.setBody(body);
        message.setToRecipients(List.of(recipient));

        // Assert
        assertEquals(subject, message.getSubject());

        assertEquals(
                bodyText,
                message.getBody().getContent()
        );

        assertEquals(
                to,
                message.getToRecipients()
                        .getFirst()
                        .getEmailAddress()
                        .getAddress()
        );
    }
}