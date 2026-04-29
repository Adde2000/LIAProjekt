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
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String entraId;

    private LocalDateTime createdAt = LocalDateTime.now();

    @ManyToMany
    private List<Course> courses;

    @OneToMany(mappedBy = "user")
    private List<UserProgress> progress;

    @OneToMany(mappedBy = "user")
    private List<TestResult> testResults;

    @OneToMany(mappedBy = "user")
    private List<AiSession> aiSessions;
}
