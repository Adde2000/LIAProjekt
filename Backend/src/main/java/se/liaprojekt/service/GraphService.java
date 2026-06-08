package se.liaprojekt.service;

import com.microsoft.graph.models.*;
import com.microsoft.graph.models.UserCollectionResponse;
import com.microsoft.graph.serviceclient.GraphServiceClient;
import com.microsoft.graph.users.item.sendmail.SendMailPostRequestBody;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import se.liaprojekt.dto.GraphResponse;
import se.liaprojekt.exception.ResourceNotFoundException;

import java.util.*;
import java.util.List;

@Service
public class GraphService {

//    private static final String ROLE_NAME_ADMIN       = "sg-app-admin";
//    private static final String ROLE_NAME_COURSEADMIN = "sg-app-courseadmin";
//    private static final String ROLE_NAME_PARTICIPANT = "sg-app-participant";
//
//    private static final Set<String> ROLES = Set.of(
//            ROLE_NAME_ADMIN,
//            ROLE_NAME_COURSEADMIN,
//            ROLE_NAME_PARTICIPANT
//    );

    @Value("${app.email.enabled}")
    private boolean appEmailEnabled;

    @Value("${app.redirect.frontend}")
    private String redirectUrl;

    //TODO Either CLIENT_ID is needed or the ServicePrincipalId directly
    @Value("${spring.cloud.azure.credential.client-id}")
    private String clientId;

    @Value("${azure.graph.mail-user}")
    private String mailUser;

    private final GraphServiceClient graphServiceClient;
    private List<AppRole> appRoles;
    private String resourceId;

    public GraphService(
            TokenService tokenService,
            @Value("${graph.scope}") String[] scopes
    ) {
        graphServiceClient = new GraphServiceClient(tokenService.getCredential(), scopes);
    }

    @PostConstruct
    public void getAppRoles() {
        ServicePrincipalCollectionResponse sp = graphServiceClient
                .servicePrincipals()
                .get(config -> {
                    assert config.queryParameters != null;
                    config.queryParameters.filter = "appId eq '" + clientId + "'";
                });
        assert sp != null;
        appRoles = Objects.requireNonNull(sp.getValue()).getFirst().getAppRoles();
        resourceId = Objects.requireNonNull(sp.getValue()).getFirst().getId();
    }

    public List<GraphResponse> getAllUsers() {
        UserCollectionResponse userCollectionResponse = graphServiceClient.users().get();
        List<GraphResponse> graphResponses = new ArrayList<>();
        if (userCollectionResponse != null && userCollectionResponse.getValue() != null) {
            userCollectionResponse.getValue().forEach((user) -> {
                //getRoles() makes another call to Graph to ask for the roles, this leads to N+1 problem
                //No other way to get them in v1.0
                Set<String> roles = getRoles(user.getId());

                graphResponses.add(mapToGraphResponse(user, roles));
            });
        }
        return graphResponses;
    }

    public GraphResponse getUserByEntraId(String entraId) {
        User user = graphServiceClient.users().byUserId(entraId).get();
        Set<String> roles = getRoles(entraId);

        return mapToGraphResponse(user, roles);
    }

    public GraphResponse inviteUser(String email, String displayName) {
        Invitation invitation = new Invitation();
        invitation.setInvitedUserEmailAddress(email);
        invitation.setInvitedUserDisplayName(displayName);
        invitation.setInviteRedirectUrl(redirectUrl);
        invitation.setSendInvitationMessage(appEmailEnabled);
        Invitation result = graphServiceClient.invitations().post(invitation);
        if (result != null) {
            return mapToGraphResponse(result.getInvitedUser());
        } else {
            throw new ResourceNotFoundException("Graph returned null for: " + email);
        }
    }

    public void setRoles(String entraId, List<String> roles) {

        for (String role : roles) {
            UUID appRoleId = null;

            assert appRoles != null;
            for (AppRole appRole : appRoles) {
                if (Objects.equals(appRole.getDisplayName(), role)) {
                    appRoleId = appRole.getId();
                }
            }

            if (appRoleId == null) {
                throw new ResourceNotFoundException("Role not found for: " + role);
            }

            AppRoleAssignment assignment = new AppRoleAssignment();
            assignment.setPrincipalId(UUID.fromString(entraId));
            assignment.setResourceId(UUID.fromString(resourceId));
            assignment.setAppRoleId(appRoleId);

            AppRoleAssignment result = graphServiceClient
                    .users().byUserId(entraId).appRoleAssignments().post(assignment);
            System.out.println(result);
        }


    }

    public void deleteUser(String entraId) {
        graphServiceClient.users().byUserId(entraId).delete();
    }

    private GraphResponse mapToGraphResponse(User user, Set<String> roles) {
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

    private GraphResponse mapToGraphResponse(User user) {
        return mapToGraphResponse(user, Set.of());
    }

//    private Set<String> getRoles(String entraId) {
//        Set<String> roles = new HashSet<>();
//        try {
//            graphServiceClient.users()
//                    .byUserId(entraId)
//                    .appRoleAssignments()
//                    .get().getValue().forEach(role -> {
//                        roles.add(role.getPrincipalDisplayName());
//                    });
//        } catch (NullPointerException e) {
//            throw new ResourceNotFoundException("User not found");
//        }
//        return roles;
//    }

    private Set<String> getRoles(String entraId) {
        Set<String> roles = new HashSet<>();
        try {
            graphServiceClient.users()
                    .byUserId(entraId)
                    .appRoleAssignments()
                    .get().getValue().forEach(assignment -> {
                        appRoles.stream()
                                .filter(r -> r.getId().equals(assignment.getAppRoleId()))
                                .findFirst()
                                .ifPresent(r -> roles.add(r.getValue())); // or getDisplayName()
                    });

        } catch (NullPointerException e) {
            throw new ResourceNotFoundException("User not found");
        }
        return roles;
    }

    //removes everything before final '-' and only leaves the roles name
    private Set<String> translateRoles(Set<String> roles) {
        return roles;
//        Set<String> translatedRoles = new HashSet<>();
//        for (String role : roles) {
//            if (ROLES.contains(role)) {
//                int index = role.lastIndexOf('-');
//                translatedRoles.add(role.substring(index + 1));
//            }
//        }
//        return translatedRoles;
    }

    public void sendEmail(String to,
                          String subject,
                          String htmlBody) {
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