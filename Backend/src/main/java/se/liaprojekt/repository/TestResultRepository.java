package se.liaprojekt.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import se.liaprojekt.model.TestResult;

import java.util.List;
import java.util.Optional;

public interface TestResultRepository extends JpaRepository<TestResult, Long> {

    void deleteByUserId(long userId);

    // =========================
    // ALL USER RESULTS (ALL SECTIONS)
    // =========================
    List<TestResult> findByUser_EntraId(String entraId);

    // =========================
    // GET ATTEMPTS SORTED BY ATTEMPT NUMBER (IMPORTANT)
    // =========================
    List<TestResult> findByUser_EntraIdAndSectionIdOrderByAttemptNumberDesc(
            String entraId,
            Long sectionId
    );

    // =========================
    // GET LATEST ATTEMPT
    // =========================
    Optional<TestResult> findTopByUser_EntraIdAndSectionIdOrderByAttemptNumberDesc(
            String entraId,
            Long sectionId
    );
}
