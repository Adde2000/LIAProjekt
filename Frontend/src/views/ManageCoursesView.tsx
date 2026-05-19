import { useState, useEffect } from "react";
import { useMsal } from "@azure/msal-react";
import type { CourseResponse, UserResponse } from "../types";
import { getCourses, getCourseStudents, addStudentsToCourse, getUsers } from "../api/api";
import { pad } from "../components/Shared";

// ── Helpers ───────────────────────────────────────────────────────────────────

type LoadState<T> = { data: T | null; loading: boolean; error: string | null };

function idle<T>(): LoadState<T> {
    return { data: null, loading: false, error: null };
}

// ── Sub-components ────────────────────────────────────────────────────────────

function FetchState({ loading, error, onRetry }: {
    loading: boolean;
    error: string | null;
    onRetry?: () => void;
}) {
    if (loading) {
        return (
            <div className="vmv-fetch-state">
                <div className="vmv-fetch-spinner" />
                <span>Hämtar...</span>
            </div>
        );
    }
    if (error) {
        return (
            <div className="vmv-fetch-state vmv-fetch-state--error">
                <span>{error}</span>
                {onRetry && (
                    <button className="vmv-quiz-start" onClick={onRetry}>
                        Försök igen ↗
                    </button>
                )}
            </div>
        );
    }
    return null;
}

// ── Course list panel ─────────────────────────────────────────────────────────

function CourseList({
    courses,
    selectedId,
    onSelect,
}: {
    courses: CourseResponse[];
    selectedId: number | null;
    onSelect: (course: CourseResponse) => void;
}) {
    return (
        <div className="vmv-mgmt-course-list">
            {courses.map((c, i) => (
                <div
                    key={c.id}
                    className={`vmv-mgmt-course-item ${selectedId === c.id ? "selected" : ""}`}
                    onClick={() => onSelect(c)}
                >
                    <div className="vmv-mgmt-course-num">{pad(i + 1)}</div>
                    <div>
                        <div className="vmv-mgmt-course-title">{c.title}</div>
                        <div className="vmv-mgmt-course-meta">
                            {c.description.length > 60
                                ? c.description.slice(0, 60) + "…"
                                : c.description}
                        </div>
                        <div className="vmv-mgmt-course-by">
                            Skapad av: {c.createdBy}
                        </div>
                    </div>
                </div>
            ))}
        </div>
    );
}

// ── Student row ───────────────────────────────────────────────────────────────

function StudentRow({ user, index, action }: {
    user: UserResponse;
    index: number;
    action?: React.ReactNode;
}) {
    return (
        <div className="vmv-mgmt-student-row">
            <span className="vmv-user-id">{pad(index + 1)}</span>
            <span className="vmv-user-name">{user.displayName}</span>
            <span className="vmv-user-email">{user.mail ?? <span style={{ opacity: 0.4, fontStyle: "italic" }}>–</span>}</span>
            <span className="vmv-role-badges">
                {user.role.map((r) => (
                    <span key={r} className="vmv-mgmt-role-badge">{r}</span>
                ))}
            </span>
            {action && <span>{action}</span>}
        </div>
    );
}

// ── Course detail panel ───────────────────────────────────────────────────────

