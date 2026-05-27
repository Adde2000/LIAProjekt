package se.liaprojekt.dto;

public record SubmitAnswerRequest(
        Long questionId,
        Long answerId
) {}
