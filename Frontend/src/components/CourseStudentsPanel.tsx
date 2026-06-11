import { useState, useEffect } from "react";
import { useMsal } from "@azure/msal-react";
import type { CourseResponse, UserResponse, UserProgressResponse, LoadState } from "../types";
import { getCourseStudents, addStudentsToCourse, getUsers } from "../api/api";
import { FetchState } from "./FetchState";
import { pad } from "./Shared";
import { ROLE_LABELS, ROLE_CLS, normaliseRole } from "../utils/roles";
import { useHasRole } from "../auth/useRoles";

// ── Student row ───────────────────────────────────────────────────────────────

export function StudentRow({ user, progress, index, checked, onToggle }: {
    user: UserResponse;
    progress?: { completedSections: number; progressPercentage: number };
    index: number;
    checked?: boolean;
    onToggle?: () => void;
}) {
    const selectable = onToggle !== undefined;
    return (
        <div
            className={`vmv-mgmt-student-row${selectable ? " vmv-mgmt-student-row--selectable" : ""}`}
            onClick={selectable ? onToggle : undefined}
        >
            {selectable && (
                <span className="vmv-mgmt-student-check">
                    <input
                        type="checkbox"
                        checked={!!checked}
                        onChange={onToggle}
                        onClick={(e) => e.stopPropagation()}
                    />
                </span>
            )}
            <span className="vmv-user-id">{pad(index + 1)}</span>
            <span className="vmv-user-name">{user.displayName}</span>
            <span className="vmv-user-email">
                {user.mail ?? <span style={{ opacity: 0.4, fontStyle: "italic" }}>–</span>}
            </span>
            <span className="vmv-role-badges">
                {user.role.map((r) => {
                    const normalised = normaliseRole(r);
                    return (
                        <span key={r} className={ROLE_CLS[normalised]}>{ROLE_LABELS[normalised]}</span>
                    );
                })}
            </span>
            {progress !== undefined && (
                <>
                    <span className="vmv-user-progress-sections">{progress.completedSections}</span>
                    <span className="vmv-user-progress-pct">
                        <span
                            className="vmv-user-progress-bar"
                            style={{ width: `${progress.progressPercentage}%` }}
                        />
                        <span className="vmv-user-progress-label">{progress.progressPercentage}%</span>
                    </span>
                </>
            )}
        </div>
    );
}

// ── Add students sub-panel ────────────────────────────────────────────────────

function AddStudentsPanel({ courseId, enrolledIds, onAdded, onCancel }: {
    courseId: number;
    enrolledIds: Set<number>;
    onAdded: (updated: UserProgressResponse[]) => void;
    onCancel: () => void;
}) {
    const { instance } = useMsal();
    const [allUsers, setAllUsers]   = useState<LoadState<UserResponse[]>>({ data: null, loading: true, error: null });
    const [staged, setStaged]       = useState<Set<number>>(new Set());
    const [addStatus, setAddStatus] = useState<"idle" | "submitting" | "error">("idle");
    const [addError, setAddError]   = useState<string | null>(null);
    const [search, setSearch]       = useState("");

    useEffect(() => {
        let cancelled = false;
        getUsers(instance)
            .then((data) => {
                if (!cancelled) setAllUsers({ data: data as UserResponse[], loading: false, error: null });
            })
            .catch((err) => {
                if (!cancelled) setAllUsers({ data: null, loading: false, error: (err as Error).message });
            });
        return () => { cancelled = true; };
    }, [instance]);

    function toggleStaged(id: number) {
        setStaged((prev) => {
            const next = new Set(prev);
            if (next.has(id)) { next.delete(id); } else { next.add(id); }
            return next;
        });
    }

    async function handleAddStudents() {
        if (staged.size === 0 || !allUsers.data) return;
        const toAdd = allUsers.data.filter((u) => staged.has(u.id));
        setAddStatus("submitting");
        setAddError(null);
        try {
            const updated = await addStudentsToCourse(instance, courseId, toAdd);
            onAdded(updated as UserProgressResponse[]);
        } catch (err) {
            setAddError(err instanceof Error ? err.message : "Okänt fel");
            setAddStatus("error");
        }
    }

    const available = (allUsers.data ?? []).filter(
        (u) =>
            !enrolledIds.has(u.id) &&
            u.role.map(normaliseRole).includes("student") &&
            (u.displayName.toLowerCase().includes(search.toLowerCase()) ||
             (u.mail ?? "").toLowerCase().includes(search.toLowerCase()))
    );

    return (
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
                            <span className="vmv-mgmt-student-check" />
                            <span>#</span>
                            <span>Namn</span>
                            <span>Email</span>
                            <span>Roll</span>
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
                                    checked={staged.has(u.id)}
                                    onToggle={() => toggleStaged(u.id)}
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
                        <button className="vmv-quiz-start" onClick={onCancel}>
                            Avbryt
                        </button>
                        {addStatus === "error" && (
                            <span className="vmv-form-feedback vmv-form-feedback--error">Fel: {addError}</span>
                        )}
                    </div>
                </>
            )}
        </div>
    );
}

