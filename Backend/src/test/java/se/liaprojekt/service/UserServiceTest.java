package se.liaprojekt.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.context.ApplicationEventPublisher;

import se.liaprojekt.dto.GraphResponse;
import se.liaprojekt.dto.UserResponse;
import se.liaprojekt.event.UserCreatedEvent;
import se.liaprojekt.exception.ResourceNotFoundException;
import se.liaprojekt.model.User;
import se.liaprojekt.repository.AiSessionRepository;
import se.liaprojekt.repository.UserRepository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private GraphService graphService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AiSessionRepository aiSessionRepository;

    @InjectMocks
    private UserService userService;

    private User user;
    private GraphResponse graphResponse;

    @BeforeEach
    void setUp() {

        user = User.builder()
                .id(1L)
                .entraId("entra-123")
                .build();

        graphResponse = new GraphResponse(
                "entra-123",
                "test",
                "test",
                "testsson",
                "test@test.se",
                Set.of("STUDENT")
        );
    }

    @Test
    void shouldGetUserResponseById() {

        // Arrange
        when(userRepository.findById(1L))
                .thenReturn(Optional.of(user));

        when(graphService.getUserByEntraId("entra-123"))
                .thenReturn(graphResponse);

        // Act
        UserResponse response =
                userService.getUserResponseById(1L);

        // Assert
        assertEquals(1L, response.id());
        assertEquals("test", response.displayName());
        assertEquals("test@test.se", response.mail());
    }

    @Test
    void shouldThrowWhenUserNotFoundById() {

        when(userRepository.findById(99L))
                .thenReturn(Optional.empty());

        assertThrows(
                ResourceNotFoundException.class,
                () -> userService.getUserResponseById(99L)
        );
    }

    @Test
    void shouldGetAllUserResponses() {

        // Arrange
        when(graphService.getAllUsers())
                .thenReturn(List.of(graphResponse));

        when(userRepository.findAll())
                .thenReturn(List.of(user));

        when(userRepository.findByEntraId("entra-123"))
                .thenReturn(Optional.of(user));

        when(userRepository.saveAll(anyList()))
                .thenReturn(List.of());

        // Act
        List<UserResponse> result =
                userService.getAllUserResponses();

        // Assert
        assertEquals(1, result.size());

        UserResponse response = result.get(0);

        assertEquals("test", response.displayName());
        assertEquals("testsson", response.surname());
    }

    @Test
    void shouldCreateNewUsersAndPublishEvents() {

        // Arrange
        when(graphService.getAllUsers())
                .thenReturn(List.of(graphResponse));

        when(userRepository.findAll())
                .thenReturn(List.of());

        User savedUser = User.builder()
                .id(1L)
                .entraId("entra-123")
                .build();

        when(userRepository.saveAll(anyList()))
                .thenReturn(List.of(savedUser));

        when(userRepository.findByEntraId("entra-123"))
                .thenReturn(Optional.of(savedUser));

        // Act
        userService.getAllUserResponses();

        // Assert
        verify(userRepository).saveAll(anyList());

        ArgumentCaptor<UserCreatedEvent> captor =
                ArgumentCaptor.forClass(UserCreatedEvent.class);

        verify(eventPublisher)
                .publishEvent(captor.capture());

        assertEquals(
                "entra-123",
                captor.getValue().entraId()
        );
    }

    @Test
    void shouldDeleteUsersNotInGraph() {

        // Arrange
        User oldUser = User.builder()
                .id(2L)
                .entraId("old-user")
                .build();

        when(graphService.getAllUsers())
                .thenReturn(List.of(graphResponse));

        when(userRepository.findAll())
                .thenReturn(List.of(oldUser));

        when(userRepository.saveAll(anyList()))
                .thenReturn(List.of());

        when(userRepository.findByEntraId("entra-123"))
                .thenReturn(Optional.of(user));

        doNothing().when(aiSessionRepository).deleteByUserId(oldUser.getId());

        // Act
        userService.getAllUserResponses();

        // Assert
        verify(userRepository)
                .deleteAll(anyCollection());
    }

    @Test
    void shouldGetUserById() {

        when(userRepository.findById(1L))
                .thenReturn(Optional.of(user));

        User result = userService.getUserById(1L);

        assertEquals(1L, result.getId());
    }

    @Test
    void shouldThrowWhenGetUserByIdFails() {

        when(userRepository.findById(1L))
                .thenReturn(Optional.empty());

        assertThrows(
                ResourceNotFoundException.class,
                () -> userService.getUserById(1L)
        );
    }

    @Test
    void shouldGetUserByEntraId() {

        when(userRepository.findByEntraId("entra-123"))
                .thenReturn(Optional.of(user));

        User result =
                userService.getUserByEntraId("entra-123");

        assertEquals(
                "entra-123",
                result.getEntraId()
        );
    }

    @Test
    void shouldThrowWhenGetUserByEntraIdFails() {

        when(userRepository.findByEntraId("missing"))
                .thenReturn(Optional.empty());

        assertThrows(
                ResourceNotFoundException.class,
                () -> userService.getUserByEntraId("missing")
        );
    }
}