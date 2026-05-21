package se.liaprojekt.service;

import com.microsoft.graph.models.*;
import com.microsoft.graph.models.UserCollectionResponse;
import com.microsoft.graph.serviceclient.GraphServiceClient;
import com.microsoft.graph.users.item.sendmail.SendMailPostRequestBody;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import se.liaprojekt.dto.GraphResponse;
import se.liaprojekt.exception.ResourceNotFoundException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class GraphService {

    private static final String ROLE_NAME_ADMIN       = "sg-app-admin";
    private static final String ROLE_NAME_COURSEADMIN = "sg-app-courseadmin";
    private static final String ROLE_NAME_PARTICIPANT = "sg-app-participant";

    private static final Set<String> ROLES = Set.of(
            ROLE_NAME_ADMIN,
            ROLE_NAME_COURSEADMIN,
            ROLE_NAME_PARTICIPANT
    );

    @Value("${azure.graph.mail-user}")
    private String mailUser;

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
                //getRoles() makes another call to Graph to ask for the roles, this leads to N+1 problem
                //No other way to get them in v1.0
                Set<String> roles = getRoles(user.getId(), graphServiceClient);

                graphResponses.add(new GraphResponse(
                        user.getId(),
                        user.getDisplayName(),
                        user.getGivenName(),
                        user.getSurname(),
                        user.getMail(),
                        translateRoles(roles)
                ));
            });
        }
        return graphResponses;
    }

    public GraphResponse getUserByEntraId(String entraId) {
        String[] scopes = {"https://graph.microsoft.com/.default"};
        GraphServiceClient graphServiceClient = new GraphServiceClient(tokenService.getCredential(), scopes);

        User user = graphServiceClient.users().byUserId(entraId).get();
        Set<String> roles = getRoles(entraId, graphServiceClient);

        GraphResponse graphResponse;
        if (user != null) {
            graphResponse = new GraphResponse(
                    user.getId(),
                    user.getDisplayName(),
                    user.getGivenName(),
                    user.getSurname(),
                    user.getMail(),
                    translateRoles(roles)
            );
        } else {
            throw new ResourceNotFoundException("User not found");
        }
        return graphResponse;
    }

    private Set<String> getRoles(String entraId, GraphServiceClient graphServiceClient) {
        Set<String> roles = new HashSet<>();
        try {
            graphServiceClient.users().byUserId(
                            entraId)
                    .appRoleAssignments()
                    .get().getValue().forEach(role -> {
                        roles.add(role.getPrincipalDisplayName());
                    });
        } catch (NullPointerException e) {
            throw new ResourceNotFoundException("User not found");
        }
        return roles;
    }

    //removes everything before final '-' and only leaves the roles name
    private Set<String> translateRoles(Set<String> roles) {
        Set<String> translatedRoles = new HashSet<>();
        for (String role : roles) {
            if (ROLES.contains(role)) {
                int index = role.lastIndexOf('-');
                translatedRoles.add(role.substring(index + 1));
            }
        }
        return translatedRoles;
    }

    public void sendEmail(String to,
                          String subject,
                          String htmlBody) {
        String[] scopes = {"https://graph.microsoft.com/.default"};
        GraphServiceClient graphServiceClient = new GraphServiceClient(tokenService.getCredential(), scopes);

        // Email body
        ItemBody body = new ItemBody();
        body.setContentType(BodyType.Html);
        body.setContent(htmlBody);

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

        // Request body
        SendMailPostRequestBody requestBody =
                new SendMailPostRequestBody();

        requestBody.setMessage(message);
        requestBody.setSaveToSentItems(true);

        graphServiceClient
                .users()
                .byUserId(mailUser)
                .sendMail()
                .post(requestBody);
    }
}