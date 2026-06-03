import { useState, useEffect, useCallback } from "react";
import { useMsal } from "@azure/msal-react";
import type { SectionResponse, TestQuestionResponse, TestAnswerRequest } from "../../types";
import { getTestQuestions, addTestQuestion } from "../../api/api";
import { FetchState } from "../../components/FetchState";
import { QuestionCard, AnswerListEditor } from "../../components/QuestionCard";

interface Props {
    section: SectionResponse;
    onBack: () => void;
}

const EMPTY_ANSWERS: TestAnswerRequest[] = [
    { answerText: "", correct: false },
    { answerText: "", correct: false },
];

export function SectionQuizView({ section, onBack }: Props) {
    const { instance } = useMsal();

    const [questions,  setQuestions]  = useState<TestQuestionResponse[]>([]);
    const [loading,    setLoading]    = useState(true);
    const [loadError,  setLoadError]  = useState<string | null>(null);
    const [fetchKey,   setFetchKey]   = useState(0);

    const [questionText, setQuestionText] = useState("");
    const [answers,      setAnswers]      = useState<TestAnswerRequest[]>(EMPTY_ANSWERS);
    const [saveStatus,   setSaveStatus]   = useState<"idle" | "submitting" | "success" | "error">("idle");
    const [saveError,    setSaveError]    = useState<string | null>(null);
    const [noCorrect,    setNoCorrect]    = useState(false);

    const retry = useCallback(() => {
        setLoading(true);
        setLoadError(null);
        setFetchKey((k) => k + 1);
    }, []);

    useEffect(() => {
        let cancelled = false;
        getTestQuestions(instance, section.id)
            .then((data) => {
                if (!cancelled) {
                    setQuestions(data ?? []);
                    setLoadError(null);
                    setLoading(false);
                }
            })
            .catch((err) => {
                if (!cancelled) {
                    setLoadError(err instanceof Error ? err.message : "Okänt fel");
                    setLoading(false);
                }
            });
        return () => { cancelled = true; };
    }, [instance, section.id, fetchKey]);

    // ── Answer helpers ────────────────────────────────────────────────────────

    function addAnswer()               { setAnswers((prev) => [...prev, { answerText: "", correct: false }]); }
    function removeAnswer(i: number)   { setAnswers((prev) => prev.filter((_, idx) => idx !== i)); }
    function updateText(i: number, text: string) {
        setAnswers((prev) => prev.map((a, idx) => idx === i ? { ...a, answerText: text } : a));
    }
    function setCorrect(i: number) {
        setAnswers((prev) => prev.map((a, idx) => ({ ...a, correct: idx === i })));
    }

    // ── Save new question ─────────────────────────────────────────────────────

    async function handleSave() {
        const trimmedQuestion = questionText.trim();
        const validAnswers    = answers.filter((a) => a.answerText.trim());
        if (!trimmedQuestion || validAnswers.length < 2) return;

        if (!validAnswers.some((a) => a.correct)) {
            setNoCorrect(true);
            return;
        }

        setNoCorrect(false);
        setSaveStatus("submitting");
        setSaveError(null);
        try {
            await addTestQuestion(instance, section.id, {
                questionText: trimmedQuestion,
                answers: validAnswers.map((a) => ({ answerText: a.answerText.trim(), correct: a.correct })),
            });
            setSaveStatus("success");
            setQuestionText("");
            setAnswers([...EMPTY_ANSWERS]);
            setNoCorrect(false);
            retry();
            setTimeout(() => setSaveStatus("idle"), 2500);
        } catch (err) {
            setSaveStatus("error");
            setSaveError(err instanceof Error ? err.message : "Okänt fel");
        }
    }

    const canSave =
        saveStatus !== "submitting" &&
        questionText.trim().length > 0 &&
        answers.filter((a) => a.answerText.trim()).length >= 2;

    return (
        <>
            <button className="vmv-back-btn" onClick={onBack}>← Tillbaka</button>

            <div className="vmv-section-head">Quiz — {section.title}</div>

            <div className="vmv-section-head vmv-section-head--sub">
                Frågor ({loading ? "…" : questions.length})
            </div>

            <FetchState loading={loading} error={loadError} onRetry={retry} />

            {!loading && !loadError && (
                questions.length === 0 ? (
                    <div className="vmv-empty">Inga frågor tillagda ännu.</div>
                ) : (
                    <div className="vmv-quiz-question-list">
                        {questions.map((q, i) => (
                            <QuestionCard
                                key={q.id}
                                question={q}
                                index={i}
                                sectionId={section.id}
                                onChanged={retry}
                            />
                        ))}
                    </div>
                )
            )}

            <div className="vmv-section-head vmv-section-head--sub" style={{ marginTop: "1.5rem" }}>
                Ny fråga
            </div>

            <div className="vmv-mgmt-add-panel">
                <div className="vmv-mgmt-quiz-field">
                    <label className="vmv-mgmt-quiz-field-label">Fråga</label>
                    <input
                        className="vmv-mgmt-section-input vmv-mgmt-quiz-question-input"
                        type="text"
                        placeholder="Skriv frågan här…"
                        value={questionText}
                        onChange={(e) => setQuestionText(e.target.value)}
                    />
                </div>

                <AnswerListEditor
                    answers={answers}
                    noCorrect={noCorrect}
                    onChange={updateText}
                    onSetCorrect={setCorrect}
                    onAdd={addAnswer}
                    onRemove={removeAnswer}
                />

                <div className="vmv-mgmt-quiz-footer" style={{ marginTop: "0.75rem" }}>
                    <button className="vmv-quiz-start" onClick={handleSave} disabled={!canSave}>
                        {saveStatus === "submitting" ? "Sparar…" : "Spara fråga ↗"}
                    </button>
                    {saveStatus === "success" && (
                        <span className="vmv-form-feedback vmv-form-feedback--success">✓ Fråga sparad.</span>
                    )}
                    {saveStatus === "error" && (
                        <span className="vmv-form-feedback vmv-form-feedback--error">Fel: {saveError}</span>
                    )}
                </div>
            </div>
        </>
    );
}
