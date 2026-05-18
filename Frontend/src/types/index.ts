export type Status = "in-progress" | "completed" | "not-started";
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
    email: string;
    role: UserRole;
    coursesEnrolled: number;
}

// Shape returned by the API — mirrors the Java UserResponse record exactly
export interface UserResponse {
    id: number;
    displayName: string;
    givenName: string;
    surname: string;
    mail: string;
    Role: string;           // capital R, as the API sends it
}

// Mirrors the Java CourseRequest record — sent as POST body when creating a course
export interface CourseRequest {
    id:          number | null;   // null on create; the server assigns the ID
    title:       string;
    description: string;
}
