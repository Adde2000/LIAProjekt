package se.liaprojekt.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Representerar en AI-session kopplad till Azure Assistants Thread.
 * Detta ersätter all manuell chat history.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "ai_sessions")
public class AiSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Azure OpenAI Thread ID (detta är "minnet")
     */
    @Column(unique = true)
    private String threadId;

    private String assistantId;

    /**
     * Kopplad användare
     */
    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * Kursen som styr vilket material AI ska använda
     */
    @ManyToOne
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    /**
     * Vilken AI-karaktär (persona + beteende)
     */
    @ManyToOne
    @JoinColumn(name = "ai_character_id", nullable = false)
    private AiCharacter aiCharacter;
}