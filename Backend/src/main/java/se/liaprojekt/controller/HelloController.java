package se.liaprojekt.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import se.liaprojekt.dto.GraphAPIResponse;
import se.liaprojekt.dto.UserResponse;

import java.util.List;
import java.util.Map;

@RestController
public class HelloController {
    @GetMapping("admin")
    @ResponseBody
//    @PreAuthorize("hasAuthority('APPROLE_Participant')")
    public String Participant() {
        RestTemplate restTemplate = new RestTemplate();

        String baseUrl = "https://graph.microsoft.com/v1.0";

        HttpHeaders headers = new HttpHeaders();
        HttpEntity entity = new HttpEntity(headers);
        headers.setBearerAuth(getAccessToken(restTemplate));
        ResponseEntity<GraphAPIResponse> answer = restTemplate.exchange(
//                baseUrl + "/users?select=id,displayname,mail",
                baseUrl + "/users",
                HttpMethod.GET,
                entity,
                GraphAPIResponse.class
        );
        for (UserResponse user : answer.getBody().value()) {
            System.out.println(user + " role: " + getUserRole(restTemplate, user.id()));
        }
        return "Participant message: \n" + answer.getBody().toString();
    }

    private String getUserRole(RestTemplate restTemplate, String userId) {
        String baseUrl = "https://graph.microsoft.com/v1.0";
        HttpHeaders headers = new HttpHeaders();
        HttpEntity entity = new HttpEntity(headers);
        headers.setBearerAuth(getAccessToken(restTemplate));
        ResponseEntity<RoleAPIResponse> answer = restTemplate.exchange(
//                baseUrl + "/users?select=id,displayname,mail",
                baseUrl + "/users/" + userId + "/appRoleAssignments",
                HttpMethod.GET,
                entity,
                RoleAPIResponse.class
        );
        return answer.getBody().value.toString();
    }

    @Value("${TENANT_ID}")
    private String tenantId;

    @Value("${CLIENT_ID}")
    private String clientId;

    @Value("${CLIENT_SECRET}")
    private String clientSecret;

    private String getAccessToken(RestTemplate restTemplate) {

        String url =
                "https://login.microsoftonline.com/"
                        + tenantId
                        + "/oauth2/v2.0/token";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(
                MediaType.APPLICATION_FORM_URLENCODED
        );

        MultiValueMap<String, String> body =
                new LinkedMultiValueMap<>();

        body.add("client_id", clientId);
        body.add("client_secret", clientSecret);
        body.add(
                "scope",
                "https://graph.microsoft.com/.default"
        );
        body.add(
                "grant_type",
                "client_credentials"
        );

        HttpEntity<MultiValueMap<String, String>> request =
                new HttpEntity<>(body, headers);

        ResponseEntity<Map> response =
                restTemplate.postForEntity(
                        url,
                        request,
                        Map.class
                );

        return (String) response.getBody()
                .get("access_token");
    }

    private record RoleAPIResponse(List<RoleResponse> value)  {}
    private record RoleResponse(String principalDisplayName) {}
}
