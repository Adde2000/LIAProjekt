package se.liaprojekt.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import se.liaprojekt.model.UserProgress;

import java.util.List;

public interface UserProgressRepository extends JpaRepository<UserProgress, Long> {
    List<UserProgress> findByUser_Id(Long userId);
    List<UserProgress> findByCourseId(Long courseId);
    UserProgress findByCourseIdAndUserId(Long courseId, Long userId);

    @Modifying
    @Transactional
    @Query("""
    delete from UserProgress up
    where up.user.id = :userId
""")
    int deleteForUser(@Param("userId") Long userId);
}
