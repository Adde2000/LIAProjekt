package se.liaprojekt.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import se.liaprojekt.dto.GraphResponse;

import java.util.List;

@Service
public class GraphService {

    private final TokenService tokenService;
    private final RestTemplate restTemplate;

    @Value("${graph.base-url}")
    private String graphBaseUrl;

    public GraphService(TokenService tokenService) {
        this.tokenService = tokenService;
        this.restTemplate = new RestTemplate();
    }

    public List<GraphResponse> getAllUsers() {
        String token = tokenService.getAccessToken(restTemplate);

        HttpHeaders headers = new HttpHeaders();
        HttpEntity<String> entity = new HttpEntity<>(headers);
        headers.setBearerAuth(token);
        ResponseEntity<GraphAPIResponse> response = restTemplate.exchange(
                graphBaseUrl + "/users",
                HttpMethod.GET,
                entity,
                GraphAPIResponse.class
        );

        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            return response.getBody().value();
        } else {
            //TODO throw appropriate exception when request fail
        }

        return null;
    }

    public GraphResponse getUserByEntraId(String entraId) {
        String token = tokenService.getAccessToken(restTemplate);
        HttpHeaders headers = new HttpHeaders();
        HttpEntity<String> entity = new HttpEntity<>(headers);
        headers.setBearerAuth(token);
        ResponseEntity<GraphResponse> response = restTemplate.exchange(
                graphBaseUrl + "/users/" + entraId,
                HttpMethod.GET,
                entity,
                GraphResponse.class
        );
        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            return response.getBody();
        } else {
            //TODO throw appropriate exception when request fail
        }

        return null;
    }

    private record GraphAPIResponse(
            List<GraphResponse> value) {}
}