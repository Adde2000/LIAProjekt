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

    private String status;
    private Integer score;
    private Boolean passed;

    private LocalDateTime startedAt;
    private LocalDateTime completedAt;

    @ManyToOne
    private User user;

    @OneToMany(cascade = CascadeType.ALL)
    private List<AnsweredQuestion> answeredQuestions;
}
