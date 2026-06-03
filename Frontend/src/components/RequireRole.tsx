import type { ReactNode } from "react";
import { useRoles } from "../auth/useRoles";
import type { UserRole } from "../types";

interface Props {
    /** A single role or list of roles — access is granted if the user has ANY of them. */
    role: UserRole | UserRole[];
    fallback?: ReactNode;
    children: ReactNode;
}

/**
 * Renders `children` only when the active user has at least one of the required roles.
 * Renders `fallback` (default: an access-denied message) otherwise.
 */
export function RequireRole({ role, fallback, children }: Props) {
    const userRoles = useRoles();
    const required  = Array.isArray(role) ? role : [role];
    const allowed   = required.some((r) => userRoles.includes(r));

    if (!allowed) {
        return (
            <>
                {fallback ?? (
                    <div className="vmv-access-denied">
                        <h2>Åtkomst nekad</h2>
                        <p>Du har inte behörighet att visa den här sidan.</p>
                    </div>
                )}
            </>
        );
    }

    return <>{children}</>;
}
