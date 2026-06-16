package se.liaprojekt.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.ApplicationEventPublisher;
import se.liaprojekt.dto.*;
import se.liaprojekt.event.TestResultEvent;
import se.liaprojekt.exception.BadRequestException;
import se.liaprojekt.exception.ResourceNotFoundException;
import se.liaprojekt.model.*;
import se.liaprojekt.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TestService {

    private static final Logger log =
            LoggerFactory.getLogger(TestService.class);

    private final TestResultRepository testResultRepository;
    private final SectionRepository sectionRepository;
    private final TestQuestionRepository questionRepository;
    private final TestAnswerRepository testAnswerRepository;
    private final AnsweredQuestionRepository answeredQuestionRepository;
    private final UserRepository userRepository;
    private final CurrentUserService currentUserService;
    private final ApplicationEventPublisher eventPublisher;
    private final UserProgressRepository userProgressRepository;


    @Transactional
    public TestQuestion createQuestion(Long sectionId, TestQuestionRequest request) {

        // =========================
        // Hämta section
        // =========================
        Section section = sectionRepository.findById(sectionId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Section not found: " + sectionId));

        // =========================
        // VALIDERING
        // =========================
        long correctCount = request.answers()
                .stream()
                .filter(TestAnswerRequest::isCorrect)
                .count();

        if (correctCount != 1) {
            throw new BadRequestException("A question must have exactly one correct answer");
        }

        // =========================
        // Skapa question
        // =========================
        TestQuestion question = new TestQuestion();
        question.setQuestionText(request.questionText());
        question.setSection(section);

        // =========================
        // Skapa answers (kopplade till question)
        // =========================
        List<TestAnswer> answers = request.answers().stream()
                .map(dto -> {
                    TestAnswer answer = new TestAnswer();
                    answer.setAnswerText(dto.answerText());
                    answer.setIsCorrect(dto.isCorrect());
                    answer.setQuestion(question);
                    return answer;
                })
                .toList();

        question.setAnswers(answers);

        // =========================
        // Spara (cascade sparar answers automatiskt)
        // =========================
        return questionRepository.save(question);
    }

    @Transactional
    public TestQuestionResponse updateQuestion(Long sectionId, Long questionId, TestQuestionRequest request) {

        TestQuestion question = questionRepository.findByIdWithAnswers(questionId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Question not found: " + questionId));

        if (!question.getSection().getId().equals(sectionId)) {
            throw new BadRequestException("Question does not belong to section");
        }

        long correctCount = request.answers()
                .stream()
                .filter(TestAnswerRequest::isCorrect)
                .count();

        if (correctCount != 1) {
            throw new BadRequestException("A question must have exactly one correct answer");
        }

        question.setQuestionText(request.questionText());

        question.getAnswers().clear();

        List<TestAnswer> newAnswers = request.answers().stream()
                .map(dto -> {
                    TestAnswer answer = new TestAnswer();
                    answer.setAnswerText(dto.answerText());
                    answer.setIsCorrect(dto.isCorrect());
                    answer.setQuestion(question);
                    return answer;
                })
                .toList();

        question.getAnswers().addAll(newAnswers);

        TestQuestion saved = questionRepository.save(question);

        return new TestQuestionResponse(
                saved.getId(),
                saved.getQuestionText(),
                saved.getAnswers().stream()
                        .map(a -> new TestAnswerResponse(
                                a.getId(),
                                a.getAnswerText()
                        ))
                        .toList()
        );
    }

    @Transactional
    public void deleteQuestion(Long sectionId, Long questionId) {

        TestQuestion question = questionRepository.findById(questionId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Question not found: " + questionId));

        if (!question.getSection().getId().equals(sectionId)) {
            throw new BadRequestException("Question does not belong to section");
        }

        questionRepository.delete(question);
    }

    @Transactional
    public void deleteSectionQuestions(Long sectionId) {
        List<TestQuestion> questionList = questionRepository.findBySectionId(sectionId);
        for (TestQuestion question : questionList) {
            for (TestAnswer answer : question.getAnswers()) {
                testAnswerRepository.delete(answer);
            }
            testAnswerRepository.flush();
            answeredQuestionRepository.deleteByQuestionId(question.getId());
            answeredQuestionRepository.flush();
        }
        questionRepository.deleteBySectionId(sectionId);
        questionRepository.flush();
    }

    @Transactional
    public void deleteSection(Long sectionId) {
        deleteSectionQuestions(sectionId);
        testResultRepository.deleteBySectionId(sectionId);
    }

    // =========================
    // START TEST (MULTI ATTEMPT)
    // =========================
    @Transactional
    public TestResultResponse startTest(String entraId, Long sectionId) {

        Section section = sectionRepository.findById(sectionId)
                .orElseThrow(() -> new ResourceNotFoundException("Section not found"));

        User user = userRepository.findByEntraId(entraId)
                .orElseGet(() -> {
                    User newUser = new User();
                    newUser.setEntraId(entraId);
                    return userRepository.save(newUser);
                });

        // =========================
        // LOCK CHECK (SECTION PROGRESSION)
        // =========================
        if (isSectionLocked(user, section)) {
            throw new BadRequestException("Section is locked");
        }

        // =========================
        // GET LATEST ATTEMPT
        // =========================
        TestResult lastAttempt = testResultRepository
                .findByUser_EntraIdAndSectionIdOrderByAttemptNumberDesc(entraId, sectionId)
                .stream()
                .findFirst()
                .orElse(null);

        // =========================
        // IF USER ALREADY COMPLETED → BLOCK RETRY
        // =========================
        if (lastAttempt != null
                && lastAttempt.getStatus() == TestResult.Status.COMPLETED) {
            throw new BadRequestException("Test already completed. Retry not allowed.");
        }

        // =========================
        // DETERMINE NEXT ATTEMPT NUMBER
        // =========================
        int nextAttempt = (lastAttempt == null)
                ? 1
                : lastAttempt.getAttemptNumber() + 1;

        // =========================
        // CREATE NEW ATTEMPT
        // =========================
        TestResult result = new TestResult();
        result.setUser(user);
        result.setSection(section);
        result.setStatus(TestResult.Status.IN_PROGRESS);
        result.setPassed(false);
        result.setAttemptNumber(nextAttempt);

        // =========================
        // SAVE ATTEMPT
        // =========================
        result = testResultRepository.save(result);

        return mapToResponse(result);
    }

    // =========================
    // SUBMIT ANSWER (MULTI ATTEMPT SAFE)
    // =========================
    @Transactional
    public void submitAnswer(Long testResultId, Long questionId, Long answerId) {

        String entraId = currentUserService.getEntraId();

        // =========================
        // FETCH CURRENT ATTEMPT
        // =========================
        TestResult result = testResultRepository.findById(testResultId)
                .orElseThrow(() -> new ResourceNotFoundException("Test not found"));

        // =========================
        // SECURITY CHECK (OWNER CHECK)
        // =========================
        if (!result.getUser().getEntraId().equals(entraId)) {
            throw new BadRequestException("Not allowed");
        }

        // =========================
        // SAFETY CHECK: ONLY ACTIVE ATTEMPTS CAN ACCEPT ANSWERS
        // =========================
        if (result.getStatus() != TestResult.Status.IN_PROGRESS) {
            throw new BadRequestException("Cannot submit answers to a finished test attempt");
        }

        // =========================
        // FETCH QUESTION
        // =========================
        TestQuestion question = questionRepository.findByIdWithAnswers(questionId)
                .orElseThrow(() -> new ResourceNotFoundException("Question not found"));

        // =========================
        // PREVENT DUPLICATE ANSWERS PER ATTEMPT
        // =========================
        if (answeredQuestionRepository
                .findByTestResult_IdAndQuestion_Id(testResultId, questionId)
                .isPresent()) {
            return;
        }

        // =========================
        // FIND SELECTED ANSWER
        // =========================
        TestAnswer selectedAnswer = question.getAnswers()
                .stream()
                .filter(a -> a.getId().equals(answerId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Answer not found"));

        // =========================
        // CREATE ANSWER ENTITY
        // =========================
        AnsweredQuestion answered = new AnsweredQuestion();
        answered.setTestResult(result);
        answered.setQuestion(question);
        answered.setCorrect(selectedAnswer.getIsCorrect());

        // =========================
        // SAVE ANSWER
        // =========================
        answeredQuestionRepository.save(answered);
    }

    // =========================
    // SUBMIT TEST (MULTI-ATTEMPT)
    // =========================
    @Transactional
    public TestResultResponse submitTest(Long testResultId) {

        String entraId = currentUserService.getEntraId();

        // =========================
        // HÄMTA TEST ATTEMPT
        // =========================
        TestResult result = testResultRepository.findById(testResultId)
                .orElseThrow(() -> new ResourceNotFoundException("Test not found"));

        // =========================
        // SECURITY CHECK (OWNER CHECK)
        // =========================
        if (!result.getUser().getEntraId().equals(entraId)) {
            throw new BadRequestException("Not allowed");
        }

        // =========================
        // PREVENT DOUBLE SUBMIT
        // =========================
        if (result.getStatus() == TestResult.Status.COMPLETED) {
            throw new BadRequestException("This attempt is already completed and cannot be resubmitted");
        }

        // =========================
        // PREVENT SUBMIT ON FAILED (OPTIONAL SAFETY)
        // =========================
        if (result.getStatus() == TestResult.Status.FAILED) {
            throw new BadRequestException("This attempt is already finished. Start a new attempt.");
        }

        // =========================
        // HÄMTA ALLA SVAR FÖR DENNA ATTEMPT
        // =========================
        List<AnsweredQuestion> answers =
                answeredQuestionRepository.findByTestResult_Id(testResultId);

        // =========================
        // RÄKNA RÄTT SVAR
        // =========================
        long correct = answers.stream()
                .filter(AnsweredQuestion::isCorrect)
                .count();

        int total = answers.size();

        // =========================
        // BERÄKNA SCORE
        // =========================
        int score = total == 0 ? 0 : (int) ((correct * 100.0) / total);

        result.setScore(score);
        result.setCompletedAt(LocalDateTime.now());

        // =========================
        // JUST NU 100% FÖR GODKÄNT
        // =========================
        boolean passed = score >= 100;
        result.setPassed(passed);

        // =========================
        // SÄTT FINAL STATUS
        // =========================
        result.setStatus(
                passed ? TestResult.Status.COMPLETED : TestResult.Status.FAILED
        );

        TestResult saved = testResultRepository.save(result);

        if (passed) {

            log.info("TEST_RESULT_EVENT | userId={} entraId={} sectionId={} score={}",
                    saved.getUser().getId(),
                    saved.getUser().getEntraId(),
                    saved.getSection().getId(),
                    score
            );

            //Update userProgress
            UserProgress userProgress = userProgressRepository.findByCourseIdAndUserId(saved.getSection().getCourse().getId(), saved.getUser().getId());
            if (userProgress == null) {
                userProgress = new UserProgress();
                userProgress.setCourse(saved.getSection().getCourse());
                userProgress.setUser(saved.getUser());
            }
            userProgress.setCompletedSections(userProgress.getCompletedSections() + 1);
            int nbrOfSections = saved.getSection().getCourse().getSections().size();
            if(nbrOfSections == 0) {
                userProgress.setProgressPercentage(0);
            } else {
                userProgress.setProgressPercentage(Math.divideExact(userProgress.getCompletedSections()*100, nbrOfSections));
            }
            userProgressRepository.save(userProgress);

            eventPublisher.publishEvent(new TestResultEvent(saved.getId()));
        }

        return mapToResponse(saved);
    }

    private TestResultResponse mapToResponse(TestResult result) {
        return new TestResultResponse(
                result.getId(),
                result.getStatus().name(),
                result.getScore(),
                result.isPassed(),
                result.getStartedAt(),
                result.getCompletedAt(),
                result.getAttemptNumber()
        );
    }

    // =========================
// SECTION LOCK LOGIC
// =========================
    public boolean isSectionLocked(User user, Section section) {

        if (section.getOrderIndex() == 0) return false;

        Section previous = sectionRepository
                .findByCourseIdAndOrderIndex(
                        section.getCourse().getId(),
                        section.getOrderIndex() - 1
                )
                .orElseThrow();

        // =========================
        // GET LATEST ATTEMPT
        // =========================
        TestResult lastAttempt = testResultRepository
                .findTopByUser_EntraIdAndSectionIdOrderByAttemptNumberDesc(
                        user.getEntraId(),
                        previous.getId()
                )
                .orElse(null);

        // =========================
        // LOCK IF NOT COMPLETED
        // =========================
        return lastAttempt == null ||
                lastAttempt.getStatus() != TestResult.Status.COMPLETED;
    }

    public List<TestQuestionResponse> getQuestions(Long sectionId) {

        List<TestQuestion> questions = questionRepository.findBySectionId(sectionId);

        return questions.stream()
                .map(q -> new TestQuestionResponse(
                        q.getId(),
                        q.getQuestionText(),
                        q.getAnswers().stream()
                                .map(a -> new TestAnswerResponse(
                                        a.getId(),
                                        a.getAnswerText()
                                ))
                                .toList()
                ))
                .toList();
    }

    // =========================
    // GET ALL ATTEMPTS FOR SECTION
    // =========================
    @Transactional(readOnly = true)
    public List<TestResultResponse> getAttempts(String entraId, Long sectionId) {

        // =========================
        // VALIDATE USER
        // =========================
        User user = userRepository.findByEntraId(entraId)
                .orElseGet(() -> {
                    User u = new User();
                    u.setEntraId(entraId);
                    return userRepository.save(u);
                });

        // =========================
        // FETCH ALL ATTEMPTS (SORTED BY ATTEMPT NUMBER)
        // =========================
        List<TestResult> attempts =
                testResultRepository
                        .findByUser_EntraIdAndSectionIdOrderByAttemptNumberDesc(
                                entraId,
                                sectionId
                        );

        // =========================
        // MAP TO RESPONSE
        // =========================
        return attempts.stream()
                .map(this::mapToResponse)
                .toList();
    }
}