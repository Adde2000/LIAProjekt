package se.liaprojekt.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record TestQuestionRequest(

        @Schema(example = "What is Java?")
        String questionText,

        @Schema(
                description = "List of possible answers (exactly one must be correct)",
                example = """
                [
                  { "answerText": "A programming language", "correct": true },
                  { "answerText": "A database", "correct": false },
                  { "answerText": "An OS", "correct": false }
                ]
                """
        )
        List<TestAnswerRequest> answers
) {}
