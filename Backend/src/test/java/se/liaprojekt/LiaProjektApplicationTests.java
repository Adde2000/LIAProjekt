package se.liaprojekt;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import se.liaprojekt.service.GraphService;

@SpringBootTest
class LiaProjektApplicationTests {

    @MockBean
    private GraphService graphService;

    @Test
    void contextLoads() {
    }

}
