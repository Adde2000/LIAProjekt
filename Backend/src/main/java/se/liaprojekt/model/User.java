package se.liaprojekt.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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

    @OneToMany(mappedBy = "user")
    private List<TestResult> testResults;

    @OneToMany(mappedBy = "user")
    private List<AiSession> aiSessions;

    @OneToMany(mappedBy = "user")
    private List<UserProgress> userProgressList;
}
