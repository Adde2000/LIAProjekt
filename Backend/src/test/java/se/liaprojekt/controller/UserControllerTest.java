package se.liaprojekt.controller;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import se.liaprojekt.dto.GraphResponse;
import se.liaprojekt.dto.UserResponse;
import se.liaprojekt.exception.ResourceNotFoundException;
import se.liaprojekt.model.User;
import se.liaprojekt.repository.UserRepository;
import se.liaprojekt.service.GraphService;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@AutoConfigureMockMvc
class UserControllerTest {

    @Autowired
    private UserController controller;

    @MockBean
    private GraphService graphService;

    @Autowired
    private UserRepository userRepository;

    private static final int NUMBER_OF_TEST_USERS = 10;
    private static List<GraphResponse> users;

    private User testUser;

    @BeforeAll
    static void setup() {
        users = new ArrayList<>();
        for (int i = 0; i < NUMBER_OF_TEST_USERS; i++) {
            users.add(new GraphResponse(
                    "entra" + i,
                    "TestTestsson" + i,
                    "Test",
                    "Testsson" + i,
                    "test.testsson" + i + "@testing.se",
                    Set.of("Testing Role")
            ));
        }
    }

    @BeforeEach
    void setUpBeforeEach() {
        User user = User.builder()
                .entraId(users.getLast().id())
                .build();
        testUser = userRepository.save(user);
    }

    @AfterEach
    void tearDownAfterEach() {
        userRepository.deleteAll();
    }

    @Test
    void getAllUsers() {
        Mockito.when(graphService.getAllUsers()).thenReturn(users);

        ResponseEntity<List<UserResponse>> responseEntity = controller.getAllUsers();
        assertNotNull(responseEntity, "Response entity is null");
        assertEquals(HttpStatus.OK, responseEntity.getStatusCode(), "Status code is incorrect");
        assertNotNull(responseEntity.getBody(), "Response body is null");
        assertFalse(responseEntity.getBody().isEmpty(), "Response body is empty");
        assertEquals(NUMBER_OF_TEST_USERS, responseEntity.getBody().size(), "Number of users is incorrect");

        List<UserResponse> userResponses = responseEntity.getBody();

        for (int i = 0; i < userResponses.size(); i++) {
            assertEquals(users.get(i).displayName(), userResponses.get(i).displayName(), "Display name is incorrect");
            assertEquals(users.get(i).givenName(), userResponses.get(i).givenName(), "Given name is incorrect");
            assertEquals(users.get(i).surname(), userResponses.get(i).surname(), "Surname is incorrect");
            assertEquals(users.get(i).mail(), userResponses.get(i).mail(), "Mail is incorrect");
            assertEquals(users.get(i).role(), userResponses.get(i).role(), "Role is incorrect");
        }

    }

    @Test
    void getUserByIdNotFound() {
        assertThrows(ResourceNotFoundException.class, () -> controller.getUserById(-1L));
    }

    @Test
    void getUserById() {
        GraphResponse user = users.getLast();
        Mockito.when(graphService.getUserByEntraId(user.id())).thenReturn(user);

        ResponseEntity<UserResponse> responseEntity = controller.getUserById(testUser.getId());
        assertNotNull(responseEntity, "Response entity is null");
        assertEquals(HttpStatus.OK, responseEntity.getStatusCode(), "Status code is incorrect");
        assertNotNull(responseEntity.getBody(), "Response body is null");

        UserResponse userResponse = responseEntity.getBody();

        assertEquals(user.displayName(), userResponse.displayName(), "Display name is incorrect");
        assertEquals(user.givenName(), userResponse.givenName(), "Given name is incorrect");
        assertEquals(user.surname(), userResponse.surname(), "Surname is incorrect");
        assertEquals(user.mail(), userResponse.mail(), "Mail is incorrect");
        assertEquals(user.role(), userResponse.role(), "Role is incorrect");
    }

    @Test
    void getCurrentUser() {
        //TODO write proper test when needed
        assertEquals(HttpStatus.OK, controller.getCurrentUser().getStatusCode(), "Status code is incorrect");
    }

    @Test
    void getMyCourses() {
        //TODO write proper test when needed
        assertEquals(HttpStatus.OK, controller.getMyCourses().getStatusCode(), "Status code is incorrect");
    }
}