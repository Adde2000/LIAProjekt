package se.liaprojekt.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import se.liaprojekt.controller.util.Roles;
import se.liaprojekt.dto.UserRequest;
import se.liaprojekt.dto.UserResponse;
import se.liaprojekt.model.User;
import se.liaprojekt.service.CourseService;
import se.liaprojekt.service.CurrentUserService;
import se.liaprojekt.service.UserService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;
    private final CourseService courseService;
    private final CurrentUserService currentUserService;


    //Admin
    @GetMapping("/all")
//    @PreAuthorize(Roles.ADMIN)
    public ResponseEntity<List<UserResponse>> getAllUsers() {
        List<UserResponse> userResponseList = userService.getAllUserResponses();
        return ResponseEntity.ok(userResponseList);
    }

    //Admin
    @GetMapping("/{userId}")
    @PreAuthorize(Roles.ADMIN)
    public ResponseEntity<UserResponse> getUserById(@PathVariable long userId) {
        UserResponse userResponse = userService.getUserResponseById(userId);
        return ResponseEntity.ok(userResponse);
    }

    @PostMapping("/invite")
    public ResponseEntity<List<UserResponse>> inviteUser(@RequestBody List<InviteRequest> invites) {
        List<UserResponse> userResponses = new ArrayList<>();
        for (InviteRequest invite : invites) {
            userResponses.add(userService.inviteUser(invite.email, invite.displayName, List.of("Admin", "Participant")));
        }
        return ResponseEntity.ok(userResponses);
    }

    public record InviteRequest(String email, String displayName) {}

    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> deleteUser(@PathVariable long userId) {
        userService.deleteUser(userId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/test/{userId}")
    public ResponseEntity<String> test(@PathVariable long userId) {
        User user = userService.getUserById(userId);
        userService.assignRoles(user.getEntraId(), List.of(Roles.ADMIN));
        return ResponseEntity.ok("Test");
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> getCurrentUser() {
        String entraId = currentUserService.getEntraId();
        UserResponse user = userService.getUserResponseByEntraId(entraId);
        return ResponseEntity.ok(user);
    }

    //Participant
    @GetMapping("/me/courses")
    @PreAuthorize(Roles.PARTICIPANT)
    public ResponseEntity<List<Map<String, Object>>> getMyCourses(@AuthenticationPrincipal Jwt jwt) {
        String entraId = jwt.getClaim("oid");
        long userId = userService.getUserByEntraId(entraId).getId();
        List<Map<String, Object>> courseResponseList = courseService.getAllRegisteredCourses(userId);
        return ResponseEntity.ok(courseResponseList);
    }

}