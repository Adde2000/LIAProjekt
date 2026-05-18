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
import java.util.List;

@Service
public class GraphService {

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
                graphResponses.add(new GraphResponse(
                        user.getId(),
                        user.getDisplayName(),
                        user.getGivenName(),
                        user.getSurname(),
                        user.getMail()
                ));
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
                    user.getMail()
            );
        } else {
            throw new ResourceNotFoundException("User not found");
        }
        return graphResponse;
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