// ── Inner panel (keyed by course.id so state resets naturally on course change)

function StudentsPanelInner({ course }: { course: CourseResponse }) {
    const { instance } = useMsal();
    const isAdmin = useHasRole("admin");

    const [enrolled, setEnrolled]   = useState<LoadState<UserProgressResponse[]>>({ data: null, loading: true, error: null });
    const [showAdd, setShowAdd]     = useState(false);
    const [addedMsg, setAddedMsg]   = useState(false);

    useEffect(() => {
        let cancelled = false;
        getCourseStudents(instance, course.id)
            .then((data) => {
                if (!cancelled) setEnrolled({ data: data as UserProgressResponse[], loading: false, error: null });
            })
            .catch((err) => {
                if (!cancelled) setEnrolled({ data: null, loading: false, error: (err as Error).message });
            });
        return () => { cancelled = true; };
    }, [instance, course.id]);

    const enrolledIds = new Set((enrolled.data ?? []).map((u) => u.userResponse.id));

    function handleAdded(updated: UserProgressResponse[]) {
        setEnrolled({ data: updated, loading: false, error: null });
        setShowAdd(false);
        setAddedMsg(true);
    }

    return (
        <>
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
                        <div className="vmv-mgmt-student-table vmv-mgmt-student-table--progress">
                            <div className="vmv-mgmt-student-thead">
                                <span>#</span>
                                <span>Namn</span>
                                <span>Email</span>
                                <span>Roll</span>
                                <span>Avsnitt</span>
                                <span>Framsteg</span>
                            </div>
                            {enrolled.data.map((entry, i) => (
                                <StudentRow
                                    key={entry.userResponse.id}
                                    user={entry.userResponse}
                                    progress={{ completedSections: entry.completedSections, progressPercentage: entry.progressPercentage }}
                                    index={i}
                                />
                            ))}
                        </div>
                    )}

                    <div className="vmv-mgmt-actions">
                        {isAdmin && !showAdd && (
                            <button
                                className="vmv-quiz-start"
                                onClick={() => { setShowAdd(true); setAddedMsg(false); }}
                            >
                                Lägg till studenter ↗
                            </button>
                        )}
                        {addedMsg && (
                            <span className="vmv-form-feedback vmv-form-feedback--success">✓ Studenter tillagda.</span>
                        )}
                    </div>
                </>
            )}

            {showAdd && (
                <AddStudentsPanel
                    courseId={course.id}
                    enrolledIds={enrolledIds}
                    onAdded={handleAdded}
                    onCancel={() => setShowAdd(false)}
                />
            )}
        </>
    );
}

// ── Public export — key forces full remount on course change ──────────────────

export function CourseStudentsPanel({ course }: { course: CourseResponse }) {
    return <StudentsPanelInner key={course.id} course={course} />;
}
