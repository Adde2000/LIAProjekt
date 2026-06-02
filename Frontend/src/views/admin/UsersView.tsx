import { useState, useEffect } from "react";
import { useMsal } from "@azure/msal-react";
import type { User, UserRole, UserResponse } from "../../types";
import { getUsers } from "../../api/api";
import { pad } from "../../components/Shared";
import { ROLE_LABELS, ROLE_CLS, mapUser } from "../../utils/roles";
import { FetchState } from "../../components/FetchState";

// ── Hook ──────────────────────────────────────────────────────────────────────

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

// ── Component ─────────────────────────────────────────────────────────────────

export function UsersView() {
    const { users, loading, error }       = useUsers();
    const [search, setSearch]             = useState("");
    const [roleFilter, setRoleFilter]     = useState<"all" | UserRole>("all");
    const [selectedUser, setSelectedUser] = useState<User | null>(null);

    if (loading || error) {
        return (
            <FetchState
                loading={loading}
                error={error}
                onRetry={() => window.location.reload()}
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
                            <span className="vmv-user-email">{u.email ?? <span style={{ opacity: 0.4, fontStyle: "italic" }}>–</span>}</span>
                            <span className="vmv-role-badges">
                                {u.roles.map((r) => (
                                    <span key={r} className={ROLE_CLS[r]}>{ROLE_LABELS[r]}</span>
                                ))}
                            </span>
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
                </div>
            )}
        </>
    );
}