function CourseDetail({ course }: { course: CourseResponse }) {
    const { instance } = useMsal();

    // enrolled students
    const [enrolled, setEnrolled]   = useState<LoadState<UserResponse[]>>(idle());
    // all users (for the add panel)
    const [allUsers, setAllUsers]   = useState<LoadState<UserResponse[]>>(idle());
    // which users are staged to be added
    const [staged, setStaged]       = useState<Set<number>>(new Set());
    const [addStatus, setAddStatus] = useState<"idle" | "submitting" | "success" | "error">("idle");
    const [addError, setAddError]   = useState<string | null>(null);
    const [showAdd, setShowAdd]     = useState(false);
    const [search, setSearch]       = useState("");

    // Fetch enrolled students whenever the selected course changes
    useEffect(() => {
        let cancelled = false;

        async function fetchStudents() {
            setEnrolled({ data: null, loading: true, error: null });
            setStaged(new Set());
            setShowAdd(false);
            setAddStatus("idle");

            try {
                const data = await getCourseStudents(instance, course.id);
                if (!cancelled) {
                    setEnrolled({ data: data as UserResponse[], loading: false, error: null });
                }
            } catch (err) {
                if (!cancelled) {
                    setEnrolled({ data: null, loading: false, error: (err as Error).message });
                }
            }
        }

        fetchStudents();

        return () => { cancelled = true; };
    }, [instance, course.id]);

    // Fetch all users lazily — only when the add panel opens
    useEffect(() => {
        if (!showAdd || allUsers.data) return;

        let cancelled = false;

        async function fetchAllUsers() {
            setAllUsers({ data: null, loading: true, error: null });

            try {
                const data = await getUsers(instance);
                if (!cancelled) {
                    setAllUsers({ data: data as UserResponse[], loading: false, error: null });
                }
            } catch (err) {
                if (!cancelled) {
                    setAllUsers({ data: null, loading: false, error: (err as Error).message });
                }
            }
        }

        fetchAllUsers();

        return () => { cancelled = true; };
    }, [showAdd, instance, allUsers.data]);

    const enrolledIds = new Set((enrolled.data ?? []).map((u) => u.id));

    // Users available to add = all users minus already enrolled
    const available = (allUsers.data ?? []).filter(
        (u) => !enrolledIds.has(u.id) &&
               (u.displayName.toLowerCase().includes(search.toLowerCase()) ||
               (u.mail ?? "").toLowerCase().includes(search.toLowerCase()))
    );

    function toggleStaged(id: number) {
        setStaged((prev) => {
            const next = new Set(prev);
            if (next.has(id)) {
                next.delete(id);
            } else {
                next.add(id);
            }
            return next;
        });
    }

    async function handleAddStudents() {
        if (staged.size === 0 || !allUsers.data) return;

        const toAdd = allUsers.data.filter((u) => staged.has(u.id));
        setAddStatus("submitting");
        setAddError(null);

        try {
            const updated = await addStudentsToCourse(instance, course.id, toAdd);
            setEnrolled({ data: updated as UserResponse[], loading: false, error: null });
            setStaged(new Set());
            setShowAdd(false);
            setAddStatus("success");
        } catch (err) {
            setAddError(err instanceof Error ? err.message : "Okänt fel");
            setAddStatus("error");
        }
    }

    return (
        <div className="vmv-mgmt-detail">

            {/* Course header */}
            <div className="vmv-mgmt-detail-header">
                <div>
                    <div className="vmv-mgmt-detail-title">{course.title}</div>
                    <div className="vmv-mgmt-detail-desc">{course.description}</div>
                    <div className="vmv-mgmt-detail-meta">Skapad av: {course.createdBy}</div>
                </div>
            </div>

            {/* Enrolled students */}
            <div className="vmv-section-head" style={{ marginTop: "1.25rem" }}>
                Inregistrerade studenter ({enrolled.data?.length ?? "–"})
            </div>

            <FetchState loading={enrolled.loading} error={enrolled.error} />

            {enrolled.data && (
                <>
                    {enrolled.data.length === 0 ? (
                        <div className="vmv-empty" style={{ gridColumn: "unset", border: "1px solid var(--color-border-tertiary)" }}>
                            Inga studenter inregistrerade ännu.
                        </div>
                    ) : (
                        <div className="vmv-mgmt-student-table">
                            <div className="vmv-mgmt-student-thead">
                                <span>#</span>
                                <span>Namn</span>
                                <span>Email</span>
                                <span>Roll</span>
                            </div>
                            {enrolled.data.map((u, i) => (
                                <StudentRow key={u.id} user={u} index={i} />
                            ))}
                        </div>
                    )}

                    {/* Toggle add panel */}
                    <div className="vmv-mgmt-actions">
                        <button
                            className="vmv-quiz-start"
                            onClick={() => { setShowAdd((p) => !p); setSearch(""); }}
                        >
                            {showAdd ? "Avbryt" : "Lägg till studenter ↗"}
                        </button>
                        {addStatus === "success" && (
                            <span className="vmv-form-feedback vmv-form-feedback--success">
                                ✓ Studenter tillagda.
                            </span>
                        )}
                        {addStatus === "error" && (
                            <span className="vmv-form-feedback vmv-form-feedback--error">
                                Fel: {addError}
                            </span>
                        )}
                    </div>
                </>
            )}

            {/* Add students panel */}
            {showAdd && (
                <div className="vmv-mgmt-add-panel">
                    <div className="vmv-section-head">Välj studenter att lägga till</div>

                    <FetchState loading={allUsers.loading} error={allUsers.error} />

                    {allUsers.data && (
                        <>
                            <div className="vmv-search" style={{ marginBottom: "0.75rem" }}>
                                <span className="vmv-search-icon">⌕</span>
                                <input
                                    type="text"
                                    placeholder="Sök användare..."
                                    value={search}
                                    onChange={(e) => setSearch(e.target.value)}
                                />
                            </div>

                            <div className="vmv-mgmt-student-table">
                                <div className="vmv-mgmt-student-thead vmv-mgmt-student-thead--add">
                                    <span>#</span>
                                    <span>Namn</span>
                                    <span>Email</span>
                                    <span>Roll</span>
                                    <span>Välj</span>
                                </div>

                                {available.length === 0 ? (
                                    <div className="vmv-empty" style={{ gridColumn: "unset" }}>
                                        Inga tillgängliga användare.
                                    </div>
                                ) : (
                                    available.map((u, i) => (
                                        <StudentRow
                                            key={u.id}
                                            user={u}
                                            index={i}
                                            action={
                                                <input
                                                    type="checkbox"
                                                    className="vmv-mgmt-checkbox"
                                                    checked={staged.has(u.id)}
                                                    onChange={() => toggleStaged(u.id)}
                                                />
                                            }
                                        />
                                    ))
                                )}
                            </div>

                            <div className="vmv-mgmt-add-footer">
                                <span className="vmv-mgmt-staged-count">
                                    {staged.size} vald{staged.size !== 1 ? "a" : ""}
                                </span>
                                <button
                                    className="vmv-quiz-start"
                                    onClick={handleAddStudents}
                                    disabled={staged.size === 0 || addStatus === "submitting"}
                                >
                                    {addStatus === "submitting" ? "Sparar..." : "Bekräfta ↗"}
                                </button>
                            </div>
                        </>
                    )}
                </div>
            )}
        </div>
    );
}

