import { useState } from "react";
import { useMsal } from "@azure/msal-react";
import type { CourseRequest } from "../../types";
import { createCourse } from "../../api/api";

type FormStatus = "idle" | "submitting" | "success" | "error";

const EMPTY_FORM: CourseRequest = { id: null, title: "", description: "" };

export function CreateCourseView() {
    const { instance }            = useMsal();
    const [form, setForm]         = useState<CourseRequest>(EMPTY_FORM);
    const [status, setStatus]     = useState<FormStatus>("idle");
    const [errorMsg, setErrorMsg] = useState<string | null>(null);

    function updateField(field: keyof CourseRequest, value: string) {
        setForm((prev) => ({ ...prev, [field]: value }));
    }

    async function handleSubmit() {
        if (!form.title.trim() || !form.description.trim()) return;

        setStatus("submitting");
        setErrorMsg(null);

        try {
            await createCourse(instance, form);
            setStatus("success");
            setForm(EMPTY_FORM);
        } catch (err) {
            setErrorMsg(err instanceof Error ? err.message : "Okänt fel");
            setStatus("error");
        }
    }

    return (
        <>
            <div className="vmv-section-head">Skapa ny kurs</div>

            <div className="vmv-course-form">

                <div className="vmv-form-field">
                    <label className="vmv-form-label" htmlFor="course-title">
                        Titel
                    </label>
                    <input
                        id="course-title"
                        className="vmv-form-input"
                        type="text"
                        placeholder="Kursens namn..."
                        value={form.title}
                        onChange={(e) => updateField("title", e.target.value)}
                        disabled={status === "submitting"}
                    />
                </div>

                <div className="vmv-form-field">
                    <label className="vmv-form-label" htmlFor="course-description">
                        Beskrivning
                    </label>
                    <textarea
                        id="course-description"
                        className="vmv-form-input vmv-form-textarea"
                        placeholder="Beskriv kursens innehåll..."
                        value={form.description}
                        onChange={(e) => updateField("description", e.target.value)}
                        disabled={status === "submitting"}
                        rows={5}
                    />
                </div>

                <div className="vmv-form-preview">
                    <div className="vmv-form-preview-label">Förhandsgranskning</div>
                    <div className="vmv-form-preview-title">
                        {form.title || <span style={{ opacity: 0.4 }}>Ingen titel ännu</span>}
                    </div>
                    <div className="vmv-form-preview-desc">
                        {form.description || <span style={{ opacity: 0.4 }}>Ingen beskrivning ännu</span>}
                    </div>
                </div>

                <div className="vmv-form-actions">
                    <button
                        className="vmv-quiz-start"
                        onClick={handleSubmit}
                        disabled={status === "submitting" || !form.title.trim() || !form.description.trim()}
                    >
                        {status === "submitting" ? "Sparar..." : "Skapa kurs ↗"}
                    </button>
                    <button
                        className="vmv-quiz-start"
                        onClick={() => { setForm(EMPTY_FORM); setStatus("idle"); setErrorMsg(null); }}
                        disabled={status === "submitting"}
                    >
                        Rensa
                    </button>
                </div>

                {status === "success" && (
                    <div className="vmv-form-feedback vmv-form-feedback--success">
                        ✓ Kursen skapades.
                    </div>
                )}
                {status === "error" && (
                    <div className="vmv-form-feedback vmv-form-feedback--error">
                        Fel: {errorMsg}
                    </div>
                )}
            </div>
        </>
    );
}
