import { useState, useEffect } from "react";
import { useMsal } from "@azure/msal-react";
import type { CourseResponse, SectionResponse, UserResponse, AssistantAdminResponse, LoadState } from "../../types";
import { getCourses, deleteCourse, getAssistants, assignAssistantToCourse, getUsers, updateCourse } from "../../api/api";
import { ConfirmDialog } from "../../components/ConfirmDialog";
import { FetchState } from "../../components/FetchState";
import { useHasRole } from "../../auth/useRoles";
import { normaliseRole } from "../../utils/roles";
import { pad } from "../../components/Shared";
import { CourseStudentsPanel } from "../../components/CourseStudentsPanel";
import { CourseSectionsPanel } from "../../components/CourseSectionsPanel";
import { SectionQuizView } from "./SectionQuizView";

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
                        <div className="vmv-mgmt-course-by">Skapad av: {c.createdBy}</div>
                    </div>
                </div>
            ))}
        </div>
    );
}

// ── Course detail panel ───────────────────────────────────────────────────────

function CourseDetail({ course, onDelete, onOpenQuiz, }: { course: CourseResponse; onDelete: (id: number) => void;  onOpenQuiz: (section: SectionResponse) => void; } ) {
    const { instance } = useMsal();
    const isAdmin = useHasRole("admin");

    const [showConfirm, setShowConfirm] = useState(false);
    const [deleting, setDeleting]       = useState(false);
    const [deleteError, setDeleteError] = useState<string | null>(null);

    const [assistants, setAssistants]           = useState<AssistantAdminResponse[]>([]);
    const [selectedAssistant, setSelectedAssistant] = useState(course.assistantId ?? "");

    const [courseAdmins, setCourseAdmins]               = useState<UserResponse[]>([]);
    const [selectedCourseAdmin, setSelectedCourseAdmin] = useState<number | null>(course.courseAdmin?.id ?? null);
    const [courseAdminStatus, setCourseAdminStatus]     = useState<"idle" | "saving" | "saved" | "error">("idle");
    const [courseAdminError, setCourseAdminError]       = useState<string | null>(null);

    useEffect(() => {
        let cancelled = false;
        getAssistants(instance)
            .then((data) => {
                if (!cancelled) {
                    const list = data as AssistantAdminResponse[];
                    setAssistants(list);
                    // Förvälj nuvarande assistant om den finns i listan
                    if (course.assistantId && list.find((a) => a.id === course.assistantId)) {
                        setSelectedAssistant(course.assistantId);
                    }
                }
            })
            .catch(console.error);
        return () => { cancelled = true; };
    }, [instance]);

    useEffect(() => {
        if (!isAdmin) return;
        let cancelled = false;
        getUsers(instance)
            .then((data) => {
                if (!cancelled && data) {
                    const admins = (data as UserResponse[]).filter((u) =>
                        u.role.some((r) => normaliseRole(r) === "courseAdmin")
                    );
                    if (course.courseAdmin && !admins.find((u) => u.id === course.courseAdmin!.id)) {
                        admins.unshift(course.courseAdmin);
                    }
                    setCourseAdmins(admins);
                }
            })
            .catch(console.error);
        return () => { cancelled = true; };
    }, [instance, isAdmin]);

    async function handleAssistantChange(assistantId: string) {
        try {
            await assignAssistantToCourse(instance, course.id, assistantId);
            setSelectedAssistant(assistantId);
        } catch (err) {
            console.error("Failed to assign assistant", err);
        }
    }

    async function handleSaveCourseAdmin() {
        setCourseAdminStatus("saving");
        setCourseAdminError(null);
        try {
            await updateCourse(instance, course.id, {
                title: course.title,
                description: course.description,
                aiSessionTtlWeeks: null,
                courseAdminId: selectedCourseAdmin,
            });
            setCourseAdminStatus("saved");
        } catch (err) {
            setCourseAdminError(err instanceof Error ? err.message : "Okänt fel");
            setCourseAdminStatus("error");
        }
    }

    async function handleDelete() {
        setDeleting(true);
        setDeleteError(null);
        try {
            await deleteCourse(instance, course.id);
            onDelete(course.id);
        } catch (err) {
            setDeleteError(err instanceof Error ? err.message : "Okänt fel");
            setDeleting(false);
            setShowConfirm(false);
        }
    }

    const currentAssistantName =
        assistants.find((a) => a.id === selectedAssistant)?.name
        ?? null;

    return (
        <div className="vmv-mgmt-detail">

            {/* ── Header: title, assistant, course admin, delete ── */}
            <div className="vmv-mgmt-detail-header">

                <div>
                    <div className="vmv-mgmt-detail-title">{course.title}</div>
                    <div className="vmv-mgmt-detail-desc">{course.description}</div>
                    <div className="vmv-mgmt-detail-meta">
                        Skapad av: {course.createdBy}
                    </div>

                    <div style={{ marginTop: "1rem" }}>
                        <div className="vmv-section-head">AI Assistant</div>

                        {currentAssistantName && (
                            <div
                                className="vmv-mgmt-detail-meta"
                                style={{ marginBottom: "0.4rem" }}
                            >
                                Nuvarande: <strong>{currentAssistantName}</strong>
                            </div>
                        )}

                        <select
                            className="vmv-mgmt-section-input"
                            value={selectedAssistant}
                            onChange={(e) => handleAssistantChange(e.target.value)}
                        >
                            <option value="">Välj AI Assistant</option>
                            {assistants.map((a) => (
                                <option key={a.id} value={a.id}>
                                    {a.name}
                                </option>
                            ))}
                        </select>
                    </div>

                    <div style={{ marginTop: "1rem" }}>
                        <div className="vmv-section-head">Kursledare</div>

                        {isAdmin ? (
                            <>
                                {course.courseAdmin && (
                                    <div
                                        className="vmv-mgmt-detail-meta"
                                        style={{ marginBottom: "0.4rem" }}
                                    >
                                        Nuvarande:{" "}
                                        <strong>{course.courseAdmin.displayName}</strong>
                                    </div>
                                )}

                                <div
                                    style={{
                                        display: "flex",
                                        gap: "0.5rem",
                                        alignItems: "center",
                                        flexWrap: "wrap",
                                    }}
                                >
                                    <select
                                        className="vmv-mgmt-section-input"
                                        value={selectedCourseAdmin ?? ""}
                                        onChange={(e) =>
                                            setSelectedCourseAdmin(
                                                e.target.value
                                                    ? Number(e.target.value)
                                                    : null
                                            )
                                        }
                                        disabled={courseAdminStatus === "saving"}
                                    >
                                        <option value="">Ingen kursledare</option>

                                        {courseAdmins.map((u) => (
                                            <option key={u.id} value={u.id}>
                                                {u.displayName}
                                                {u.mail ? ` — ${u.mail}` : ""}
                                            </option>
                                        ))}
                                    </select>

                                    <button
                                        className="vmv-quiz-start"
                                        onClick={handleSaveCourseAdmin}
                                        disabled={courseAdminStatus === "saving"}
                                    >
                                        {courseAdminStatus === "saving"
                                            ? "Sparar..."
                                            : "Spara ↗"}
                                    </button>
                                </div>

                                {courseAdminStatus === "saved" && (
                                    <div
                                        className="vmv-form-feedback vmv-form-feedback--success"
                                        style={{ marginTop: "0.5rem" }}
                                    >
                                        ✓ Kursledare uppdaterad.
                                    </div>
                                )}

                                {courseAdminStatus === "error" && (
                                    <div
                                        className="vmv-form-feedback vmv-form-feedback--error"
                                        style={{ marginTop: "0.5rem" }}
                                    >
                                        Fel: {courseAdminError}
                                    </div>
                                )}
                            </>
                        ) : (
                            course.courseAdmin && (
                                <div className="vmv-mgmt-detail-meta">
                                    <strong>{course.courseAdmin.displayName}</strong>
                                </div>
                            )
                        )}
                    </div>
                </div>

                {isAdmin && (
                    <div className="vmv-mgmt-detail-header-actions">
                        <button
                            className="vmv-quiz-start vmv-quiz-start--danger"
                            onClick={() => setShowConfirm(true)}
                        >
                            Ta bort kurs
                        </button>

                        {deleteError && (
                            <span className="vmv-form-feedback vmv-form-feedback--error">
                        Fel: {deleteError}
                    </span>
                        )}
                    </div>
                )}
            </div>

            {showConfirm && (
                <ConfirmDialog
                    title="Ta bort kurs"
                    message={`Är du säker på att du vill ta bort "${course.title}"? Åtgärden kan inte ångras.`}
                    confirmLabel="Ta bort"
                    danger
                    disabled={deleting}
                    onConfirm={handleDelete}
                    onCancel={() => setShowConfirm(false)}
                />
            )}

            <CourseStudentsPanel course={course} />
            <CourseSectionsPanel course={course} onOpenQuiz={onOpenQuiz} />
        </div>
    );
}

