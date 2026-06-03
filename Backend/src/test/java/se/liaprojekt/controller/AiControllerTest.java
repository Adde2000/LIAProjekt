package se.liaprojekt.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import se.liaprojekt.dto.*;
import se.liaprojekt.model.AiSession;
import se.liaprojekt.repository.UserRepository;
import se.liaprojekt.service.CurrentUserService;
import se.liaprojekt.service.ai.AiCharacterService;
import se.liaprojekt.service.ai.AiChatService;
import se.liaprojekt.service.ai.AiSessionInitService;
import se.liaprojekt.service.ai.AssistantAdminService;

import java.util.List;

import static org.mockito.Mockito.when;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AiController.class)
@AutoConfigureMockMvc(addFilters = false)
class AiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AiChatService aiChatService;

    @MockBean
    private AiSessionInitService initService;

    @MockBean
    private AiCharacterService aiCharacterService;

    @MockBean
    private AssistantAdminService assistantAdminService;

    @MockBean
    private CurrentUserService currentUserService;

    @Test
    void shouldChatSuccessfully() throws Exception {

        ChatRequest request = new ChatRequest();
        request.setSessionId(1L);
        request.setMessage("Hej AI");

        when(aiChatService.chat(1L, "Hej AI"))
                .thenReturn("Hej användare");

        mockMvc.perform(post("/api/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response")
                        .value("Hej användare"));
    }

    @Test
    void shouldCreateSession() throws Exception {
        when(currentUserService.getEntraId()).thenReturn("1");

        AiSession session = new AiSession();
        session.setId(99L);

        when(initService.createSession("1", 2L))
                .thenReturn(session);

        mockMvc.perform(post("/api/ai/session")
//                        .param("userId", "1")
                        .param("courseId", "2"))
                .andExpect(status().isOk())
                .andExpect(content().string("99"));
    }

    @Test
    void shouldGetCharacters() throws Exception {

        AiCharacterResponse character =
                new AiCharacterResponse(
                        1L,
                        "Java Mentor",
                        "Expert på Java"
                );

        when(aiCharacterService.getByCourse(1L))
                .thenReturn(List.of(character));

        mockMvc.perform(get("/api/ai/characters/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id")
                        .value(1))
                .andExpect(jsonPath("$[0].name")
                        .value("Java Mentor"))
                .andExpect(jsonPath("$[0].description")
                        .value("Expert på Java"));
    }

    @Test
    void shouldGetAssistants() throws Exception {

        AssistantAdminResponse assistant =
                new AssistantAdminResponse(
                        "assistant-1",
                        "Math Assistant",
                        "Helpful math tutor",
                        "gpt-4"
                );

        when(assistantAdminService.getAllAssistants())
                .thenReturn(List.of(assistant));

        mockMvc.perform(get("/api/ai/assistants"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id")
                        .value("assistant-1"))
                .andExpect(jsonPath("$[0].name")
                        .value("Math Assistant"))
                .andExpect(jsonPath("$[0].model")
                        .value("gpt-4"));
    }

    @Test
    void shouldGetChatHistory() throws Exception {

        ChatHistoryMessage historyMessage =
                new ChatHistoryMessage(
                        "user",
                        "Hej där"
                );

        when(aiChatService.getHistory(1L))
                .thenReturn(List.of(historyMessage));

        mockMvc.perform(get("/api/ai/history/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].role")
                        .value("user"))
                .andExpect(jsonPath("$[0].content")
                        .value("Hej där"));
    }
}