package se.liaprojekt.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private static final Logger log = LoggerFactory.getLogger(UserController.class);

    private final UserService userService;
    private final CourseService courseService;
    private final CurrentUserService currentUserService;


    //Admin
    @GetMapping("/all")
    @PreAuthorize(Roles.ROLE_ADMIN)
    public ResponseEntity<List<UserResponse>> getAllUsers() {
        log.info("Get all users");
        List<UserResponse> userResponseList = userService.getAllUserResponses();
        return ResponseEntity.ok(userResponseList);
    }

    //Admin
    @GetMapping("/{userId}")
    @PreAuthorize(Roles.ROLE_ADMIN)
    public ResponseEntity<UserResponse> getUserById(@PathVariable long userId) {
        log.info("Get user by id: {}", userId);
        UserResponse userResponse = userService.getUserResponseById(userId);
        return ResponseEntity.ok(userResponse);
    }

    @PostMapping("/invite")
    @PreAuthorize(Roles.ROLE_ADMIN)
    public ResponseEntity<List<UserResponse>> inviteUser(@Valid @RequestBody List<InviteRequest> invites) {
        log.info("Inviting {} user(s)", invites.size());
        List<UserResponse> userResponses = new ArrayList<>();
        for (InviteRequest invite : invites) {
            Roles.checkRolesValid(invite.roles);
            UserResponse created = userService.inviteUser(invite.email, invite.displayName, invite.roles);
            log.info("Invited user id={} roles={}", created.id(), invite.roles);
            userResponses.add(created);
        }
        return ResponseEntity.ok(userResponses);
    }

    @DeleteMapping("/{userId}")
    @PreAuthorize(Roles.ROLE_ADMIN)
    public ResponseEntity<Void> deleteUser(@PathVariable long userId) {
        log.info("Delete user: {}", userId);
        userService.deleteUser(userId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{userId}")
    @PreAuthorize(Roles.ROLE_ADMIN)
    public ResponseEntity<UserResponse> updateUser(@PathVariable long userId, @RequestBody InviteRequest inviteRequest) {
        log.info("Update user: {}", userId);
        Roles.checkRolesValid(inviteRequest.roles);
        UserResponse userResponse = userService.updateUser(userId, inviteRequest.displayName, inviteRequest.roles);
        log.info("Updated user id={} roles={}", userId, inviteRequest.roles);
        return ResponseEntity.ok(userResponse);
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> getCurrentUser() {
        log.info("Get current user");
        String entraId = currentUserService.getEntraId();
        UserResponse user = userService.getUserResponseByEntraId(entraId);
        return ResponseEntity.ok(user);
    }

    //Participant
    @GetMapping("/me/courses")
    @PreAuthorize(Roles.ROLE_PARTICIPANT)
    public ResponseEntity<List<Map<String, Object>>> getMyCourses() {
        log.info("Get courses for current user");
        String entraId = currentUserService.getEntraId();
        long userId = userService.getUserByEntraId(entraId).getId();
        List<Map<String, Object>> courseResponseList = courseService.getAllRegisteredCourses(userId);
        return ResponseEntity.ok(courseResponseList);
    }

    public record InviteRequest(
            @NotBlank
            @Email
            String email,

            @NotBlank
            String displayName,

            @NotEmpty
            List<String> roles
    ) {}
}