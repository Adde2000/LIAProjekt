import { useCallback, useEffect, useState } from "react";
import { useMsal } from "@azure/msal-react";
import type { CourseResponse, LoadState, SectionResponse } from "../types";
import { getCourseSections } from "../api/api";
import { FetchState } from "../components/FetchState";

interface Props {
    course: CourseResponse;
    onBack: () => void;
}

function LockIcon() {
    return (
        <svg
            className="vmv-section-lock-icon"
            xmlns="http://www.w3.org/2000/svg"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            strokeWidth="2"
            strokeLinecap="round"
            strokeLinejoin="round"
            aria-label="Låst"
        >
            <rect x="3" y="11" width="18" height="11" rx="2" ry="2" />
            <path d="M7 11V7a5 5 0 0 1 10 0v4" />
        </svg>
    );
}

export function CourseSectionView({ course, onBack }: Props) {
    const { instance } = useMsal();
    const [fetchKey, setFetchKey] = useState(0);
    const [state, setState] = useState<LoadState<SectionResponse[]>>({
        data: null,
        loading: true,
        error: null,
    });

    const retry = useCallback(() => setFetchKey((k) => k + 1), []);

    useEffect(() => {
        let cancelled = false;

        getCourseSections(instance, course.id)
            .then((data) => {
                if (!cancelled)
                    setState({ data: data ?? [], loading: false, error: null });
            })
            .catch(() => {
                if (!cancelled)
                    setState({
                        data: null,
                        loading: false,
                        error: "Kunde inte hämta kursens avsnitt.",
                    });
            });

        return () => { cancelled = true; };
    }, [instance, course.id, fetchKey]);

    // Sort by orderIndex so display order always matches the server's intent
    const sorted = [...(state.data ?? [])].sort((a, b) => a.orderIndex - b.orderIndex);

    return (
        <>
            {/* ── Back button + course header ── */}
            <button className="vmv-back-btn" onClick={onBack}>
                ← Tillbaka
            </button>

            <div className="vmv-section-head">{course.title}</div>

            {course.description && (
                <p className="vmv-course-section-description">
                    {course.description}
                </p>
            )}

            <div className="vmv-course-section-meta">
                Skapad av {course.createdBy}
            </div>

            {/* ── Sections list ── */}
            <div className="vmv-section-head vmv-section-head--sub">Avsnitt</div>

            <FetchState loading={state.loading} error={state.error} onRetry={retry} />

            {!state.loading && !state.error && (
                <div className="vmv-sections">
                    {sorted.length === 0 ? (
                        <div className="vmv-empty">
                            Den här kursen har inga avsnitt ännu.
                        </div>
                    ) : (
                        sorted.map((section) => (
                            <div
                                key={section.id}
                                className={`vmv-section-item${section.isLocked ? " vmv-section-item--locked" : ""}`}
                                aria-disabled={section.isLocked}
                                title={section.isLocked ? "Slutför föregående avsnitt för att låsa upp" : undefined}
                            >
                                <div className="vmv-section-item-num">
                                    {String(section.orderIndex + 1).padStart(2, "0")}
                                </div>
                                <div className="vmv-section-item-title">
                                    {section.title}
                                </div>
                                {section.isLocked && (
                                    <div className="vmv-section-item-lock">
                                        <LockIcon />
                                        <span className="vmv-section-item-lock-label">Låst</span>
                                    </div>
                                )}
                            </div>
                        ))
                    )}
                </div>
            )}
        </>
    );
}
