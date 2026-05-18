package se.liaprojekt.model;

import jakarta.persistence.*;
import lombok.*;


@Entity
@Getter
@Setter
@NoArgsConstructor
//@RequiredArgsConstructor
@AllArgsConstructor
@Table(
        name = "user_progress",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"user_id", "course_id"})
        }
)
public class UserProgress {

    public UserProgress(User user, Course course) {
        this.user = user;
        this.course = course;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private int completedSections;

    @Column(nullable = false)
    private int progressPercentage;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;
}
