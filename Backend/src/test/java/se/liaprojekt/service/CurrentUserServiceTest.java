package se.liaprojekt.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CurrentUserServiceTest {

    private final CurrentUserService currentUserService =
            new CurrentUserService();

    @AfterEach
    void tearDown() {

        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldReturnEntraId() {

        // Arrange
        Jwt jwt = new Jwt(
                "token",
                null,
                null,
                Map.of("alg", "none"),
                Map.of(
                        "oid", "entra-123",
                        "name", "Test User"
                )
        );

        JwtAuthenticationToken authentication =
                new JwtAuthenticationToken(jwt);

        SecurityContextHolder.getContext()
                .setAuthentication(authentication);

        // Act
        String entraId =
                currentUserService.getEntraId();

        // Assert
        assertEquals("entra-123", entraId);
    }

    @Test
    void shouldThrowWhenNoAuthenticationExists() {

        // Arrange
        SecurityContextHolder.clearContext();

        // Act + Assert
        assertThrows(
                IllegalStateException.class,
                () -> currentUserService.getEntraId()
        );
    }

    @Test
    void shouldThrowWhenOidClaimMissing() {

        // Arrange
        Jwt jwt = new Jwt(
                "token",
                null,
                null,
                Map.of("alg", "none"),
                Map.of(
                        "name", "Test User"
                )
        );

        JwtAuthenticationToken authentication =
                new JwtAuthenticationToken(jwt);

        SecurityContextHolder.getContext()
                .setAuthentication(authentication);

        // Act + Assert
        assertThrows(
                IllegalStateException.class,
                () -> currentUserService.getEntraId()
        );
    }

    @Test
    void shouldReturnName() {

        // Arrange
        Jwt jwt = new Jwt(
                "token",
                null,
                null,
                Map.of("alg", "none"),
                Map.of(
                        "oid", "entra-123",
                        "name", "Test User"
                )
        );

        JwtAuthenticationToken authentication =
                new JwtAuthenticationToken(jwt);

        SecurityContextHolder.getContext()
                .setAuthentication(authentication);

        // Act
        String name =
                currentUserService.getName();

        // Assert
        assertEquals("Test User", name);
    }

    @Test
    void shouldThrowWhenNameClaimMissing() {

        // Arrange
        Jwt jwt = new Jwt(
                "token",
                null,
                null,
                Map.of("alg", "none"),
                Map.of(
                        "oid", "entra-123"
                )
        );

        JwtAuthenticationToken authentication =
                new JwtAuthenticationToken(jwt);

        SecurityContextHolder.getContext()
                .setAuthentication(authentication);

        // Act + Assert
        assertThrows(
                IllegalStateException.class,
                () -> currentUserService.getName()
        );
    }

    @Test
    void shouldThrowWhenAuthenticationIsNotJwt() {

        // Arrange
        SecurityContextHolder.getContext()
                .setAuthentication(null);

        // Act + Assert
        assertThrows(
                IllegalStateException.class,
                () -> currentUserService.getName()
        );
    }
}