import { useState, useEffect } from "react";
import { useMsal } from "@azure/msal-react";
import type { User, UserRole, UserResponse, CourseRequest } from "../types";
import { getUsers, createCourse } from "../api/api";
import { pad } from "../components/Shared";
import { ManageCoursesView } from "./ManageCoursesView";

// ── Types ─────────────────────────────────────────────────────────────────────

type AdminTab = "users" | "create-course" | "manage-courses";

// ── Constants ─────────────────────────────────────────────────────────────────

const ROLE_LABELS: Record<UserRole, string> = {
    admin:       "Admin",
    student:     "Student",
    courseAdmin: "Kursledare",
};

const ROLE_CLS: Record<UserRole, string> = {
    admin:       "vmv-role vmv-role--admin",
    student:     "vmv-role vmv-role--student",
    courseAdmin: "vmv-role vmv-role--courseAdmin",
};

// ── Helpers ───────────────────────────────────────────────────────────────────

function normaliseRole(raw: string): UserRole {
    const map: Record<string, UserRole> = {
        admin:       "admin",
        student:     "student",
        courseadmin: "courseAdmin",
        courseAdmin: "courseAdmin",
    };
    return map[raw.toLowerCase()] ?? "student";
}

function mapUser(u: UserResponse): User {
    return {
        id:              u.id,
        name:            u.displayName,
        email:           u.mail,
        role:            normaliseRole(u.role),   // lowercase .role from updated DTO
        coursesEnrolled: 0,
    };
}

// ── Hooks ─────────────────────────────────────────────────────────────────────

function useUsers() {
    const { instance } = useMsal();
    const [users, setUsers]     = useState<User[]>([]);
    const [loading, setLoading] = useState(true);
    const [error, setError]     = useState<string | null>(null);

    useEffect(() => {
        getUsers(instance)
            .then((data) => {
                if (!data) throw new Error("Tomt svar från servern");
                setUsers((data as UserResponse[]).map(mapUser));
            })
            .catch((err: Error) => setError(err.message))
            .finally(() => setLoading(false));
    }, [instance]);

    return { users, loading, error };
}

// ── Users sub-view ────────────────────────────────────────────────────────────

