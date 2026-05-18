import { useState } from "react";
import type { FilterKey } from "../types";
import { COURSES, QUIZZES, FILTERS } from "../data";
import { CourseCard } from "../components/CourseCard";
import { QuizRow } from "../components/QuizRow";

export function CoursesView() {
    const [filter, setFilter] = useState<FilterKey>("all");
    const [search, setSearch] = useState("");

    const filtered = COURSES.filter((c) => {
        const matchFilter = filter === "all" || c.status === filter;
        const matchSearch = c.title.toLowerCase().includes(search.toLowerCase());
        return matchFilter && matchSearch;
    });

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

            <div className="vmv-filters">
                {FILTERS.map((f) => (
                    <button
                        key={f.key}
                        className={`vmv-filter ${filter === f.key ? "active" : ""}`}
                        onClick={() => setFilter(f.key)}
                    >
                        {f.label}
                    </button>
                ))}
            </div>

            <div className="vmv-section-head">Alla Kurser</div>

            <div className="vmv-courses">
                {filtered.length === 0 ? (
                    <div className="vmv-empty">Inga kurser matchar din sökning.</div>
                ) : (
                    filtered.map((c) => <CourseCard key={c.id} course={c} />)
                )}
            </div>

            <div className="vmv-bottom">
                <div>
                    <div className="vmv-panel-title">Examinationer</div>
                    {QUIZZES.slice(0, 3).map((q, i) => (
                        <QuizRow key={i} quiz={q} index={i} />
                    ))}
                </div>
            </div>
        </>
    );
}
