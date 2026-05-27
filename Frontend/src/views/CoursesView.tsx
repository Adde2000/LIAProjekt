import { useCallback, useEffect, useState } from "react";
import { useMsal } from "@azure/msal-react";
import type { CourseResponse, MyCourseEntry, LoadState } from "../types";
import { getMyCourses } from "../api/api";
import { FetchState } from "../components/FetchState";
import { CourseSectionView } from "./CourseSectionView";

export function CoursesView() {
    const { instance } = useMsal();
    const [fetchKey, setFetchKey] = useState(0);
    const [state, setState] = useState<LoadState<MyCourseEntry[]>>({ data: null, loading: true, error: null });
    const [search, setSearch] = useState("");
    const [selectedCourse, setSelectedCourse] = useState<CourseResponse | null>(null);

    const retry = useCallback(() => setFetchKey((k) => k + 1), []);

    useEffect(() => {
        let cancelled = false;

        getMyCourses(instance)
            .then((data) => {
                if (!cancelled) setState({ data: data ?? [], loading: false, error: null });
            })
            .catch(() => {
                if (!cancelled) setState({ data: null, loading: false, error: "Kunde inte hämta dina kurser." });
            });

        return () => { cancelled = true; };
    }, [instance, fetchKey]);

    const filtered = (state.data ?? []).filter((entry) =>
        entry.courseResponse.title.toLowerCase().includes(search.toLowerCase())
    );

    if (selectedCourse) {
        return (
            <CourseSectionView
                course={selectedCourse}
                onBack={() => setSelectedCourse(null)}
            />
        );
    }

    return (
        <>
            <div className="vmv-search">
                <span className="vmv-search-icon">⌕</span>
                <input
                    type="text"
                    placeholder="Sök kurser..."
                    value={search}
                    onChange={(e) => setSearch(e.target.value)}
                />
            </div>

            <div className="vmv-section-head">Mina Kurser</div>

            <FetchState
                loading={state.loading}
                error={state.error}
                onRetry={retry}
            />

            {!state.loading && !state.error && (
                <div className="vmv-courses">
                    {filtered.length === 0 ? (
                        <div className="vmv-empty">
                            {state.data?.length === 0
                                ? "Du är inte registrerad på några kurser."
                                : "Inga kurser matchar din sökning."}
                        </div>
                    ) : (
                        filtered.map(({ courseResponse: c, userProgressResponse: p }) => (
                            <div
                                key={c.id}
                                className="vmv-course"
                                onClick={() => setSelectedCourse(c)}
                                role="button"
                                tabIndex={0}
                                onKeyDown={(e) => e.key === "Enter" && setSelectedCourse(c)}
                            >
                                <div className="vmv-course-num">
                                    {String(c.id).padStart(2, "0")}
                                </div>
                                <div className="vmv-course-title">{c.title}</div>
                                <div className="vmv-course-meta">{c.createdBy}</div>
                                <div className="vmv-course-description">{c.description}</div>

                                <div className="vmv-course-footer">
                                    <div className="vmv-course-progress-bar-wrap">
                                        <div
                                            className="vmv-course-progress-bar-fill"
                                            style={{ width: `${p.progressPercentage}%` }}
                                        />
                                    </div>
                                    <span className="vmv-course-pct">{p.progressPercentage}%</span>
                                </div>
                            </div>
                        ))
                    )}
                </div>
            )}
        </>
    );
}
