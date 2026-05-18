package se.liaprojekt.service;

import com.azure.core.credential.TokenCredential;
import com.azure.core.credential.TokenRequestContext;
import com.azure.identity.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import java.util.List;

@Service
public class TokenService {

    public String getAccessToken(RestTemplate restTemplate) {
        ManagedIdentityCredential credential = new ManagedIdentityCredentialBuilder()

                .build();
        TokenRequestContext tokenRequestContext = new TokenRequestContext();
        tokenRequestContext.setScopes(List.of("https://graph.microsoft.com/.default"));



        return credential.getToken(tokenRequestContext).block().getToken();
    }

    private TokenCredential credential;

    public TokenCredential getCredential() {
        if (credential == null) {
            credential = new DefaultAzureCredentialBuilder()
                    .build();
        }
        return credential;
    }
}