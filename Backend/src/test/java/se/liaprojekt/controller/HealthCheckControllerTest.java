package se.liaprojekt.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.client.RestClientTest;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class HealthCheckControllerTest {

    @Autowired
    private HealthCheckController healthCheckController;

    @Test
    void healthCheck() {
        ResponseEntity<Map<String, Object>> response = healthCheckController.healthCheck();
        assertNotNull(response);
        assertTrue(response.getStatusCode().is2xxSuccessful());
    }
}