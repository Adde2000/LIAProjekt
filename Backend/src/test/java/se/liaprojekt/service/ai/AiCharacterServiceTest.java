package se.liaprojekt.service.ai;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import se.liaprojekt.dto.AiCharacterResponse;
import se.liaprojekt.model.AiCharacter;
import se.liaprojekt.repository.AiCharacterRepository;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiCharacterServiceTest {

    @Mock
    private AiCharacterRepository repository;

    @InjectMocks
    private AiCharacterService service;

    private AiCharacter character;

    @BeforeEach
    void setUp() {

        character = new AiCharacter();
        character.setId(1L);
        character.setName("Java Mentor");
        character.setDescription("Expert på Java");
    }

    @Test
    void shouldReturnCharactersForCourse() {

        // Arrange
        when(repository.findByCourses_Id(1L))
                .thenReturn(List.of(character));

        // Act
        List<AiCharacterResponse> result =
                service.getByCourse(1L);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());

        AiCharacterResponse response = result.get(0);

        assertEquals(1L, response.getId());
        assertEquals("Java Mentor", response.getName());
        assertEquals("Expert på Java", response.getDescription());
    }

    @Test
    void shouldReturnEmptyListWhenNoCharactersExist() {

        // Arrange
        when(repository.findByCourses_Id(99L))
                .thenReturn(List.of());

        // Act
        List<AiCharacterResponse> result =
                service.getByCourse(99L);

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }
}