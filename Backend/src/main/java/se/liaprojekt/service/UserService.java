package se.liaprojekt.service;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import se.liaprojekt.dto.GraphResponse;
import se.liaprojekt.dto.UserResponse;
import se.liaprojekt.exception.ResourceNotFoundException;
import se.liaprojekt.model.User;
import se.liaprojekt.repository.UserRepository;

import java.util.*;

@Service
@AllArgsConstructor
public class UserService {
    private final GraphService graphService;
    UserRepository userRepository;

    public UserResponse getUserById(long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        GraphResponse graphResponse = graphService.getUserByEntraId(user.getEntraId());
        UserResponse userResponse = mapToResponse(user, graphResponse);
        return userResponse;
    }

    public List<UserResponse> getAllUsers() {
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


    private void updateFromGraphAPI(List<GraphResponse> graphResponseList) {
        //Get all users in database end put their unique entraId in a set
        List<User> usersInDatabaseList = userRepository.findAll();
        Map<String, User> usersInDataBaseMap = new HashMap<>();
        for (User user : usersInDatabaseList) {
            usersInDataBaseMap.put(user.getEntraId(), user);
        }

        List<User> usersToSave = new ArrayList<>();
        for (GraphResponse graphResponse : graphResponseList) {
            //Add only if database doesn't already contain this entraId
            if (!usersInDataBaseMap.containsKey(graphResponse.id())) {
                usersToSave.add(graphResponseToUser(graphResponse));
            } else {
                usersInDataBaseMap.remove(graphResponse.id());
            }
        }

        userRepository.saveAll(usersToSave);

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
                graphResponse.mail()
        );
    }
}
