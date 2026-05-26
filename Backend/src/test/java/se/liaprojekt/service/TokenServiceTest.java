package se.liaprojekt.service;

import com.azure.core.credential.AccessToken;
import com.azure.core.credential.TokenCredential;
import com.azure.core.credential.TokenRequestContext;

import com.azure.identity.DefaultAzureCredential;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.azure.identity.ManagedIdentityCredential;
import com.azure.identity.ManagedIdentityCredentialBuilder;
import com.azure.identity.DefaultAzureCredentialBuilder;

@ExtendWith(MockitoExtension.class)
class TokenServiceTest {

    @InjectMocks
    private TokenService tokenService;

    @Mock
    private RestTemplate restTemplate;

    @Test
    void shouldGetAccessToken() {

        AccessToken accessToken =
                new AccessToken(
                        "fake-token",
                        OffsetDateTime.now().plusHours(1)
                );

        ManagedIdentityCredential credential =
                mock(ManagedIdentityCredential.class);

        when(credential.getToken(any(TokenRequestContext.class)))
                .thenReturn(Mono.just(accessToken));

        try (
                MockedConstruction<ManagedIdentityCredentialBuilder> mocked =
                        mockConstruction(
                                ManagedIdentityCredentialBuilder.class,
                                (builderMock, context) -> {

                                    when(builderMock.build())
                                            .thenReturn(credential);
                                }
                        )
        ) {

            // Act
            String token =
                    tokenService.getAccessToken(restTemplate);

            // Assert
            assertEquals("fake-token", token);
        }
    }

    @Test
    void shouldCreateCredentialWhenNull() {

        DefaultAzureCredential mockCredential =
                mock(DefaultAzureCredential.class);

        try (
                MockedConstruction<DefaultAzureCredentialBuilder> mocked =
                        mockConstruction(
                                DefaultAzureCredentialBuilder.class,
                                (builderMock, context) -> {

                                    when(builderMock.build())
                                            .thenReturn(mockCredential);
                                }
                        )
        ) {

            // Act
            TokenCredential result =
                    tokenService.getCredential();

            // Assert
            assertNotNull(result);
            assertEquals(mockCredential, result);
        }
    }

    @Test
    void shouldReuseExistingCredential() {

        // Arrange
        TokenCredential existing =
                mock(TokenCredential.class);

        ReflectionTestUtils.setField(
                tokenService,
                "credential",
                existing
        );

        // Act
        TokenCredential result =
                tokenService.getCredential();

        // Assert
        assertEquals(existing, result);
    }
}