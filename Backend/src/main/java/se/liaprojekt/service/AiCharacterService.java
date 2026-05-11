package se.liaprojekt.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import se.liaprojekt.dto.AiCharacterResponse;
import se.liaprojekt.model.AiCharacter;
import se.liaprojekt.repository.AiCharacterRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AiCharacterService {

    private final AiCharacterRepository repository;

    public List<AiCharacterResponse> getByCourse(Long courseId) {

        return repository.findByCourses_Id(courseId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private AiCharacterResponse toResponse(AiCharacter c) {
        return new AiCharacterResponse(
                c.getId(),
                c.getName(),
                c.getDescription()
        );
    }
}