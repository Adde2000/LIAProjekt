package se.liaprojekt.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "test_results")
public class TestResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    private Status status;

    @Column
    private Integer score;
    private Boolean passed;

    private LocalDateTime startedAt;
    private LocalDateTime completedAt;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne
    @JoinColumn(name = "section_id", nullable = false)
    private Section section;

    @OneToMany(mappedBy = "testResult",cascade = CascadeType.ALL)
    private List<AnsweredQuestion> answeredQuestions;

    @PrePersist
    public void onCreate() {
        this.startedAt = LocalDateTime.now();
    }

    //Do not change names
    public enum Status {
        NOT_STARTED,
        IN_PROGRESS,
        COMPLETED,
        FAILED
    }
}
