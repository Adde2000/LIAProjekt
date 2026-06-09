import type { UserRole, UserResponse, User } from "../types";

export const ROLE_LABELS: Record<UserRole, string> = {
    admin:       "Admin",
    student:     "Student",
    courseAdmin: "Kursledare",
};

export const ROLE_CLS: Record<UserRole, string> = {
    admin:       "vmv-role vmv-role--admin",
    student:     "vmv-role vmv-role--student",
    courseAdmin: "vmv-role vmv-role--courseAdmin",
};

// toBackendRole maps a frontend UserRole to the string the backend expects
export function toBackendRole(role: UserRole): string {
    const map: Record<UserRole, string> = {
        admin:       "admin",
        student:     "participant",
        courseAdmin: "courseAdmin",
    };
    return map[role];
}

// toBackendRoles maps a full frontend role list to backend strings
export function toBackendRoles(roles: UserRole[]): string[] {
    return roles.map(toBackendRole);
}

export function normaliseRole(raw: string): UserRole {
    const map: Record<string, UserRole> = {
        admin:       "admin",
        student:     "student",
        participant: "student",   // backend name for the same role
        courseadmin: "courseAdmin",
        courseAdmin: "courseAdmin",
    };
    return map[raw] ?? map[raw.toLowerCase()] ?? "student";
}

// normaliseRoles maps the full Set<String> from the API to typed UserRole[]
export function normaliseRoles(raw: string[]): UserRole[] {
    return raw.map(normaliseRole);
}

export function mapUser(u: UserResponse): User {
    return {
        id:              u.id,
        name:            u.displayName,
        email:           u.mail,
        roles:           normaliseRoles(u.role),
        coursesEnrolled: 0,
    };
}
