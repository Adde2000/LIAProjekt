package se.liaprojekt.service;

import com.azure.core.credential.TokenCredential;
import com.microsoft.graph.models.*;
import com.microsoft.graph.models.User;
import com.microsoft.graph.models.UserCollectionResponse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.test.util.ReflectionTestUtils;

import se.liaprojekt.dto.GraphResponse;
import se.liaprojekt.exception.ResourceNotFoundException;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GraphServiceTest {

    @Mock
    private TokenService tokenService;

    @Spy
    @InjectMocks
    private GraphService graphService;

    @BeforeEach
    void setUp() {

        ReflectionTestUtils.setField(
                graphService,
                "mailUser",
                "admin@test.se"
        );
    }

    @Test
    void shouldTranslateRoles() throws Exception {

        // Arrange
        Set<String> roles = Set.of(
                "sg-app-admin",
                "sg-app-courseadmin",
                "sg-app-participant",
                "other-role"
        );

        Method method = GraphService.class
                .getDeclaredMethod(
                        "translateRoles",
                        Set.class
                );

        method.setAccessible(true);

        // Act
        Set<String> result =
                (Set<String>) method.invoke(
                        graphService,
                        roles
                );

        // Assert
        assertEquals(3, result.size());

        assertTrue(result.contains("admin"));
        assertTrue(result.contains("courseadmin"));
        assertTrue(result.contains("participant"));
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

    @Test
    void shouldContainExpectedRoleConstants() throws Exception {

        // Arrange
        Method method = GraphService.class
                .getDeclaredMethod(
                        "translateRoles",
                        Set.class
                );

        method.setAccessible(true);

        // Act
        Set<String> result =
                (Set<String>) method.invoke(
                        graphService,
                        Set.of("sg-app-admin")
                );

        // Assert
        assertTrue(result.contains("admin"));
    }
}