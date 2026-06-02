package se.liaprojekt.service;

import jakarta.annotation.PostConstruct;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import se.liaprojekt.dto.GraphResponse;
import se.liaprojekt.dto.UserResponse;
import se.liaprojekt.event.UserCreatedEvent;
import se.liaprojekt.exception.ResourceNotFoundException;
import se.liaprojekt.model.User;
import org.springframework.context.ApplicationEventPublisher;
import se.liaprojekt.repository.UserRepository;

import java.util.*;

@Slf4j
@Service
@AllArgsConstructor
public class UserService {
    private final GraphService graphService;
    private final ApplicationEventPublisher eventPublisher;
    private final UserRepository userRepository;

    @PostConstruct
    public void init() {
        try {
            log.info("Starting user sync from Graph API...");

            List<GraphResponse> graphResponseList = graphService.getAllUsers();
            updateFromGraphAPI(graphResponseList);

            log.info("User sync completed successfully");

        } catch (Exception e) {
            log.error("User sync failed during startup. App will continue running.", e);
        }
    }

    public UserResponse getUserResponseById(long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        GraphResponse graphResponse = graphService.getUserByEntraId(user.getEntraId());
        return mapToResponse(user, graphResponse);
    }

    public List<UserResponse> getAllUserResponses() {
        List<GraphResponse> graphResponseList = graphService.getAllUsers();
        updateFromGraphAPI(graphResponseList);

        //Create UserResponse
        List<UserResponse> userResponseList = new ArrayList<>();
        for (GraphResponse graphResponse : graphResponseList) {
            Optional<User> optionalUser = userRepository.findByEntraId(graphResponse.id());
            if (optionalUser.isPresent()) {
                User user = optionalUser.get();
                UserResponse userResponse = mapToResponse(user, graphResponse);
                userResponseList.add(userResponse);
            }
        }
        return userResponseList;
    }

    public User getUserById(long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
    }

    public User getUserByEntraId(String entraId) {
        return userRepository.findByEntraId(entraId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + entraId));
    }

    private void updateFromGraphAPI(List<GraphResponse> graphResponseList) {

        //Get all users in database end put their unique entraId in a set
        List<User> usersInDatabaseList = userRepository.findAll();

        Map<String, User> usersInDataBaseMap = new HashMap<>();
        for (User user : usersInDatabaseList) {
            usersInDataBaseMap.put(user.getEntraId(), user);
        }

        List<User> usersToSave = new ArrayList<>();

        // TRACK NEW USERS
        // (used later for events)
        List<String> newlyCreatedUserIds = new ArrayList<>();

        for (GraphResponse graphResponse : graphResponseList) {

            //Add only if database doesn't already contain this entraId
            if (!usersInDataBaseMap.containsKey(graphResponse.id())) {

                User newUser = graphResponseToUser(graphResponse);
                usersToSave.add(newUser);

                // STORE ENTRA ID
                // (event published AFTER successful DB save)
                newlyCreatedUserIds.add(graphResponse.id());


            } else {
                usersInDataBaseMap.remove(graphResponse.id());
            }
        }

        List<User> savedUsers = userRepository.saveAll(usersToSave);

        // PUBLISH EVENTS
        // ONLY AFTER SUCCESSFUL SAVE
        for (User user : savedUsers) {
            if (newlyCreatedUserIds.contains(user.getEntraId())) {
                eventPublisher.publishEvent(
                        new UserCreatedEvent(user.getEntraId())
                );
            }
        }

        //Users left in the map are users that have been removed from graph and should be removed from database
        userRepository.deleteAll(usersInDataBaseMap.values());
    }

    private User graphResponseToUser(GraphResponse graphResponse) {
        return User.builder()
                .entraId(graphResponse.id())
                .build();
    }

    private UserResponse mapToResponse(User user, GraphResponse graphResponse) {
        return new UserResponse(
                user.getId(),
                graphResponse.displayName(),
                graphResponse.givenName(),
                graphResponse.surname(),
                graphResponse.mail(),
                graphResponse.role()
        );
    }
}
