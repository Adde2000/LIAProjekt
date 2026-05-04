package se.liaprojekt.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

@Service
public class TokenService {
    @Value("${TENANT_ID}")
    private String tenantId;

    @Value("${CLIENT_ID}")
    private String clientId;

    @Value("${CLIENT_SECRET}")
    private String clientSecret;


    private TokenResponseBody tokenResponseBody;
    private long tokenExpiryTimeMillis;

    public String getAccessToken(RestTemplate restTemplate) {
        String url = "https://login.microsoftonline.com/" + tenantId + "/oauth2/v2.0/token";

        //If a token has been saved, and it doesn't expire within the next minute return current token
        if (tokenResponseBody != null && tokenExpiryTimeMillis > System.currentTimeMillis() + 60000) {
            return tokenResponseBody.access_token;
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

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

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

        ResponseEntity<TokenResponseBody> response = restTemplate.postForEntity(url, request, TokenResponseBody.class);

        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            tokenResponseBody = response.getBody();
            tokenExpiryTimeMillis = System.currentTimeMillis() + tokenResponseBody.expires_in * 1000;
            return tokenResponseBody.access_token;
        } else {
            //TODO throw appropriate exception when request fail
        }

        return response.getBody().access_token;
    }


    private record TokenResponseBody(String access_token, String token_type, long expires_in) {}
}