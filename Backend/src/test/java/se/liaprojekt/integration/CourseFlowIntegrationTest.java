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
class CourseFlowIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private CurrentUserService currentUserService;

    private static final String TEST_USER = "test-user-1";

    @BeforeEach
    void setup() {

        when(currentUserService.getEntraId())
                .thenReturn(TEST_USER);

        when(currentUserService.getName())
                .thenReturn("Test User");

        userRepository.findByEntraId(TEST_USER)
                .orElseGet(() -> {
                    User u = new User();
                    u.setEntraId(TEST_USER);
                    return userRepository.saveAndFlush(u);
                });
    }

    @Test
    void fullFlow_shouldPassTest_andUnlockNextSection() throws Exception {

        Long courseId = createCourse();
        Long section1 = createSection(courseId, "Intro");
        Long section2 = createSection(courseId, "Advanced");

        createQuestion(section1);

        Long testId = startTest(section1);

        QuestionData q = getFirstQuestion(section1);

        submitAnswer(testId, q.questionId(), q.correctAnswerId());

        submitTest(testId);

        verifyCompletedAttempt(section1);

        startTest(section2); // should succeed if unlock logic works
    }

    // =========================
    // HELPERS
    // =========================

    private Long createCourse() throws Exception {

        String response = mockMvc.perform(post("/api/courses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {
                          "title": "Java",
                          "description": "Backend",
                          "createdBy": "admin"
                        }
                        """))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return objectMapper.readTree(response).get("id").asLong();
    }

    private Long createSection(Long courseId, String title) throws Exception {

        String response = mockMvc.perform(
                        post("/api/courses/" + courseId + "/sections")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                { "title": "%s" }
                                """.formatted(title)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return objectMapper.readTree(response).get("id").asLong();
    }

    private void createQuestion(Long sectionId) throws Exception {

        mockMvc.perform(post("/api/courses/sections/tests/" + sectionId + "/questions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {
                          "questionText": "What is Java?",
                          "answers": [
                            {"answerText": "Language", "correct": true},
                            {"answerText": "Car", "correct": false}
                          ]
                        }
                        """))
                .andExpect(status().isOk());
    }

    private Long startTest(Long sectionId) throws Exception {

        String response = mockMvc.perform(
                        post("/api/courses/sections/tests/" + sectionId + "/start"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return objectMapper.readTree(response).get("id").asLong();
    }

    private void submitAnswer(Long testId, Long questionId, Long answerId) throws Exception {

        if (answerId == null) {
            throw new IllegalStateException("answerId is null");
        }

        String body = """
        {
          "testResultId": %d,
          "questionId": %d,
          "answerId": %d
        }
        """.formatted(testId, questionId, answerId);

        mockMvc.perform(post("/api/courses/sections/tests/answer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }

    private void submitTest(Long testId) throws Exception {

        mockMvc.perform(post("/api/courses/sections/tests/" + testId + "/submit"))
                .andExpect(status().isOk());
    }

    private void verifyCompletedAttempt(Long sectionId) throws Exception {

        mockMvc.perform(get("/api/courses/sections/tests/" + sectionId + "/attempts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("COMPLETED"));
    }

    // =========================
    // QUESTION PARSING (CLEAN + DETERMINISTIC)
    // =========================

    private QuestionData getFirstQuestion(Long sectionId) throws Exception {

        String response = mockMvc.perform(
                        get("/api/courses/sections/tests/" + sectionId + "/questions"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode root = objectMapper.readTree(response);
        JsonNode first = root.get(0);

        Long questionId = first.get("id").asLong();
        JsonNode answers = first.get("answers");

        if (answers.size() < 2) {
            throw new IllegalStateException("Expected at least 2 answers");
        }

        // 💡 TEST OWNERSHIP: position-based, not API logic
        Long correctAnswerId = answers.get(0).get("id").asLong();
        Long wrongAnswerId = answers.get(1).get("id").asLong();

        return new QuestionData(questionId, correctAnswerId, wrongAnswerId);
    }

    // =========================
    // DTO
    // =========================

    private record QuestionData(
            Long questionId,
            Long correctAnswerId,
            Long wrongAnswerId
    ) {}
}