function UsersView() {
    const { users, loading, error }         = useUsers();
    const [search, setSearch]               = useState("");
    const [roleFilter, setRoleFilter]       = useState<"all" | UserRole>("all");
    const [selectedUser, setSelectedUser]   = useState<User | null>(null);

    if (loading) {
        return (
            <div className="vmv-fetch-state">
                <div className="vmv-fetch-spinner" />
                <span>Hämtar användare...</span>
            </div>
        );
    }

    if (error) {
        return (
            <div className="vmv-fetch-state vmv-fetch-state--error">
                <span>Kunde inte hämta användare: {error}</span>
                <button className="vmv-quiz-start" onClick={() => window.location.reload()}>
                    Försök igen ↗
                </button>
            </div>
        );
    }

    const filtered = users.filter((u) => {
        const matchRole   = roleFilter === "all" || u.role === roleFilter;
        const matchSearch =
            u.name.toLowerCase().includes(search.toLowerCase()) ||
            u.email.toLowerCase().includes(search.toLowerCase());
        return matchRole && matchSearch;
    });

    return (
        <>
            <div className="vmv-admin-toolbar">
                <div className="vmv-search" style={{ flex: 1, margin: 0 }}>
                    <span className="vmv-search-icon">⌕</span>
                    <input
                        type="text"
                        placeholder="Sök användare..."
                        value={search}
                        onChange={(e) => setSearch(e.target.value)}
                    />
                </div>
                <div className="vmv-filters" style={{ margin: 0 }}>
                    {(["all", "admin", "student", "courseAdmin"] as const).map((r) => (
                        <button
                            key={r}
                            className={`vmv-filter ${roleFilter === r ? "active" : ""}`}
                            onClick={() => setRoleFilter(r)}
                        >
                            {r === "all" ? "Alla roller" : ROLE_LABELS[r]}
                        </button>
                    ))}
                </div>
            </div>

            <div className="vmv-section-head">
                Registrerade användare — {filtered.length} av {users.length}
            </div>

            <div className="vmv-user-table">
                <div className="vmv-user-thead">
                    <span>#</span>
                    <span>Namn</span>
                    <span>Email</span>
                    <span>Roll</span>
                    <span>Kurser</span>
                </div>

                {filtered.length === 0 ? (
                    <div className="vmv-empty">Inga användare matchar din sökning.</div>
                ) : (
                    filtered.map((u) => (
                        <div
                            key={u.id}
                            className={`vmv-user-row ${selectedUser?.id === u.id ? "selected" : ""}`}
                            onClick={() => setSelectedUser(selectedUser?.id === u.id ? null : u)}
                        >
                            <span className="vmv-user-id">{pad(u.id)}</span>
                            <span className="vmv-user-name">{u.name}</span>
                            <span className="vmv-user-email">{u.email}</span>
                            <span><span className={ROLE_CLS[u.role]}>{ROLE_LABELS[u.role]}</span></span>
                            <span className="vmv-user-meta">{u.coursesEnrolled}</span>
                        </div>
                    ))
                )}
            </div>

            {selectedUser && (
                <div className="vmv-user-detail">
                    <div className="vmv-user-detail-header">
                        <div className="vmv-user-avatar">
                            {selectedUser.name.split(" ").map((n) => n[0]).join("").slice(0, 2)}
                        </div>
                        <div>
                            <div className="vmv-user-detail-name">{selectedUser.name}</div>
                            <div className="vmv-user-detail-email">{selectedUser.email}</div>
                        </div>
                        <button
                            className="vmv-user-detail-close"
                            onClick={() => setSelectedUser(null)}
                            aria-label="Stäng"
                        >✕</button>
                    </div>
                    <div className="vmv-user-detail-grid">
                        <div>
                            <div className="vmv-user-detail-label">Roll</div>
                            <div className="vmv-user-detail-val">{ROLE_LABELS[selectedUser.role]}</div>
                        </div>
                        <div>
                            <div className="vmv-user-detail-label">Antal Kurser</div>
                            <div className="vmv-user-detail-val">{selectedUser.coursesEnrolled}</div>
                        </div>
                    </div>
                    <div className="vmv-user-detail-actions">
                        <button className="vmv-quiz-start">Lägg till i kurs ↗</button>
                        <button className="vmv-quiz-start vmv-action--danger">Ta bort från kurs ↗</button>
                    </div>
                </div>
            )}
        </>
    );
}

// ── Create course sub-view ────────────────────────────────────────────────────

type FormStatus = "idle" | "submitting" | "success" | "error";

const EMPTY_FORM: CourseRequest = { id: null, title: "", description: "" };

function CreateCourseView() {
    const { instance }              = useMsal();
    const [form, setForm]           = useState<CourseRequest>(EMPTY_FORM);
    const [status, setStatus]       = useState<FormStatus>("idle");
    const [errorMsg, setErrorMsg]   = useState<string | null>(null);

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

                {/* Preview of what will be sent */}
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

// ── AdminView — submenu shell ──────────────────────────────────────────────────

const ADMIN_TABS: { key: AdminTab; label: string }[] = [
    { key: "users",          label: "Användare"  },
    { key: "create-course",  label: "Ny kurs"    },
    { key: "manage-courses", label: "Hantera kurser" },
];

export function AdminView() {
    const [tab, setTab] = useState<AdminTab>("users");

    return (
        <>
            {/* Submenu */}
            <div className="vmv-admin-submenu">
                {ADMIN_TABS.map((t) => (
                    <button
                        key={t.key}
                        className={`vmv-admin-subtab ${tab === t.key ? "active" : ""}`}
                        onClick={() => setTab(t.key)}
                    >
                        {t.label}
                    </button>
                ))}
            </div>

            {tab === "users"          && <UsersView />}
            {tab === "create-course"  && <CreateCourseView />}
            {tab === "manage-courses" && <ManageCoursesView />}
        </>
    );
}
