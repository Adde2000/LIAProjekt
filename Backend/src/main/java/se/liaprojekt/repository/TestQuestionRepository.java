package se.liaprojekt.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import se.liaprojekt.model.TestQuestion;

import java.util.List;
import java.util.Optional;

public interface TestQuestionRepository extends JpaRepository<TestQuestion, Long> {
    List<TestQuestion> findBySectionId(Long sectionId);
    void deleteBySectionId(Long sectionId);

    @Query("SELECT q FROM TestQuestion q LEFT JOIN FETCH q.answers WHERE q.id = :id")
    Optional<TestQuestion> findByIdWithAnswers(Long id);
}
