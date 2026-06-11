package se.liaprojekt.controller;

import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import se.liaprojekt.controller.util.Roles;
import se.liaprojekt.dto.*;
import se.liaprojekt.model.TestQuestion;
import se.liaprojekt.service.CurrentUserService;
import se.liaprojekt.service.TestService;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/courses/sections/tests")
@RequiredArgsConstructor
public class TestController {
    private static final Logger log = LoggerFactory.getLogger(TestController.class);

    private final TestService testService;
    private final CurrentUserService currentUserService;

    // CREATE QUESTION (Admin/CourseAdmin)
    @Operation(summary = "Create a question")
    @PostMapping("/{sectionId}/questions")
    @PreAuthorize(Roles.ANY_ROLE_ADMIN_COURSE_ADMIN)
    public ResponseEntity<TestQuestionResponse> createQuestion(
            @PathVariable Long sectionId,
            @RequestBody TestQuestionRequest request
    ) {
        log.info("Creating a question for section id {}", sectionId);
        TestQuestion testQuestion = testService.createQuestion(sectionId, request);
        List<TestAnswerResponse> testAnswerResponseList = new ArrayList<>();
        testQuestion.getAnswers().forEach(testAnswer -> {
             testAnswerResponseList.add( new TestAnswerResponse(
                    testAnswer.getId(),
                    testAnswer.getAnswerText()
            ));
        });
        TestQuestionResponse testQuestionResponse = new TestQuestionResponse(
                testQuestion.getId(),
                testQuestion.getQuestionText(),
                testAnswerResponseList
                );
        return ResponseEntity.ok(testQuestionResponse);
    }

//    (Admin/CourseAdmin)
    @Operation(summary = "Update question")
    @PutMapping("/{sectionId}/questions/{questionId}")
    @PreAuthorize(Roles.ANY_ROLE_ADMIN_COURSE_ADMIN)
    public ResponseEntity<TestQuestionResponse> updateQuestion(
            @PathVariable Long sectionId,
            @PathVariable Long questionId,
            @RequestBody TestQuestionRequest request
    ) {
        log.info("Updating question for section id {}, and question id {}", sectionId, questionId);
        return ResponseEntity.ok(
                testService.updateQuestion(sectionId, questionId, request)
        );
    }

//    (Admin/CourseAdmin)
    @Operation(summary = "Delete question")
    @DeleteMapping("/{sectionId}/questions/{questionId}")
    @PreAuthorize(Roles.ANY_ROLE_ADMIN_COURSE_ADMIN)
    public ResponseEntity<Void> deleteQuestion(
            @PathVariable Long sectionId,
            @PathVariable Long questionId
    ) {
        log.info("Deleting question for section id {}, and question id {}", sectionId, questionId);
        testService.deleteQuestion(sectionId, questionId);
        return ResponseEntity.noContent().build();
    }

    // SUBMIT TEST (Participant)
    @Operation(summary = "Submit test")
    @PostMapping("/{sectionId}/submit")
    @PreAuthorize(Roles.ROLE_PARTICIPANT)
    public ResponseEntity<TestResultResponse> submitTest(
            @PathVariable Long sectionId,
            @RequestBody List<SubmitAnswerRequest> requestList) {
        log.info("Submitting test for section id {}", sectionId);
        String entraId = currentUserService.getEntraId();
        Long testResultId = testService.startTest(entraId, sectionId).id();

        for (SubmitAnswerRequest request : requestList) {
            testService.submitAnswer(
                    testResultId,
                    request.questionId(),
                    request.answerId()
            );
        }

        TestResultResponse result = testService.submitTest(testResultId);
        log.info("Test submitted for sectionId={} testResultId={} passed={}", sectionId, testResultId, result.passed());
        return ResponseEntity.ok(result);
    }

    // GET TEST QUESTIONS
    @Operation(summary = "Get all questions in section test")
    @GetMapping("/{sectionId}/questions")
    public ResponseEntity<List<TestQuestionResponse>> getQuestions(
            @PathVariable Long sectionId) {
        log.info("Getting questions for section id {}", sectionId);
        List<TestQuestionResponse> questions = testService.getQuestions(sectionId);
        log.debug("Returning {} question(s) for sectionId={}", questions.size(), sectionId);
        return ResponseEntity.ok(questions);
    }

    // (Participant)
    @Operation(summary = "Get your test attempts")
    @GetMapping("/{sectionId}/attempts")
    @PreAuthorize(Roles.ROLE_PARTICIPANT)
    public ResponseEntity<List<TestResultResponse>> getAttempts(
            @PathVariable Long sectionId
    ) {
        log.info("Getting attempts for section id {}", sectionId);
        String entraId = currentUserService.getEntraId();

        return ResponseEntity.ok(
                testService.getAttempts(entraId, sectionId)
        );
    }
}