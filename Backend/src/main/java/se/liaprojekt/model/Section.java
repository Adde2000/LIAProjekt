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
@Table(name = "sections")
public class Section {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;
    private int orderIndex;

    @ManyToOne
    private Course course;

    @OneToMany(cascade = CascadeType.ALL)
    private List<Material> material;

    @OneToMany(cascade = CascadeType.ALL)
    private List<TestQuestion> test;
}
