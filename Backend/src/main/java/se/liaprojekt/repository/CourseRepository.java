package se.liaprojekt.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import se.liaprojekt.model.Course;

public interface CourseRepository extends JpaRepository<Course, Long> {
}