// ── Main view ─────────────────────────────────────────────────────────────────

export function ManageCoursesView() {
    const { instance } = useMsal();
    const [courses, setCourses]               = useState<LoadState<CourseResponse[]>>({ data: null, loading: true, error: null });
    const [selectedCourse, setSelectedCourse] = useState<CourseResponse | null>(null);
    const [quizSection, setQuizSection]       = useState<SectionResponse | null>(null);

    function handleCourseDeleted(id: number) {
        setCourses((prev) => ({ ...prev, data: (prev.data ?? []).filter((c) => c.id !== id) }));
        setSelectedCourse(null);
    }

    useEffect(() => {
        let cancelled = false;

        getCourses(instance)
            .then((data) => { if (!cancelled) setCourses({ data: data as CourseResponse[], loading: false, error: null }); })
            .catch((err) => { if (!cancelled) setCourses({ data: null, loading: false, error: (err as Error).message }); });

        return () => { cancelled = true; };
    }, [instance]);

    if (quizSection) {
        return <SectionQuizView section={quizSection} onBack={() => setQuizSection(null)} />;
    }

    return (
        <>
            <div className="vmv-section-head">Hantera kurser</div>

            <FetchState loading={courses.loading} error={courses.error} onRetry={() => window.location.reload()} />

            {courses.data && (
                <div className="vmv-mgmt-layout">
                    <CourseList
                        courses={courses.data}
                        selectedId={selectedCourse?.id ?? null}
                        onSelect={(c) => setSelectedCourse(c)}
                    />
                    <div className="vmv-mgmt-detail-pane">
                        {selectedCourse ? (
                            <CourseDetail course={selectedCourse} onDelete={handleCourseDeleted} onOpenQuiz={setQuizSection} />
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
