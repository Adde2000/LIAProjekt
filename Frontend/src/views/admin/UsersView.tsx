import { useState, useEffect } from "react";
import { useMsal } from "@azure/msal-react";
import type { User, UserRole, UserResponse } from "../../types";
import { getUsers, inviteUsers, deleteUser, updateUser } from "../../api/api";
import { pad } from "../../components/Shared";
import { ROLE_LABELS, ROLE_CLS, mapUser, toBackendRoles, normaliseRole } from "../../utils/roles";
import { FetchState } from "../../components/FetchState";
import { ConfirmDialog } from "../../components/ConfirmDialog";

// ── Hook ──────────────────────────────────────────────────────────────────────

function useUsers() {
    const { instance } = useMsal();
    const [users, setUsers]     = useState<User[]>([]);
    const [loading, setLoading] = useState(true);
    const [error, setError]     = useState<string | null>(null);

    function reload() {
        setLoading(true);
        setError(null);
        getUsers(instance)
            .then((data) => {
                if (!data) throw new Error("Tomt svar från servern");
                setUsers((data as UserResponse[]).map(mapUser));
            })
            .catch((err: Error) => setError(err.message))
            .finally(() => setLoading(false));
    }

    useEffect(() => { reload(); }, [instance]);

    return { users, loading, error, reload };
}

// ── Invite modal ──────────────────────────────────────────────────────────────

const ALL_ROLES: UserRole[] = ["admin", "student", "courseAdmin"];

type InviteRow = { email: string; displayName: string; roles: string[] };
type InviteTab = "manual" | "csv";

/** Parse a CSV file into InviteRows.
 *  Expected columns (header row required): email, displayName, roles
 *  roles column is comma-separated inside the cell, e.g. "student,courseAdmin"
 */
function parseCsv(text: string): { rows: InviteRow[]; parseError: string | null } {
    const lines = text.split(/\r?\n/).filter((l) => l.trim());
    if (lines.length < 2) return { rows: [], parseError: "CSV-filen måste ha en rubrikrad och minst en datarad." };

    const headers = lines[0].split(",").map((h) => h.trim().toLowerCase());
    const emailIdx       = headers.indexOf("email");
    const displayNameIdx = headers.indexOf("displayname");
    const rolesIdx       = headers.indexOf("roles");

    if (emailIdx === -1 || displayNameIdx === -1 || rolesIdx === -1) {
        return { rows: [], parseError: "Rubrikrad måste innehålla kolumnerna: email, displayName, roles" };
    }

    const rows: InviteRow[] = [];
    for (let i = 1; i < lines.length; i++) {
        const cols = lines[i].split(",").map((c) => c.trim());
        const email       = cols[emailIdx]       ?? "";
        const displayName = cols[displayNameIdx] ?? "";
        const roles       = (cols[rolesIdx] ?? "").split(";").map((r) => r.trim()).filter(Boolean);
        if (!email || !displayName) continue;
        rows.push({ email, displayName, roles: roles.length ? roles : ["student"] });
    }

    if (rows.length === 0) return { rows: [], parseError: "Inga giltiga rader hittades i CSV-filen." };
    return { rows, parseError: null };
}

