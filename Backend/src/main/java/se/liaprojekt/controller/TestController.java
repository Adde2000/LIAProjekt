package se.liaprojekt.controller;

import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import se.liaprojekt.dto.*;
import se.liaprojekt.service.CurrentUserService;
import se.liaprojekt.service.TestService;

import java.util.List;

@RestController
@RequestMapping("/api/courses/sections/tests")
@RequiredArgsConstructor
public class TestController {

    private final TestService testService;
    private final CurrentUserService currentUserService;

    // CREATE QUESTION (ADMIN)
    @Operation(summary = "Create a question")
    @PostMapping("/{sectionId}/questions")
    public ResponseEntity<Void> createQuestion(
            @PathVariable Long sectionId,
            @RequestBody TestQuestionRequest request
    ) {
        testService.createQuestion(sectionId, request);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Update question")
    @PutMapping("/{sectionId}/questions/{questionId}")
    public ResponseEntity<TestQuestionResponse> updateQuestion(
            @PathVariable Long sectionId,
            @PathVariable Long questionId,
            @RequestBody TestQuestionRequest request
    ) {
        return ResponseEntity.ok(
                testService.updateQuestion(sectionId, questionId, request)
        );
    }

    @Operation(summary = "Delete question")
    @DeleteMapping("/{sectionId}/questions/{questionId}")
    public ResponseEntity<Void> deleteQuestion(
            @PathVariable Long sectionId,
            @PathVariable Long questionId
    ) {
        testService.deleteQuestion(sectionId, questionId);
        return ResponseEntity.noContent().build();
    }

    // START TEST
    @Operation(summary = "Start test to answer questions")
    @PostMapping("/{sectionId}/start")
    public ResponseEntity<TestResultResponse> startTest(@PathVariable Long sectionId) {

        String entraId = currentUserService.getEntraId();

        return ResponseEntity.ok(
                testService.startTest(entraId, sectionId)
        );
    }

    // SUBMIT ANSWER
    @Operation(summary = "Submit answer for questions")
    @PostMapping("/answer")
    public ResponseEntity<Void> submitAnswer(@RequestBody SubmitAnswerRequest request) {

        testService.submitAnswer(
                request.testResultId(),
                request.questionId(),
                request.answerId()
        );

        return ResponseEntity.ok().build();
    }

    // SUBMIT TEST
    @Operation(summary = "Submit test")
    @PostMapping("/{testResultId}/submit")
    public ResponseEntity<TestResultResponse> submitTest(
            @PathVariable Long testResultId) {

        return ResponseEntity.ok(
                testService.submitTest(testResultId)
        );
    }

    // GET TEST QUESTIONS
    @Operation(summary = "Get all questions in section test")
    @GetMapping("/{sectionId}/questions")
    public ResponseEntity<List<TestQuestionResponse>> getQuestions(
            @PathVariable Long sectionId) {

        return ResponseEntity.ok(
                testService.getQuestions(sectionId)
        );
    }

    @Operation(summary = "Get your test attempts")
    @GetMapping("/{sectionId}/attempts")
    public ResponseEntity<List<TestResultResponse>> getAttempts(
            @PathVariable Long sectionId
    ) {
        String entraId = currentUserService.getEntraId();

        return ResponseEntity.ok(
                testService.getAttempts(entraId, sectionId)
        );
    }
}