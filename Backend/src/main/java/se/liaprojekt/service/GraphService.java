package se.liaprojekt.service;

import com.microsoft.graph.models.User;
import com.microsoft.graph.models.UserCollectionResponse;
import com.microsoft.graph.serviceclient.GraphServiceClient;
import org.springframework.stereotype.Service;
import se.liaprojekt.dto.GraphResponse;
import se.liaprojekt.exception.ResourceNotFoundException;

import java.util.ArrayList;
import java.util.List;

@Service
public class GraphService {

    private final TokenService tokenService;

    public GraphService(TokenService tokenService) {
        this.tokenService = tokenService;
    }

    public List<GraphResponse> getAllUsers() {
        String[] scopes = {"https://graph.microsoft.com/.default"};
        GraphServiceClient graphServiceClient = new GraphServiceClient(tokenService.getCredential(), scopes);

        UserCollectionResponse userCollectionResponse = graphServiceClient.users().get();
        List<GraphResponse> graphResponses = new ArrayList<>();
        if (userCollectionResponse != null && userCollectionResponse.getValue() != null) {
            userCollectionResponse.getValue().forEach((user) -> {
                try {
                    List<String> roles = new ArrayList<>();
//                    user.getAppRoleAssignments().forEach(roleAssignment -> {roles.add(roleAssignment.getId());});
                    graphServiceClient.users().byUserId(
                            user.getId())
                            .appRoleAssignments()
                            .get().getValue().forEach(role -> {
                                roles.add(role.getPrincipalDisplayName());
                            });
                    graphResponses.add(new GraphResponse(
                            user.getId(),
                            user.getDisplayName(),
                            user.getGivenName(),
                            user.getSurname(),
                            user.getMail(),
                            roles
                    ));
                } catch (NullPointerException e) {
                    System.out.println("Error getting roles from: " + user.getDisplayName());
                }

            });
        }
        return graphResponses;
    }

    public GraphResponse getUserByEntraId(String entraId) {
        String[] scopes = {"https://graph.microsoft.com/.default"};
        GraphServiceClient graphServiceClient = new GraphServiceClient(tokenService.getCredential(), scopes);

        User user = graphServiceClient.users().byUserId(entraId).get();
        GraphResponse graphResponse;
        if (user != null) {
            graphResponse = new GraphResponse(
                    user.getId(),
                    user.getDisplayName(),
                    user.getGivenName(),
                    user.getSurname(),
                    user.getMail(),
//                    Objects.requireNonNull(user.getAppRoleAssignments()).getFirst().getId()
                    List.of("")
            );
        } else {
            throw new ResourceNotFoundException("User not found");
        }
        return graphResponse;
    }
}