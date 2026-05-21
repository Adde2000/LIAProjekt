export type Status = "in-progress" | "completed" | "not-started";

export type LoadState<T> = { data: T | null; loading: boolean; error: string | null };

export function idle<T>(): LoadState<T> {
    return { data: null, loading: false, error: null };
}
export type ViewKey = "courses" | "quizzes" | "admin";
export type FilterKey = "all" | Status;
export type UserRole = "admin" | "student" | "courseAdmin";

export interface Course {
    id: number;
    title: string;
    sections: number;
    progress: number;
    status: Status;
}

export interface Quiz {
    title: string;
    course: string;
    done: boolean;
    score: string | null;
}

export interface User {
    id: number;
    name: string;
    email: string | null;    // null if the user has no email registered
    roles: UserRole[];
    coursesEnrolled: number;
}

// Shape returned by the API — mirrors the Java UserResponse record exactly
export interface UserResponse {
    id: number;
    displayName: string;
    givenName: string;
    surname: string;
    mail: string | null;    // null if the user has no email registered
    role: string[];         // Java Set<String> deserialises to an array in JSON
}

// Mirrors the Java SectionRequest record — sent as POST body when creating a section
export interface SectionRequest {
    title: string;
}

// Shape returned by the API for sections — mirrors the Java SectionResponse record exactly
export interface SectionResponse {
    id:         number;
    title:      string;
    orderIndex: number;
    courseId:   number;
    isLocked:   boolean;
}

export interface CourseRequest {
    id:          number | null;   // null on create; the server assigns the ID
    title:       string;
    description: string;
}

// Shape returned by the API for courses — mirrors the Java CourseResponse class
export interface CourseResponse {
    id:          number;
    title:       string;
    description: string;
    createdBy:   string;
}
