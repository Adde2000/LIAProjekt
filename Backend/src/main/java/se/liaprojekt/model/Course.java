package se.liaprojekt.model;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
@Builder
@RequiredArgsConstructor
@AllArgsConstructor
@Table(name = "courses")
public class Course {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "assistant_id")
    private String assistantId;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false)
    private String createdBy;

    @Column(nullable = false)
    private Integer aiSessionTtlWeeks = 6;

    @OrderBy("orderIndex ASC")
    @OneToMany(fetch = FetchType.EAGER, mappedBy = "course", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Section> sections = new ArrayList<>();

    @OneToMany(fetch = FetchType.EAGER, mappedBy = "course", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<UserProgress> userProgress = new ArrayList<>();

    @ManyToMany
    @JoinTable(
            name = "course_ai",
            joinColumns = @JoinColumn(name = "course_id"),
            inverseJoinColumns = @JoinColumn(name = "ai_character_id")
    )
    private List<AiCharacter> aiCharacters;
}