import { useMsal } from "@azure/msal-react";
import { normaliseRoles } from "../utils/roles";
import type { UserRole } from "../types";

/**
 * Reads the `roles` claim from the active account's ID-token claims.
 * Azure AD populates this via App Role assignments.
 *
 * Returns a typed UserRole[] so the rest of the app never deals
 * with raw strings.
 */
export function useRoles(): UserRole[] {
    const { instance } = useMsal();
    const account = instance.getActiveAccount();

    if (!account) return [];

    // idTokenClaims is typed as `object` by MSAL — cast to access claims
    const claims = account.idTokenClaims as Record<string, unknown> | undefined;
    const raw = claims?.["roles"];

    if (!Array.isArray(raw)) return [];

    return normaliseRoles(raw.filter((r): r is string => typeof r === "string"));
}

/** Convenience: true when the current user has the given role. */
export function useHasRole(role: UserRole): boolean {
    return useRoles().includes(role);
}