function InviteModal({
    onClose,
    onSuccess,
}: {
    onClose: () => void;
    onSuccess: () => void;
}) {
    const { instance } = useMsal();
    const [tab, setTab]                   = useState<InviteTab>("manual");

    // Manual fields
    const [email, setEmail]               = useState("");
    const [displayName, setDisplayName]   = useState("");
    const [roles, setRoles]               = useState<UserRole[]>(["student"]);

    // CSV fields
    const [csvRows, setCsvRows]           = useState<InviteRow[] | null>(null);
    const [csvFileName, setCsvFileName]   = useState<string | null>(null);
    const [csvParseError, setCsvParseError] = useState<string | null>(null);

    const [submitting, setSubmitting]     = useState(false);
    const [error, setError]               = useState<string | null>(null);

    function toggleRole(role: UserRole) {
        setRoles((prev) =>
            prev.includes(role) ? prev.filter((r) => r !== role) : [...prev, role]
        );
    }

    function handleCsvFile(e: React.ChangeEvent<HTMLInputElement>) {
        const file = e.target.files?.[0];
        if (!file) return;
        setCsvFileName(file.name);
        const reader = new FileReader();
        reader.onload = (ev) => {
            const text = ev.target?.result as string;
            const { rows, parseError } = parseCsv(text);
            setCsvRows(rows);
            setCsvParseError(parseError);
        };
        reader.readAsText(file);
    }

    async function handleSubmit() {
        setError(null);
        let payload: InviteRow[];

        if (tab === "manual") {
            if (!email.trim() || !displayName.trim()) {
                setError("E-post och namn är obligatoriska.");
                return;
            }
            if (roles.length === 0) {
                setError("Välj minst en roll.");
                return;
            }
            payload = [{ email: email.trim(), displayName: displayName.trim(), roles: toBackendRoles(roles) }];
        } else {
            if (!csvRows || csvRows.length === 0) {
                setError(csvParseError ?? "Ladda upp en giltig CSV-fil.");
                return;
            }
            payload = csvRows.map((r) => ({
                ...r,
                roles: toBackendRoles(r.roles.map(normaliseRole)),
            }));
        }

        setSubmitting(true);
        try {
            await inviteUsers(instance, payload);
            onSuccess();
        } catch (err: unknown) {
            setError(err instanceof Error ? err.message : "Något gick fel.");
        } finally {
            setSubmitting(false);
        }
    }

    return (
        <div className="vmv-dialog-backdrop" onClick={onClose}>
            <div className="vmv-dialog" style={{ maxWidth: 460 }} onClick={(e) => e.stopPropagation()}>
                <div className="vmv-dialog-title">Bjud in användare</div>

                {/* Tab switcher */}
                <div className="vmv-filters" style={{ margin: "1rem 0 0" }}>
                    <button
                        className={`vmv-filter ${tab === "manual" ? "active" : ""}`}
                        onClick={() => setTab("manual")}
                        disabled={submitting}
                    >
                        Manuellt
                    </button>
                    <button
                        className={`vmv-filter ${tab === "csv" ? "active" : ""}`}
                        onClick={() => setTab("csv")}
                        disabled={submitting}
                    >
                        CSV-import
                    </button>
                </div>

                <div className="vmv-course-form" style={{ marginTop: "1rem" }}>
                    {tab === "manual" ? (
                        <>
                            <div className="vmv-form-field">
                                <label className="vmv-form-label">E-postadress</label>
                                <input
                                    className="vmv-form-input"
                                    type="email"
                                    placeholder="namn@exempel.se"
                                    value={email}
                                    onChange={(e) => setEmail(e.target.value)}
                                    disabled={submitting}
                                />
                            </div>
                            <div className="vmv-form-field">
                                <label className="vmv-form-label">Visningsnamn</label>
                                <input
                                    className="vmv-form-input"
                                    type="text"
                                    placeholder="Förnamn Efternamn"
                                    value={displayName}
                                    onChange={(e) => setDisplayName(e.target.value)}
                                    disabled={submitting}
                                />
                            </div>
                            <div className="vmv-form-field">
                                <label className="vmv-form-label">Roller</label>
                                <div style={{ display: "flex", gap: "8px", flexWrap: "wrap" }}>
                                    {ALL_ROLES.map((r) => (
                                        <button
                                            key={r}
                                            type="button"
                                            className={`vmv-filter ${roles.includes(r) ? "active" : ""}`}
                                            onClick={() => toggleRole(r)}
                                            disabled={submitting}
                                        >
                                            {ROLE_LABELS[r]}
                                        </button>
                                    ))}
                                </div>
                            </div>
                        </>
                    ) : (
                        <>
                            <div className="vmv-form-field">
                                <label className="vmv-form-label">CSV-fil</label>
                                <input
                                    className="vmv-form-input"
                                    type="file"
                                    accept=".csv,text/csv"
                                    onChange={handleCsvFile}
                                    disabled={submitting}
                                    style={{ padding: "6px 12px", cursor: "pointer" }}
                                />
                            </div>

                            {/* Format hint */}
                            <div style={{
                                fontFamily: "'IBM Plex Mono', monospace",
                                fontSize: "10px",
                                color: "var(--color-text-secondary)",
                                lineHeight: 1.7,
                                background: "var(--color-background-secondary)",
                                border: "1px solid var(--color-border-tertiary)",
                                padding: "8px 12px",
                            }}>
                                <div style={{ marginBottom: 4, textTransform: "uppercase", letterSpacing: "0.1em" }}>Förväntat format</div>
                                <div>email,displayName,roles</div>
                                <div style={{ opacity: 0.6 }}>anna@ex.se,Anna Svensson,student</div>
                                <div style={{ opacity: 0.6 }}>kalle@ex.se,Kalle Berg,student;courseAdmin</div>
                                <div style={{ marginTop: 4, opacity: 0.5 }}>Flera roller separeras med semikolon (;)</div>
                            </div>

                            {/* Preview */}
                            {csvRows && !csvParseError && (
                                <div style={{
                                    fontFamily: "'IBM Plex Mono', monospace",
                                    fontSize: "11px",
                                    color: "var(--color-text-secondary)",
                                }}>
                                    ✓ {csvFileName} — {csvRows.length} användare redo att bjudas in
                                </div>
                            )}

                            {csvParseError && (
                                <div style={{ color: "#c0392b", fontFamily: "'IBM Plex Mono', monospace", fontSize: "11px" }}>
                                    {csvParseError}
                                </div>
                            )}
                        </>
                    )}

                    {error && (
                        <div style={{ color: "#c0392b", fontFamily: "'IBM Plex Mono', monospace", fontSize: "11px" }}>
                            {error}
                        </div>
                    )}
                </div>

                <div className="vmv-dialog-actions" style={{ marginTop: "1.5rem" }}>
                    <button className="vmv-quiz-start" onClick={onClose} disabled={submitting}>
                        Avbryt
                    </button>
                    <button className="vmv-quiz-start" onClick={handleSubmit} disabled={submitting}>
                        {submitting
                            ? "Skickar..."
                            : tab === "csv" && csvRows
                            ? `Bjud in ${csvRows.length} användare`
                            : "Bjud in"}
                    </button>
                </div>
            </div>
        </div>
    );
}

