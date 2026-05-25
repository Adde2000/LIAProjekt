package se.liaprojekt.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "sections")
public class Section {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private int orderIndex;

    @ManyToOne
    @JoinColumn(name = "course_id")
    private Course course;

//    @OneToMany(mappedBy = "section", cascade = CascadeType.ALL)
//    private List<Material> materials;

    @OneToMany(mappedBy = "section", cascade = CascadeType.ALL)
    private List<TestQuestion> testQuestions = new ArrayList<>();
}
