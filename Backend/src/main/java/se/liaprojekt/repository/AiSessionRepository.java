package se.liaprojekt.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;
import se.liaprojekt.model.AiSession;

import java.util.List;

public interface AiSessionRepository extends JpaRepository<AiSession, Long> {

    List<AiSession> findAllByUser_IdAndCourse_Id(
            Long userId,
            Long courseId
    );

    List<AiSession> findAll();

    @Transactional
    @Modifying
    void deleteByCourseId(Long courseId);
}