// ── Edit modal ────────────────────────────────────────────────────────────────

function EditModal({
    user,
    onClose,
    onSuccess,
}: {
    user: User;
    onClose: () => void;
    onSuccess: (updated: User) => void;
}) {
    const { instance }                    = useMsal();
    const [displayName, setDisplayName]   = useState(user.name);
    const [roles, setRoles]               = useState<UserRole[]>(user.roles);
    const [submitting, setSubmitting]     = useState(false);
    const [error, setError]               = useState<string | null>(null);

    function toggleRole(role: UserRole) {
        setRoles((prev) =>
            prev.includes(role) ? prev.filter((r) => r !== role) : [...prev, role]
        );
    }

    async function handleSubmit() {
        if (!displayName.trim()) {
            setError("Namn är obligatoriskt.");
            return;
        }
        if (roles.length === 0) {
            setError("Välj minst en roll.");
            return;
        }
        setSubmitting(true);
        setError(null);
        try {
            await updateUser(instance, user.id, {
                email: null,
                displayName: displayName.trim(),
                roles: toBackendRoles(roles),
            });
            onSuccess({ ...user, name: displayName.trim(), roles });
        } catch (err: unknown) {
            setError(err instanceof Error ? err.message : "Något gick fel.");
        } finally {
            setSubmitting(false);
        }
    }

    return (
        <div className="vmv-dialog-backdrop" onClick={onClose}>
            <div className="vmv-dialog" style={{ maxWidth: 440 }} onClick={(e) => e.stopPropagation()}>
                <div className="vmv-dialog-title">Redigera användare</div>

                <div className="vmv-course-form" style={{ marginTop: "1rem" }}>
                    <div className="vmv-form-field">
                        <label className="vmv-form-label">Visningsnamn</label>
                        <input
                            className="vmv-form-input"
                            type="text"
                            value={displayName}
                            onChange={(e) => setDisplayName(e.target.value)}
                            disabled={submitting}
                        />
                    </div>

                    <div className="vmv-form-field">
                        <label className="vmv-form-label">E-postadress</label>
                        <input
                            className="vmv-form-input"
                            type="text"
                            value={user.email ?? ""}
                            disabled
                            style={{ opacity: 0.45, cursor: "not-allowed" }}
                        />
                    </div>

                    <div className="vmv-form-field">
                        <label className="vmv-form-label">Roller</label>
                        <div style={{ display: "flex", gap: "8px", flexWrap: "wrap" }}>
                            {ALL_ROLES.map((r) => (
                                <button
                                    key={r}
                                    type="button"
                                    className={`vmv-filter ${roles.includes(r) ? "active" : ""}`}
                                    onClick={() => toggleRole(r)}
                                    disabled={submitting}
                                >
                                    {ROLE_LABELS[r]}
                                </button>
                            ))}
                        </div>
                    </div>

                    {error && (
                        <div style={{ color: "#c0392b", fontFamily: "'IBM Plex Mono', monospace", fontSize: "11px" }}>
                            {error}
                        </div>
                    )}
                </div>

                <div className="vmv-dialog-actions" style={{ marginTop: "1.5rem" }}>
                    <button className="vmv-quiz-start" onClick={onClose} disabled={submitting}>
                        Avbryt
                    </button>
                    <button className="vmv-quiz-start" onClick={handleSubmit} disabled={submitting}>
                        {submitting ? "Sparar..." : "Spara"}
                    </button>
                </div>
            </div>
        </div>
    );
}

// ── Component ─────────────────────────────────────────────────────────────────

