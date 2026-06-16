package se.liaprojekt.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import se.liaprojekt.model.TestAnswer;

public interface TestAnswerRepository extends JpaRepository<TestAnswer, Long> {

}
