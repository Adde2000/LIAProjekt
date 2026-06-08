package se.liaprojekt.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import se.liaprojekt.controller.util.Roles;
import se.liaprojekt.dto.UserResponse;
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
    @PreAuthorize(Roles.ROLE_ADMIN)
    public ResponseEntity<List<UserResponse>> getAllUsers() {
        List<UserResponse> userResponseList = userService.getAllUserResponses();
        return ResponseEntity.ok(userResponseList);
    }

    //Admin
    @GetMapping("/{userId}")
    @PreAuthorize(Roles.ROLE_ADMIN)
    public ResponseEntity<UserResponse> getUserById(@PathVariable long userId) {
        UserResponse userResponse = userService.getUserResponseById(userId);
        return ResponseEntity.ok(userResponse);
    }

    @PostMapping("/invite")
    @PreAuthorize(Roles.ROLE_ADMIN)
    public ResponseEntity<List<UserResponse>> inviteUser(@RequestBody List<InviteRequest> invites) {
        List<UserResponse> userResponses = new ArrayList<>();
        for (InviteRequest invite : invites) {
            Roles.checkRolesValid(invite.roles);
            userResponses.add(userService.inviteUser(invite.email, invite.displayName, invite.roles));
        }
        return ResponseEntity.ok(userResponses);
    }

    @DeleteMapping("/{userId}")
    @PreAuthorize(Roles.ROLE_ADMIN)
    public ResponseEntity<Void> deleteUser(@PathVariable long userId) {
        userService.deleteUser(userId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{userId}")
    @PreAuthorize(Roles.ROLE_ADMIN)
    public ResponseEntity<UserResponse> updateCourse(@PathVariable long userId, @RequestBody InviteRequest inviteRequest) {
        Roles.checkRolesValid(inviteRequest.roles);
        UserResponse userResponse = userService.updateUser(userId, inviteRequest.displayName, inviteRequest.roles);
        return ResponseEntity.ok(userResponse);
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> getCurrentUser() {
        String entraId = currentUserService.getEntraId();
        UserResponse user = userService.getUserResponseByEntraId(entraId);
        return ResponseEntity.ok(user);
    }

    //Participant
    @GetMapping("/me/courses")
    @PreAuthorize(Roles.ROLE_PARTICIPANT)
    public ResponseEntity<List<Map<String, Object>>> getMyCourses() {
        String entraId = currentUserService.getEntraId();
        long userId = userService.getUserByEntraId(entraId).getId();
        List<Map<String, Object>> courseResponseList = courseService.getAllRegisteredCourses(userId);
        return ResponseEntity.ok(courseResponseList);
    }

    public record InviteRequest(String email, String displayName, List<String> roles) {}
}