// ── Main view ─────────────────────────────────────────────────────────────────

export function ManageCoursesView() {
    const { instance } = useMsal();
    const [courses, setCourses]             = useState<LoadState<CourseResponse[]>>(idle());
    const [selectedCourse, setSelectedCourse] = useState<CourseResponse | null>(null);

    useEffect(() => {
        let cancelled = false;

        async function fetchCourses() {
            setCourses({ data: null, loading: true, error: null });

            try {
                const data = await getCourses(instance);
                if (!cancelled) {
                    setCourses({ data: data as CourseResponse[], loading: false, error: null });
                }
            } catch (err) {
                if (!cancelled) {
                    setCourses({ data: null, loading: false, error: (err as Error).message });
                }
            }
        }

        fetchCourses();

        return () => { cancelled = true; };
    }, [instance]);

    return (
        <>
            <div className="vmv-section-head">Hantera kurser</div>

            <FetchState
                loading={courses.loading}
                error={courses.error}
                onRetry={() => window.location.reload()}
            />

            {courses.data && (
                <div className="vmv-mgmt-layout">
                    <CourseList
                        courses={courses.data}
                        selectedId={selectedCourse?.id ?? null}
                        onSelect={(c) => setSelectedCourse(c)}
                    />
                    <div className="vmv-mgmt-detail-pane">
                        {selectedCourse ? (
                            <CourseDetail course={selectedCourse} />
                        ) : (
                            <div className="vmv-mgmt-placeholder">
                                ← Välj en kurs för att hantera studenter
                            </div>
                        )}
                    </div>
                </div>
            )}
        </>
    );
}
