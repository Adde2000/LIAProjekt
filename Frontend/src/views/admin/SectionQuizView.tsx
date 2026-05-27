import { useState, useEffect, useCallback } from "react";
import { useMsal } from "@azure/msal-react";
import type { SectionResponse, TestQuestionResponse, TestAnswerRequest } from "../../types";
import { getTestQuestions, addTestQuestion, updateTestQuestion, deleteTestQuestion } from "../../api/api";
import { FetchState } from "../../components/FetchState";
import { ConfirmDialog } from "../../components/ConfirmDialog";

interface Props {
    section: SectionResponse;
    onBack: () => void;
}

const EMPTY_ANSWERS: TestAnswerRequest[] = [
    { answerText: "", correct: false },
    { answerText: "", correct: false },
];

// ── Inline edit form embedded in QuestionCard ─────────────────────────────────
function EditQuestionForm({
    question,
    sectionId,
    onSaved,
    onCancel,
}: {
    question: TestQuestionResponse;
    sectionId: number;
    onSaved: () => void;
    onCancel: () => void;
}) {
    const { instance } = useMsal();

    const [questionText, setQuestionText] = useState(question.questionText);
    const [answers, setAnswers] = useState<TestAnswerRequest[]>(
        question.answers.map((a) => ({ answerText: a.answerText, correct: false }))
    );
    const [status, setStatus] = useState<"idle" | "submitting" | "error">("idle");
    const [error, setError] = useState<string | null>(null);

    function addAnswer() {
        setAnswers((prev) => [...prev, { answerText: "", correct: false }]);
    }

    function removeAnswer(i: number) {
        setAnswers((prev) => prev.filter((_, idx) => idx !== i));
    }

    function updateAnswerText(i: number, text: string) {
        setAnswers((prev) => prev.map((a, idx) => idx === i ? { ...a, answerText: text } : a));
    }

    function setCorrect(i: number) {
        setAnswers((prev) => prev.map((a, idx) => ({ ...a, correct: idx === i })));
    }

    async function handleSave() {
        const trimmed = questionText.trim();
        const valid = answers.filter((a) => a.answerText.trim());
        if (!trimmed || valid.length < 2) return;

        setStatus("submitting");
        setError(null);
        try {
            await updateTestQuestion(instance, sectionId, question.id, {
                questionText: trimmed,
                answers: valid.map((a) => ({ answerText: a.answerText.trim(), correct: a.correct })),
            });
            onSaved();
        } catch (err) {
            setStatus("error");
            setError(err instanceof Error ? err.message : "Okänt fel");
        }
    }

    const canSave =
        status !== "submitting" &&
        questionText.trim().length > 0 &&
        answers.filter((a) => a.answerText.trim()).length >= 2;

    return (
        <div className="vmv-mgmt-add-panel" style={{ marginTop: "0.5rem" }}>
            <div className="vmv-mgmt-quiz-field">
                <label className="vmv-mgmt-quiz-field-label">Fråga</label>
                <input
                    className="vmv-mgmt-section-input vmv-mgmt-quiz-question-input"
                    type="text"
                    value={questionText}
                    onChange={(e) => setQuestionText(e.target.value)}
                />
            </div>

            <div className="vmv-mgmt-quiz-field" style={{ marginTop: "0.75rem" }}>
                <label className="vmv-mgmt-quiz-field-label">
                    Svarsalternativ
                    <span className="vmv-mgmt-quiz-field-hint"> — markera rätt svar med ✓</span>
                </label>
                <div className="vmv-mgmt-quiz-answers">
                    {answers.map((a, i) => (
                        <div key={i} className="vmv-mgmt-quiz-answer-row">
                            <button
                                className={`vmv-mgmt-quiz-correct-btn${a.correct ? " vmv-mgmt-quiz-correct-btn--active" : ""}`}
                                title={a.correct ? "Rätt svar" : "Markera som rätt"}
                                onClick={() => setCorrect(i)}
                                type="button"
                            >
                                ✓
                            </button>
                            <input
                                className="vmv-mgmt-section-input vmv-mgmt-quiz-answer-input"
                                type="text"
                                placeholder={`Svar ${i + 1}…`}
                                value={a.answerText}
                                onChange={(e) => updateAnswerText(i, e.target.value)}
                            />
                            {answers.length > 2 && (
                                <button
                                    className="vmv-mgmt-material-delete"
                                    onClick={() => removeAnswer(i)}
                                    title="Ta bort svar"
                                    type="button"
                                >
                                    ✕
                                </button>
                            )}
                        </div>
                    ))}
                </div>
                <button className="vmv-mgmt-quiz-add-answer" onClick={addAnswer} type="button">
                    + Lägg till svar
                </button>
            </div>

            <div className="vmv-mgmt-quiz-footer" style={{ marginTop: "0.75rem" }}>
                <button className="vmv-quiz-start" onClick={handleSave} disabled={!canSave}>
                    {status === "submitting" ? "Sparar…" : "Spara ändringar ↗"}
                </button>
                <button
                    className="vmv-quiz-start"
                    onClick={onCancel}
                    disabled={status === "submitting"}
                    style={{ marginLeft: "0.5rem" }}
                >
                    Avbryt
                </button>
                {status === "error" && (
                    <span className="vmv-form-feedback vmv-form-feedback--error">Fel: {error}</span>
                )}
            </div>
        </div>
    );
}

