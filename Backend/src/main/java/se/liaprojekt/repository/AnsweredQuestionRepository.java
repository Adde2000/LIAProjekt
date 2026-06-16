package se.liaprojekt.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import se.liaprojekt.model.AnsweredQuestion;

import java.util.List;
import java.util.Optional;

public interface AnsweredQuestionRepository extends JpaRepository<AnsweredQuestion, Long> {

    List<AnsweredQuestion> findByTestResult_Id(Long testResultId);

    Optional<AnsweredQuestion> findByTestResult_IdAndQuestion_Id(
            Long testResultId,
            Long questionId
    );

    void deleteByTestResultId(Long testResultId);
    void deleteByQuestionId(Long answeredQuestionId);
}