export function UsersView() {
    const { users, loading, error, reload } = useUsers();
    const { instance }                      = useMsal();
    const [search, setSearch]               = useState("");
    const [roleFilter, setRoleFilter]       = useState<"all" | UserRole>("all");
    const [selectedUser, setSelectedUser]   = useState<User | null>(null);
    const [showInvite, setShowInvite]       = useState(false);
    const [userToEdit, setUserToEdit]       = useState<User | null>(null);
    const [userToDelete, setUserToDelete]   = useState<User | null>(null);
    const [deleting, setDeleting]           = useState(false);

    async function handleDelete() {
        if (!userToDelete) return;
        setDeleting(true);
        try {
            await deleteUser(instance, userToDelete.id);
            setUserToDelete(null);
            setSelectedUser(null);
            reload();
        } catch {
            // error surfaced via the dialog disabled state — just close
        } finally {
            setDeleting(false);
        }
    }

    if ((loading && users.length === 0) || error) {
        return (
            <FetchState
                loading={loading}
                error={error}
                onRetry={reload}
            />
        );
    }

    const filtered = users.filter((u) => {
        const matchRole   = roleFilter === "all" || u.roles.includes(roleFilter);
        const matchSearch =
            u.name.toLowerCase().includes(search.toLowerCase()) ||
            (u.email ?? "").toLowerCase().includes(search.toLowerCase());
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
                <div style={{ display: "flex", gap: "8px", marginLeft: "auto", paddingLeft: "1rem", borderLeft: "1px solid var(--color-border-secondary)" }}>
                    <button
                        className="vmv-quiz-start"
                        style={{ whiteSpace: "nowrap" }}
                        onClick={() => setShowInvite(true)}
                    >
                        + Bjud in
                    </button>
                    <button
                        className="vmv-quiz-start"
                        style={{ whiteSpace: "nowrap" }}
                        onClick={reload}
                        disabled={loading}
                        aria-label="Uppdatera användarlistan"
                    >
                        {loading ? "↻ ..." : "↻"}
                    </button>
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
                            <span className="vmv-user-email">{u.email ?? <span style={{ opacity: 0.4, fontStyle: "italic" }}>–</span>}</span>
                            <span className="vmv-role-badges">
                                {u.roles.map((r) => (
                                    <span key={r} className={ROLE_CLS[r]}>{ROLE_LABELS[r]}</span>
                                ))}
                            </span>
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
                            <div className="vmv-user-detail-email">
                                {selectedUser.email ?? <span style={{ fontStyle: "italic", opacity: 0.5 }}>Ingen e-post registrerad</span>}
                            </div>
                        </div>
                        <button
                            className="vmv-user-detail-close"
                            onClick={() => setSelectedUser(null)}
                            aria-label="Stäng"
                        >✕</button>
                    </div>
                    <div className="vmv-user-detail-grid">
                        <div>
                            <div className="vmv-user-detail-label">Roller</div>
                            <div className="vmv-role-badges">
                                {selectedUser.roles.map((r) => (
                                    <span key={r} className={ROLE_CLS[r]}>{ROLE_LABELS[r]}</span>
                                ))}
                            </div>
                        </div>
                        <div>
                            <div className="vmv-user-detail-label">Antal Kurser</div>
                            <div className="vmv-user-detail-val">{selectedUser.coursesEnrolled}</div>
                        </div>
                    </div>
                    <div className="vmv-user-detail-actions">
                        <button
                            className="vmv-quiz-start"
                            onClick={() => setUserToEdit(selectedUser)}
                        >
                            Redigera
                        </button>
                        <button
                            className="vmv-quiz-start vmv-action--danger"
                            onClick={() => setUserToDelete(selectedUser)}
                        >
                            Ta bort användare
                        </button>
                    </div>
                </div>
            )}

            {showInvite && (
                <InviteModal
                    onClose={() => setShowInvite(false)}
                    onSuccess={() => {
                        setShowInvite(false);
                        reload();
                    }}
                />
            )}

            {userToEdit && (
                <EditModal
                    user={userToEdit}
                    onClose={() => setUserToEdit(null)}
                    onSuccess={(updated) => {
                        setUserToEdit(null);
                        setSelectedUser(updated);
                        reload();
                    }}
                />
            )}

            {userToDelete && (
                <ConfirmDialog
                    title="Ta bort användare"
                    message={`Är du säker på att du vill ta bort ${userToDelete.name}? Åtgärden kan inte ångras.`}
                    confirmLabel="Ta bort"
                    danger
                    disabled={deleting}
                    onConfirm={handleDelete}
                    onCancel={() => { setUserToDelete(null); setDeleting(false); }}
                />
            )}
        </>
    );
}
