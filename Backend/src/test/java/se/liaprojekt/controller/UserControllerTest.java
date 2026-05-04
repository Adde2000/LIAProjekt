//package se.liaprojekt.controller;
//
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.Test;
//import org.springframework.http.HttpStatus;
//import org.springframework.http.ResponseEntity;
//import se.liaprojekt.dto.CourseResponse;
//import se.liaprojekt.dto.UserResponse;
//
//import java.util.List;
//
//import static org.junit.jupiter.api.Assertions.*;
//
//class UserControllerTest {
//
//    private UserController controller;
//
//    @BeforeEach
//    void setUp() {
//        controller = new UserController();
//    }
//
//    @Test
//    void getAllUsers_shouldReturnEmptyListInitially() {
//        ResponseEntity<List<UserResponse>> response =
//                controller.getAllUsers();
//
//        assertNotNull(response);
//        assertEquals(HttpStatus.OK, response.getStatusCode());
//
//        assertNotNull(response.getBody());
//        assertEquals(0, response.getBody().size());
//    }
//
//    @Test
//    void getCurrentUser_shouldReturnAuthenticatedUser() {
//        ResponseEntity<UserResponse> response =
//                controller.getCurrentUser();
//
//        assertNotNull(response);
//        assertEquals(HttpStatus.OK, response.getStatusCode());
//
//        assertNotNull(response.getBody());
//        assertNotNull(response.getBody().id());
//    }
//
//    @Test
//    void getCurrentUser_shouldAlwaysReturnSameUserInSession() {
//        ResponseEntity<UserResponse> response1 =
//                controller.getCurrentUser();
//
//        ResponseEntity<UserResponse> response2 =
//                controller.getCurrentUser();
//
//        assertNotNull(response1);
//        assertNotNull(response2);
//
//        assertNotNull(response1.getBody());
//        assertNotNull(response2.getBody());
//
//        assertEquals(response1.getBody().id(), response2.getBody().id());
//    }
//
//    @Test
//    void getMyCourses_shouldReturnEmptyListInitially() {
//        ResponseEntity<List<CourseResponse>> response =
//                controller.getMyCourses();
//
//        assertNotNull(response);
//        assertEquals(HttpStatus.OK, response.getStatusCode());
//
//        assertNotNull(response.getBody());
//        assertEquals(0, response.getBody().size());
//    }
//}