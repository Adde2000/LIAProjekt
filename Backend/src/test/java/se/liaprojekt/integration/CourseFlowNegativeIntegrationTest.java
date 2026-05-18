package se.liaprojekt.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import se.liaprojekt.model.User;
import se.liaprojekt.repository.UserRepository;
import se.liaprojekt.service.CurrentUserService;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@Transactional
class CourseFlowNegativeIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;

    @MockBean private CurrentUserService currentUserService;

    private static final String TEST_USER = "negative-user";

    private Long courseId;
    private Long sectionId;

    @BeforeEach
    void setup() throws Exception {

        when(currentUserService.getEntraId()).thenReturn(TEST_USER);

        userRepository.findByEntraId(TEST_USER)
                .orElseGet(() -> {
                    User u = new User();
                    u.setEntraId(TEST_USER);
                    return userRepository.saveAndFlush(u);
                });

        courseId = createCourse();
        sectionId = createSection(courseId);
    }

    // ---------------------------
    // NEGATIVE TESTS
    // ---------------------------

    // Testar att API returnerar 404 när man försöker hämta en kurs som inte finns
    @Test
    void shouldReturn404_whenCourseDoesNotExist() throws Exception {

        mockMvc.perform(get("/api/courses/999999"))
                .andExpect(status().isNotFound());
    }

    // Testar att API returnerar 404 när man försöker starta ett test på en sektion som inte finns
    @Test
    void shouldReturn404_whenSectionDoesNotExist() throws Exception {

        mockMvc.perform(post("/api/courses/sections/tests/999999/start"))
                .andExpect(status().isNotFound());
    }

    // Testar att API returnerar 404 när man försöker skapa fråga i en ogiltig sektion
    @Test
    void shouldReturn404_whenCreatingQuestionForInvalidSection() throws Exception {

        mockMvc.perform(post("/api/courses/sections/tests/999999/questions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {
                          "questionText": "What is Java?",
                          "answers": [
                            {"answerText": "A", "correct": true},
                            {"answerText": "B", "correct": false}
                          ]
                        }
                        """))
                .andExpect(status().isNotFound());
    }

    // Testar att API returnerar 400 när en fråga saknar korrekt svar
    @Test
    void shouldReturn400_whenQuestionHasNoCorrectAnswer() throws Exception {

        mockMvc.perform(post("/api/courses/sections/tests/" + sectionId + "/questions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {
                          "questionText": "Invalid question",
                          "answers": [
                            {"answerText": "A", "correct": false},
                            {"answerText": "B", "correct": false}
                          ]
                        }
                        """))
                .andExpect(status().isBadRequest());
    }

    // Testar att API returnerar 404 när man försöker starta test på ogiltig sektion
    @Test
    void shouldReturn404_whenStartingTestForInvalidSection() throws Exception {

        mockMvc.perform(post("/api/courses/sections/tests/999999/start"))
                .andExpect(status().isNotFound());
    }

    // Testar att API returnerar 404 när man försöker ta bort en kurs som inte finns
    @Test
    void shouldReturn404_whenDeletingNonExistingCourse() throws Exception {

        mockMvc.perform(delete("/api/courses/999999"))
                .andExpect(status().isNotFound());
    }

    // Testar att API returnerar 404 när man skickar svar till ett test som inte finns
    @Test
    void shouldReturn404_whenSubmittingAnswerForInvalidTest() throws Exception {

        mockMvc.perform(post("/api/courses/sections/tests/answer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {
                          "testResultId": 999999,
                          "questionId": 999999,
                          "answerId": 999999
                        }
                        """))
                .andExpect(status().isNotFound());
    }

    // Testar att API returnerar 404 när användaren försöker submit:a ett test i felaktigt flöde
    @Test
    void shouldReturn404_whenUserTriesInvalidFlow() throws Exception {

        mockMvc.perform(post("/api/courses/sections/tests/" + sectionId + "/submit"))
                .andExpect(status().isNotFound());
    }

    // ---------------------------
    // HELPERS (ROBUST VERSION)
    // ---------------------------

    private Long createCourse() throws Exception {

        String response = mockMvc.perform(post("/api/courses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {
                          "title": "Test Course",
                          "description": "Negative test setup",
                          "createdBy": "system"
                        }
                        """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode node = objectMapper.readTree(response);

        if (!node.has("id")) {
            throw new IllegalStateException("Course creation did not return id");
        }

        return node.get("id").asLong();
    }

    private Long createSection(Long courseId) throws Exception {

        String response = mockMvc.perform(post("/api/courses/" + courseId + "/sections")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {
                          "title": "Test Section"
                        }
                        """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode node = objectMapper.readTree(response);

        if (!node.has("id")) {
            throw new IllegalStateException("Section creation did not return id");
        }

        return node.get("id").asLong();
    }
}