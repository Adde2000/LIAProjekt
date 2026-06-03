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
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import se.liaprojekt.dto.SubmitAnswerRequest;
import se.liaprojekt.model.User;
import se.liaprojekt.repository.UserRepository;
import se.liaprojekt.service.CurrentUserService;
import se.liaprojekt.service.ai.VectorStoreService;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
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
    @MockBean private VectorStoreService vectorStoreService; // Mockas så att Azure/OpenAI-anrop inte sker på riktigt

    private static final String TEST_USER = "test-user-1";

    @BeforeEach
    void setup() {
        // Mocka säkerhetskontexten
        when(currentUserService.getEntraId()).thenReturn(TEST_USER);
        when(currentUserService.getName()).thenReturn("Test User");

        // Mocka Vector Store så att den returnerar ett fejk-id när createCourse körs
        when(vectorStoreService.createVectorStore(anyString())).thenReturn("mock-vs-id");

        // Skapa testanvändaren i databasen om den inte finns
        userRepository.findByEntraId(TEST_USER)
                .orElseGet(() -> {
                    User u = new User();
                    u.setEntraId(TEST_USER);
                    return userRepository.saveAndFlush(u);
                });
    }

    @Test
    @WithMockUser(roles = {"Participant", "Admin"})
    void fullFlow_shouldPassTest_andUnlockNextSection() throws Exception {
        // 1. Skapa kurs och hämta dess ID
        Long courseId = createCourse();

        // 2. Skapa sektioner kopplade till kursen
        Long section1 = createSection(courseId, "Intro");
        Long section2 = createSection(courseId, "Advanced");

        // 3. Skapa frågor för båda sektionerna
        createQuestion(section1);
        createQuestion(section2);

        // 4. Hämta fråga för sektion 1 och skicka in rätt svar (index 0)
        QuestionData q = getFirstQuestion(section1);
        List<SubmitAnswerRequest> answerRequestList = List.of(new SubmitAnswerRequest(q.questionId, q.correctAnswerId));
        submitTest(section1, answerRequestList);

        // 5. Verifiera att testet blev godkänt och registrerat som COMPLETED
        verifyCompletedAttempt(section1);

        // 6. Hämta och svara på sektion 2 (Kommer lyckas om din upplåsningslogik tillåter det!)
        q = getFirstQuestion(section2);
        answerRequestList = List.of(new SubmitAnswerRequest(q.questionId, q.correctAnswerId));
        submitTest(section2, answerRequestList);
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

    private void submitTest(Long sectionId, List<SubmitAnswerRequest> answerRequestList) throws Exception {
        mockMvc.perform(post("/api/courses/sections/tests/" + sectionId + "/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(answerRequestList)))
                .andExpect(status().isOk());
    }

    private void verifyCompletedAttempt(Long sectionId) throws Exception {
        mockMvc.perform(get("/api/courses/sections/tests/" + sectionId + "/attempts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("COMPLETED"));
    }

    // =========================
    // QUESTION PARSING
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

        // TestController döljer fältet "correct" i JSON utåt, så vi utgår från ordningen
        // de skapades i: index 0 (Language = rätt), index 1 (Car = fel).
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