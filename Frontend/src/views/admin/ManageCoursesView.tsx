import { useState, useEffect, useRef } from "react";
import { useMsal } from "@azure/msal-react";
import type { CourseResponse, UserResponse, LoadState, SectionResponse, AssistantAdminResponse } from "../../types";
import { idle } from "../../types";
import { getCourses, getCourseStudents, addStudentsToCourse, getUsers, deleteCourse, getAssistants, assignAssistantToCourse, getCourseSections as getSections, addCourseSection as addSection, uploadMaterial, deleteMaterial, getSectionMaterials } from "../../api/api";
import { ConfirmDialog } from "../../components/ConfirmDialog";
import { pad } from "../../components/Shared";
import { FetchState } from "../../components/FetchState";
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

function StudentRow({ user, index, action, checked, onToggle }: {
    user: UserResponse;
    index: number;
    action?: React.ReactNode;
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

// ── Section row with material upload/delete ───────────────────────────────────

const ACCEPTED_TYPES = ".pdf,.mp4,.mov,.avi,.mkv";

interface MaterialItem {
    fileId: string;
    originalName: string;
}

function extOf(filename: string): string {
    return filename.split(".").pop()?.toLowerCase() ?? "";
}

function fileIcon(ext: string) {
    if (ext === "pdf") return "📄";
    if (["mp4", "mov", "avi", "mkv"].includes(ext)) return "🎬";
    return "📎";
}

function SectionRow({ section, index, onOpenQuiz }: { section: SectionResponse; index: number; onOpenQuiz: (s: SectionResponse) => void }) {
    const { instance } = useMsal();
    const fileInputRef = useRef<HTMLInputElement>(null);

    const [expanded,    setExpanded]    = useState(false);
    const [materials,   setMaterials]   = useState<MaterialItem[]>([]);
    const [loadingFiles, setLoadingFiles] = useState(false);
    const [loadErr,     setLoadErr]     = useState<string | null>(null);
    const [uploading,   setUploading]   = useState(false);
    const [uploadErr,   setUploadErr]   = useState<string | null>(null);
    const [deleting,    setDeleting]    = useState<string | null>(null);
    const [deleteErr,   setDeleteErr]   = useState<string | null>(null);
    const fetchedRef = useRef(false);

    async function loadMaterials() {
        setLoadingFiles(true);
        setLoadErr(null);
        try {
            const result = await getSectionMaterials(instance, section.id) as MaterialItem[];
            setMaterials(result ?? []);
        } catch (err) {
            setLoadErr(err instanceof Error ? err.message : "Kunde inte hämta filer");
        } finally {
            setLoadingFiles(false);
        }
    }

    function handleToggle() {
        setExpanded((prev) => {
            if (!prev && !fetchedRef.current) {
                fetchedRef.current = true;
                loadMaterials();
            }
            return !prev;
        });
    }

    async function handleFileChange(e: React.ChangeEvent<HTMLInputElement>) {
        const file = e.target.files?.[0];
        if (!file) return;

        // Reset input so the same file can be re-uploaded if needed
        e.target.value = "";

        setUploading(true);
        setUploadErr(null);

        try {
            const result = await uploadMaterial(instance, section.id, file) as MaterialItem;
            setMaterials((prev) => [...prev, result]);
        } catch (err) {
            setUploadErr(err instanceof Error ? err.message : "Okänt fel vid uppladdning");
        } finally {
            setUploading(false);
        }
    }

    async function handleDelete(fileId: string) {
        setDeleting(fileId);
        setDeleteErr(null);

        try {
            await deleteMaterial(instance, fileId);
            setMaterials((prev) => prev.filter((m) => m.fileId !== fileId));
        } catch (err) {
            setDeleteErr(err instanceof Error ? err.message : "Okänt fel vid borttagning");
        } finally {
            setDeleting(null);
        }
    }

    return (
        <div className="vmv-mgmt-section-row-wrap">
            <div
                className={`vmv-mgmt-section-row vmv-mgmt-section-row--clickable ${expanded ? "expanded" : ""}`}
                onClick={handleToggle}
            >
                <span className="vmv-mgmt-section-num">{pad(index + 1)}</span>
                <span className="vmv-mgmt-section-title">{section.title}</span>
                <span className="vmv-mgmt-section-chevron">{expanded ? "▲" : "▼"}</span>
            </div>

            {expanded && (
                <div className="vmv-mgmt-material-panel">
                    {/* Material list */}
                    {loadingFiles ? (
                        <div className="vmv-mgmt-material-empty">Hämtar filer…</div>
                    ) : loadErr ? (
                        <div className="vmv-form-feedback vmv-form-feedback--error">Fel: {loadErr}</div>
                    ) : materials.length === 0 ? (
                        <div className="vmv-mgmt-material-empty">Inget material uppladdat ännu.</div>
                    ) : (
                        <div className="vmv-mgmt-material-list">
                            {materials.map((m) => (
                                <div key={m.fileId} className="vmv-mgmt-material-row">
                                    <span className="vmv-mgmt-material-icon">{fileIcon(extOf(m.originalName))}</span>
                                    <span className="vmv-mgmt-material-name">{m.originalName}</span>
                                    <span className="vmv-mgmt-material-type">{extOf(m.originalName).toUpperCase()}</span>
                                    <button
                                        className="vmv-mgmt-material-delete"
                                        disabled={deleting === m.fileId}
                                        onClick={(e) => { e.stopPropagation(); handleDelete(m.fileId); }}
                                        title="Ta bort"
                                    >
                                        {deleting === m.fileId ? "…" : "✕"}
                                    </button>
                                </div>
                            ))}
                        </div>
                    )}

                    {deleteErr && (
                        <div className="vmv-form-feedback vmv-form-feedback--error" style={{ marginTop: "0.5rem" }}>
                            Fel: {deleteErr}
                        </div>
                    )}

                    {/* Upload action */}
                    <div className="vmv-mgmt-material-footer">
                        <input
                            ref={fileInputRef}
                            type="file"
                            accept={ACCEPTED_TYPES}
                            style={{ display: "none" }}
                            onChange={handleFileChange}
                        />
                        <button
                            className="vmv-quiz-start"
                            disabled={uploading}
                            onClick={(e) => { e.stopPropagation(); fileInputRef.current?.click(); }}
                        >
                            {uploading ? "Laddar upp…" : "Ladda upp material ↗"}
                        </button>
                        {uploadErr && (
                            <span className="vmv-form-feedback vmv-form-feedback--error">Fel: {uploadErr}</span>
                        )}
                    </div>

                    {/* ── Quiz link ─────────────────────────────────────────── */}
                    <div className="vmv-mgmt-quiz-section" onClick={(e) => e.stopPropagation()}>
                        <div className="vmv-mgmt-quiz-header">
                            <span className="vmv-mgmt-quiz-label">Quiz</span>
                            <button
                                className="vmv-quiz-start"
                                onClick={(e) => { e.stopPropagation(); onOpenQuiz(section); }}
                            >
                                Hantera quiz ↗
                            </button>
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
}

// ── Course detail panel ───────────────────────────────────────────────────────

function CourseDetail({ course, onDelete }: { course: CourseResponse; onDelete: (id: number) => void }) {
    const { instance } = useMsal();

    const [enrolled, setEnrolled]   = useState<LoadState<UserResponse[]>>(idle());
    const [allUsers, setAllUsers]   = useState<LoadState<UserResponse[]>>(idle());
    const [staged, setStaged]       = useState<Set<number>>(new Set());
    const [addStatus, setAddStatus] = useState<"idle" | "submitting" | "success" | "error">("idle");
    const [addError, setAddError]   = useState<string | null>(null);
    const [showAdd, setShowAdd]     = useState(false);
    const [search, setSearch]       = useState("");
    const [showConfirm, setShowConfirm] = useState(false);
    const [deleting, setDeleting]       = useState(false);
    const [deleteError, setDeleteError] = useState<string | null>(null);

    const [sections, setSections]           = useState<LoadState<SectionResponse[]>>(idle());
    const [showAddSection, setShowAddSection] = useState(false);
    const [sectionTitle, setSectionTitle]   = useState("");
    const [sectionStatus, setSectionStatus] = useState<"idle" | "submitting" | "success" | "error">("idle");
    const [sectionError, setSectionError]   = useState<string | null>(null);
    const [quizSection, setQuizSection]     = useState<SectionResponse | null>(null);

    const [assistants, setAssistants] = useState<AssistantAdminResponse[]>([]);
    const [selectedAssistant, setSelectedAssistant] = useState(course.assistantId ?? "");

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

    useEffect(() => {
        let cancelled = false;

        getSections(instance, course.id)
            .then((data) => {
                if (!cancelled) setSections({ data: data as SectionResponse[], loading: false, error: null });
            })
            .catch((err) => {
                if (!cancelled) setSections({ data: null, loading: false, error: (err as Error).message });
            });

        return () => { cancelled = true; };
    }, [instance, course.id]);

    useEffect(() => {

        let cancelled = false;

        async function fetchAssistants() {

            try {

                const data = await getAssistants(instance);

                if (!cancelled) {setAssistants( data as AssistantAdminResponse[]);
                }

            } catch (err) {

                console.error(
                    "Failed to fetch assistants",
                    err
                );
            }
        }

        fetchAssistants();

        return () => {
            cancelled = true;
        };

    }, [instance]);

    async function handleAddSection() {
        const title = sectionTitle.trim();
        if (!title) return;

        setSectionStatus("submitting");
        setSectionError(null);

        try {
            const added = await addSection(instance, course.id, title) as SectionResponse;
            setSections((prev) => ({
                data: [...(prev.data ?? []), added],
                loading: false,
                error: null,
            }));
            setSectionTitle("");
            setSectionStatus("success");
            setShowAddSection(false);
        } catch (err) {
            setSectionError(err instanceof Error ? err.message : "Okänt fel");
            setSectionStatus("error");
        }
    }

    async function handleAssistantChange(
        assistantId: string
    ) {

        try {

            await assignAssistantToCourse(
                instance,
                course.id,
                assistantId
            );

            setSelectedAssistant(assistantId);

        } catch (err) {

            console.error(
                "Failed to assign assistant",
                err
            );
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

    if (quizSection) {
        return <SectionQuizView section={quizSection} onBack={() => setQuizSection(null)} />;
    }

    return (
        <div className="vmv-mgmt-detail">

            <div className="vmv-mgmt-detail-header">
                <div>
                    <div className="vmv-mgmt-detail-title">{course.title}</div>
                    <div className="vmv-mgmt-detail-desc">{course.description}</div>
                    <div className="vmv-mgmt-detail-meta">Skapad av: {course.createdBy}</div>

                    <div style={{ marginTop: "1rem" }}>

                        <div className="vmv-section-head">
                            AI Assistant
                        </div>

                        <select
                            className="vmv-mgmt-section-input"
                            value={selectedAssistant}
                            onChange={(e) =>
                                handleAssistantChange(e.target.value)
                            }
                        >

                            <option value="">
                                Välj AI Assistant
                            </option>

                            {assistants.map((assistant) => (

                                <option
                                    key={assistant.id}
                                    value={assistant.id}
                                >
                                    {assistant.name}
                                </option>

                            ))}

                        </select>

                    </div>
                </div>
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
                            </div>
                        </>
                    )}
                </div>
            )}

            {/* ── Sections ─────────────────────────────────────────────────── */}

            <div className="vmv-section-head" style={{ marginTop: "1.5rem" }}>
                Avsnitt ({sections.data?.length ?? "–"})
            </div>

            <FetchState loading={sections.loading} error={sections.error} />

            {sections.data && (
                <>
                    {sections.data.length === 0 ? (
                        <div className="vmv-empty" style={{ border: "1px solid var(--color-border-tertiary)" }}>
                            Inga avsnitt tillagda ännu.
                        </div>
                    ) : (
                        <div className="vmv-mgmt-section-list">
                            {sections.data.map((s, i) => (
                                <SectionRow key={s.id} section={s} index={i} onOpenQuiz={setQuizSection} />
                            ))}
                        </div>
                    )}

                    <div className="vmv-mgmt-actions">
                        <button
                            className="vmv-quiz-start"
                            onClick={() => { setShowAddSection((p) => !p); setSectionTitle(""); setSectionStatus("idle"); }}
                        >
                            {showAddSection ? "Avbryt" : "Lägg till avsnitt ↗"}
                        </button>
                        {sectionStatus === "success" && (
                            <span className="vmv-form-feedback vmv-form-feedback--success">✓ Avsnitt tillagt.</span>
                        )}
                        {sectionStatus === "error" && (
                            <span className="vmv-form-feedback vmv-form-feedback--error">Fel: {sectionError}</span>
                        )}
                    </div>

                    {showAddSection && (
                        <div className="vmv-mgmt-add-panel">
                            <div className="vmv-section-head">Nytt avsnitt</div>
                            <div className="vmv-mgmt-section-form">
                                <input
                                    className="vmv-mgmt-section-input"
                                    type="text"
                                    placeholder="Avsnittets titel..."
                                    value={sectionTitle}
                                    onChange={(e) => setSectionTitle(e.target.value)}
                                    onKeyDown={(e) => { if (e.key === "Enter") handleAddSection(); }}
                                    autoFocus
                                />
                                <button
                                    className="vmv-quiz-start"
                                    onClick={handleAddSection}
                                    disabled={!sectionTitle.trim() || sectionStatus === "submitting"}
                                >
                                    {sectionStatus === "submitting" ? "Sparar..." : "Lägg till ↗"}
                                </button>
                            </div>
                        </div>
                    )}
                </>
            )}
        </div>
    );
}

// ── Main view ─────────────────────────────────────────────────────────────────

export function ManageCoursesView() {
    const { instance } = useMsal();
    const [courses, setCourses]               = useState<LoadState<CourseResponse[]>>(idle());
    const [selectedCourse, setSelectedCourse] = useState<CourseResponse | null>(null);

    function handleCourseDeleted(id: number) {
        setCourses((prev) => ({
            ...prev,
            data: (prev.data ?? []).filter((c) => c.id !== id),
        }));
        setSelectedCourse(null);
    }

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
                            <CourseDetail course={selectedCourse} onDelete={handleCourseDeleted} />
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
