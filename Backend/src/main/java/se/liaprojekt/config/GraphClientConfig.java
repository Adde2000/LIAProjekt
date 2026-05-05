//package se.liaprojekt.config;
//
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//import org.springframework.security.oauth2.client.*;
//import org.springframework.security.oauth2.client.registration.*;
//import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
//import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProvider;
//import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
//import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
//import org.springframework.security.oauth2.core.AuthorizationGrantType;
//
//@Configuration
//public class GraphClientConfig {
//
//    @Value("${spring.cloud.azure.tenant-id}")
//    private String tenantId;
//
//    @Value("${spring.cloud.azure.credential.client-id}")
//    private String clientId;
//
//    @Value("${spring.cloud.azure.credential.client-secret}")
//    private String clientSecret;
//
//    @Bean
//    public ClientRegistration graphClientRegistration() {
//
//        return ClientRegistration.withRegistrationId("graph")
//                .tokenUri("https://login.microsoftonline.com/" + tenantId + "/oauth2/v2.0/token")
//                .clientId(clientId)
//                .clientSecret(clientSecret)
//                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
//                .scope("https://graph.microsoft.com/.default")
//                .build();
//    }
//
//    @Bean
//    public ClientRegistrationRepository clientRegistrationRepository() {
//        return new InMemoryClientRegistrationRepository(graphClientRegistration());
//    }
//
//    @Bean
//    public OAuth2AuthorizedClientService authorizedClientService(
//            ClientRegistrationRepository repo) {
//        return new InMemoryOAuth2AuthorizedClientService(repo);
//    }
//
//    @Bean
//    public OAuth2AuthorizedClientManager authorizedClientManager(
//            ClientRegistrationRepository repo,
//            OAuth2AuthorizedClientService service) {
//
//        OAuth2AuthorizedClientProvider provider =
//                OAuth2AuthorizedClientProviderBuilder.builder()
//                        .clientCredentials()
//                        .build();
//
//        AuthorizedClientServiceOAuth2AuthorizedClientManager manager =
//                new AuthorizedClientServiceOAuth2AuthorizedClientManager(repo, service);
//
//        manager.setAuthorizedClientProvider(provider);
//
//        return manager;
//    }
//}