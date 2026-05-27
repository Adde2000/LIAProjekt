import { useEffect, useState } from "react";
import { useMsal } from "@azure/msal-react";
import type { SectionResponse, TestQuestionResponse, TestResultResponse } from "../types";
import { getTestQuestions, submitQuiz } from "../api/api";
import { FetchState } from "../components/FetchState";

interface Props {
    section: SectionResponse;
    onBack: () => void;
    onDone: () => void;
}

type QuizPhase = "loading" | "error" | "taking" | "submitting" | "done";

export function QuizView({ section, onBack, onDone }: Props) {
    const { instance } = useMsal();

    const [questions,  setQuestions]  = useState<TestQuestionResponse[]>([]);
    const [loadError,  setLoadError]  = useState<string | null>(null);
    const [phase,      setPhase]      = useState<QuizPhase>("loading");

    // Map of questionId → selected answerId (null = unanswered)
    const [selected, setSelected] = useState<Record<number, number | null>>({});

    useEffect(() => {
        let cancelled = false;

        getTestQuestions(instance, section.id)
            .then((data) => {
                if (cancelled) return;
                const qs = data ?? [];
                setQuestions(qs);
                // Pre-populate selected map so every question starts at null
                const initial: Record<number, number | null> = {};
                qs.forEach((q) => { initial[q.id] = null; });
                setSelected(initial);
                setPhase(qs.length === 0 ? "done" : "taking");
            })
            .catch((err) => {
                if (cancelled) return;
                setLoadError(err instanceof Error ? err.message : "Okänt fel");
                setPhase("error");
            });

        return () => { cancelled = true; };
    }, [instance, section.id]);

    const [submitError, setSubmitError] = useState<string | null>(null);
    const [result,      setResult]      = useState<TestResultResponse | null>(null);

    function handleSelect(questionId: number, answerId: number) {
        setSelected((prev) => ({ ...prev, [questionId]: answerId }));
    }

    async function handleFinish() {
        const answers = Object.entries(selected)
            .filter(([, answerId]) => answerId !== null)
            .map(([questionId, answerId]) => ({
                questionId: Number(questionId),
                answerId: answerId as number,
            }));

        setPhase("submitting");
        setSubmitError(null);
        try {
            const res = await submitQuiz(instance, section.id, answers);
            setResult(res);
            setPhase("done");
        } catch (err) {
            setSubmitError(err instanceof Error ? err.message : "Okänt fel");
            setPhase("taking");
        }
    }

    const answeredCount = Object.values(selected).filter((v) => v !== null).length;
    const allAnswered   = questions.length > 0 && answeredCount === questions.length;

    return (
        <>
            <button className="vmv-back-btn" onClick={onBack}>
                ← Tillbaka
            </button>

            <div className="vmv-section-head">Quiz — {section.title}</div>

            {phase === "loading" && (
                <FetchState loading error={null} onRetry={() => {}} />
            )}

            {phase === "error" && (
                <FetchState loading={false} error={loadError} onRetry={onBack} />
            )}

            {(phase === "taking" || phase === "submitting") && (
                <>
                    <div className="vmv-quiz-progress">
                        {answeredCount} / {questions.length} besvarade
                    </div>

                    <div className="vmv-quiz-take-list">
                        {questions.map((q, qi) => (
                            <div key={q.id} className="vmv-quiz-take-card">
                                <div className="vmv-quiz-take-question">
                                    <span className="vmv-quiz-take-num">
                                        {String(qi + 1).padStart(2, "0")}
                                    </span>
                                    <span className="vmv-quiz-take-question-text">
                                        {q.questionText}
                                    </span>
                                </div>

                                <div className="vmv-quiz-take-answers">
                                    {q.answers.map((a) => {
                                        const isSelected = selected[q.id] === a.id;
                                        return (
                                            <button
                                                key={a.id}
                                                className={`vmv-quiz-take-answer${isSelected ? " vmv-quiz-take-answer--selected" : ""}`}
                                                onClick={() => handleSelect(q.id, a.id)}
                                                type="button"
                                            >
                                                <span className="vmv-quiz-take-answer-indicator" />
                                                <span className="vmv-quiz-take-answer-text">{a.answerText}</span>
                                            </button>
                                        );
                                    })}
                                </div>
                            </div>
                        ))}
                    </div>

                    <div className="vmv-quiz-take-footer">
                        <button
                            className="vmv-quiz-start"
                            onClick={handleFinish}
                            disabled={!allAnswered || phase === "submitting"}
                        >
                            {phase === "submitting" ? "Lämnar in…" : "Lämna in ↗"}
                        </button>
                        {!allAnswered && phase !== "submitting" && (
                            <span className="vmv-quiz-take-hint">
                                Besvara alla frågor för att lämna in.
                            </span>
                        )}
                        {submitError && (
                            <span className="vmv-form-feedback vmv-form-feedback--error">Fel: {submitError}</span>
                        )}
                    </div>
                </>
            )}

            {phase === "done" && (
                <div className="vmv-quiz-take-done">
                    {questions.length === 0 ? (
                        <p>Det finns inga frågor i det här quizet ännu.</p>
                    ) : result ? (
                        <>
                            <div className={`vmv-quiz-take-done-icon${result.passed ? " vmv-quiz-take-done-icon--passed" : " vmv-quiz-take-done-icon--failed"}`}>
                                {result.passed ? "✓" : "✕"}
                            </div>
                            <div className="vmv-quiz-take-done-title">
                                {result.passed ? "Godkänd!" : "Ej godkänd"}
                            </div>
                            <div className="vmv-quiz-take-done-score">
                                {result.score} / {questions.length} rätt
                            </div>
                            <div className="vmv-quiz-take-done-meta">
                                <span>Försök {result.attemptNumber}</span>
                                <span className="vmv-quiz-take-done-meta-sep">·</span>
                                <span>{result.status}</span>
                            </div>
                        </>
                    ) : (
                        <>
                            <div className="vmv-quiz-take-done-icon vmv-quiz-take-done-icon--passed">✓</div>
                            <div className="vmv-quiz-take-done-title">Quiz inlämnat!</div>
                        </>
                    )}
                    <button className="vmv-quiz-start" onClick={result ? onDone : onBack}>
                        ← Tillbaka till kursen
                    </button>
                </div>
            )}
        </>
    );
}