// ── QuestionCard with edit / delete actions ───────────────────────────────────
function QuestionCard({
    question,
    index,
    sectionId,
    onChanged,
}: {
    question: TestQuestionResponse;
    index: number;
    sectionId: number;
    onChanged: () => void;
}) {
    const { instance } = useMsal();
    const [editing, setEditing] = useState(false);
    const [confirmDelete, setConfirmDelete] = useState(false);
    const [deleting, setDeleting] = useState(false);

    async function handleDelete() {
        setDeleting(true);
        try {
            await deleteTestQuestion(instance, sectionId, question.id);
            onChanged();
        } catch {
            setDeleting(false);
            setConfirmDelete(false);
        }
    }

    return (
        <>
            <div className="vmv-quiz-question-card">
                <div className="vmv-quiz-question-header">
                    <span className="vmv-quiz-question-num">{String(index + 1).padStart(2, "0")}</span>
                    <span className="vmv-quiz-question-text">{question.questionText}</span>
                    <div style={{ marginLeft: "auto", display: "flex", gap: "0.4rem", flexShrink: 0 }}>
                        <button
                            className="vmv-mgmt-material-delete"
                            title="Redigera fråga"
                            onClick={() => setEditing((e) => !e)}
                            type="button"
                            style={{ fontSize: "0.85rem", padding: "0.2rem 0.55rem" }}
                        >
                            ✏
                        </button>
                        <button
                            className="vmv-mgmt-material-delete"
                            title="Ta bort fråga"
                            onClick={() => setConfirmDelete(true)}
                            type="button"
                        >
                            ✕
                        </button>
                    </div>
                </div>
                <div className="vmv-quiz-answer-list">
                    {question.answers.map((a) => (
                        <div key={a.id} className="vmv-quiz-answer-item">
                            <span className="vmv-quiz-answer-bullet">–</span>
                            <span className="vmv-quiz-answer-text">{a.answerText}</span>
                        </div>
                    ))}
                </div>

                {editing && (
                    <EditQuestionForm
                        question={question}
                        sectionId={sectionId}
                        onSaved={() => { setEditing(false); onChanged(); }}
                        onCancel={() => setEditing(false)}
                    />
                )}
            </div>

            {confirmDelete && (
                <ConfirmDialog
                    title="Ta bort fråga"
                    message={`Är du säker på att du vill ta bort frågan "${question.questionText}"?`}
                    confirmLabel="Ta bort"
                    danger
                    disabled={deleting}
                    onConfirm={handleDelete}
                    onCancel={() => setConfirmDelete(false)}
                />
            )}
        </>
    );
}

// ── Main view ─────────────────────────────────────────────────────────────────
export function SectionQuizView({ section, onBack }: Props) {
    const { instance } = useMsal();

    const [questions,   setQuestions]   = useState<TestQuestionResponse[]>([]);
    const [loading,     setLoading]     = useState(true);
    const [loadError,   setLoadError]   = useState<string | null>(null);
    const [fetchKey,    setFetchKey]    = useState(0);

    // ── New question form state ───────────────────────────────────────────────
    const [questionText, setQuestionText] = useState("");
    const [answers,      setAnswers]      = useState<TestAnswerRequest[]>(EMPTY_ANSWERS);
    const [saveStatus,   setSaveStatus]   = useState<"idle" | "submitting" | "success" | "error">("idle");
    const [saveError,    setSaveError]    = useState<string | null>(null);

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
    function addAnswer() {
        setAnswers((prev) => [...prev, { answerText: "", correct: false }]);
    }

    function removeAnswer(i: number) {
        setAnswers((prev) => prev.filter((_, idx) => idx !== i));
    }

    function updateAnswerText(i: number, text: string) {
        setAnswers((prev) => prev.map((a, idx) => idx === i ? { ...a, answerText: text } : a));
    }

    function setCorrect(i: number) {
        setAnswers((prev) => prev.map((a, idx) => ({ ...a, correct: idx === i })));
    }

    // ── Save question ─────────────────────────────────────────────────────────
    async function handleSave() {
        const trimmedQuestion = questionText.trim();
        const validAnswers = answers.filter((a) => a.answerText.trim());
        if (!trimmedQuestion || validAnswers.length < 2) return;

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
            <button className="vmv-back-btn" onClick={onBack}>
                ← Tillbaka
            </button>

            <div className="vmv-section-head">
                Quiz — {section.title}
            </div>

            {/* ── Existing questions ── */}
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

            {/* ── Add new question ── */}
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

                <div className="vmv-mgmt-quiz-field" style={{ marginTop: "0.75rem" }}>
                    <label className="vmv-mgmt-quiz-field-label">
                        Svarsalternativ
                        <span className="vmv-mgmt-quiz-field-hint"> — markera rätt svar med ✓</span>
                    </label>
                    <div className="vmv-mgmt-quiz-answers">
                        {answers.map((a, i) => (
                            <div key={i} className="vmv-mgmt-quiz-answer-row">
                                <button
                                    className={`vmv-mgmt-quiz-correct-btn${a.correct ? " vmv-mgmt-quiz-correct-btn--active" : ""}`}
                                    title={a.correct ? "Rätt svar" : "Markera som rätt"}
                                    onClick={() => setCorrect(i)}
                                    type="button"
                                >
                                    ✓
                                </button>
                                <input
                                    className="vmv-mgmt-section-input vmv-mgmt-quiz-answer-input"
                                    type="text"
                                    placeholder={`Svar ${i + 1}…`}
                                    value={a.answerText}
                                    onChange={(e) => updateAnswerText(i, e.target.value)}
                                />
                                {answers.length > 2 && (
                                    <button
                                        className="vmv-mgmt-material-delete"
                                        onClick={() => removeAnswer(i)}
                                        title="Ta bort svar"
                                        type="button"
                                    >
                                        ✕
                                    </button>
                                )}
                            </div>
                        ))}
                    </div>
                    <button className="vmv-mgmt-quiz-add-answer" onClick={addAnswer} type="button">
                        + Lägg till svar
                    </button>
                </div>

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
