//package se.liaprojekt.config;
//
//import com.azure.identity.ClientSecretCredential;
//import com.azure.identity.ClientSecretCredentialBuilder;
//import com.microsoft.graph.serviceclient.GraphServiceClient;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//
//@Configuration
//public class GraphConfig {
//
//    @Value("${AZURE_CLIENT_ID}")
//    private String clientId;
//
//    @Value("${AZURE_CLIENT_SECRET}")
//    private String clientSecret;
//
//    @Value("${AZURE_TENANT_ID}")
//    private String tenantId;
//
//    @Bean
//    public GraphServiceClient graphServiceClient() {
//
//        ClientSecretCredential credential =
//                new ClientSecretCredentialBuilder()
//                        .clientId(clientId)
//                        .clientSecret(clientSecret)
//                        .tenantId(tenantId)
//                        .build();
//
//        String[] scopes = {
//                "https://graph.microsoft.com/.default"
//        };
//
//        return new GraphServiceClient(credential, scopes);
//    }
//}