package se.liaprojekt.service;

import com.microsoft.graph.models.*;
import com.microsoft.graph.models.UserCollectionResponse;
import com.microsoft.graph.models.odataerrors.ODataError;
import com.microsoft.graph.serviceclient.GraphServiceClient;
import com.microsoft.graph.users.item.sendmail.SendMailPostRequestBody;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import se.liaprojekt.dto.GraphResponse;
import se.liaprojekt.exception.ResourceNotFoundException;

import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class GraphService {

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
        assert appRoles != null;
        for (AppRole appRole : appRoles) {
            appRole.setDisplayName(Objects.requireNonNull(appRole.getDisplayName()).toLowerCase());
        }
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

    public void updateUser(String entraId, String displayName) {
        User user = new User();
        user.setDisplayName(displayName);

        graphServiceClient
                .users()
                .byUserId(entraId)
                .patch(user);
    }

    public void setRoles(String entraId, List<String> roles) {

        for (String role : roles) {
            UUID appRoleId = null;

            for (AppRole appRole : appRoles) {
                if (Objects.equals(appRole.getDisplayName(), role.toLowerCase())) {
                    appRoleId = appRole.getId();
                    break;
                }
            }

            if (appRoleId == null) {
                throw new ResourceNotFoundException("Role not found for: " + role);
            }

            AppRoleAssignment assignment = new AppRoleAssignment();
            assignment.setPrincipalId(UUID.fromString(entraId));
            assignment.setResourceId(UUID.fromString(resourceId));
            assignment.setAppRoleId(appRoleId);

            graphServiceClient
                    .users().byUserId(entraId).appRoleAssignments().post(assignment);
        }
    }

    void updateRoles(String entraId, List<String> newRoles) {
        List<String> newAppRoleIds = new ArrayList<>();

        for (String role : newRoles) {
            for (AppRole appRole : appRoles) {
                if (Objects.equals(appRole.getDisplayName(), role.toLowerCase())) {
                    newAppRoleIds.add(appRole.getId().toString());
                    break;
                }
            }
        }

        List<AppRoleAssignment> currentAssignments = graphServiceClient
                .users()
                .byUserId(entraId)
                .appRoleAssignments()
                .get()
                .getValue();

        Set<String> currentRoleIds = currentAssignments.stream()
                .map(a -> a.getAppRoleId().toString())
                .collect(Collectors.toSet());

        Set<String> desiredRoleIds = new HashSet<>(newAppRoleIds);

        // Delete roles not in the new set
        currentAssignments.stream()
                .filter(a -> !desiredRoleIds.contains(a.getAppRoleId().toString()))
                .forEach(a -> graphServiceClient
                        .users()
                        .byUserId(entraId)
                        .appRoleAssignments()
                        .byAppRoleAssignmentId(a.getId())
                        .delete()
                );

        // Add roles not already assigned
        desiredRoleIds.stream()
                .filter(id -> !currentRoleIds.contains(id))
                .forEach(id -> {
                    AppRoleAssignment assignment = new AppRoleAssignment();
                    assignment.setPrincipalId(UUID.fromString(entraId));
                    assignment.setResourceId(UUID.fromString(resourceId));
                    assignment.setAppRoleId(UUID.fromString(id));
                    graphServiceClient.users()
                            .byUserId(entraId)
                            .appRoleAssignments()
                            .post(assignment);
                });
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
                    roles
            );
        } else {
            throw new ResourceNotFoundException("User not found");
        }
        return graphResponse;
    }

    private GraphResponse mapToGraphResponse(User user) {
        return mapToGraphResponse(user, Set.of());
    }

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
        } catch (ODataError ignored) {

        }
        return roles;
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