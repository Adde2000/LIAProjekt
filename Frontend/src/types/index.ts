export type Status = "in-progress" | "completed" | "not-started";

export type LoadState<T> = { data: T | null; loading: boolean; error: string | null };

export function idle<T>(): LoadState<T> {
    return { data: null, loading: false, error: null };
}
export type ViewKey = "courses" | "quizzes" | "admin" | "aiChat";
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

export interface Material {
    fileId:       string;
    originalName: string;
}

export interface CourseRequest {
    id:          number | null;   // null on create; the server assigns the ID
    title:       string;
    description: string;
}

// Mirrors the Java SubmitAnswerRequest record
export interface SubmitAnswerRequest {
    questionId: number;
    answerId: number;
}

// Mirrors the Java TestAnswerResponse record
export interface TestAnswerResponse {
    id: number;
    answerText: string;
}

// Mirrors the Java TestQuestionResponse record
export interface TestQuestionResponse {
    id: number;
    questionText: string;
    answers: TestAnswerResponse[];
}

// Mirrors the Java TestResultResponse record
export interface TestResultResponse {
    id: number;
    status: string;
    score: number;
    passed: boolean;
    startedAt: string;
    completedAt: string;
    attemptNumber: number;
}

// Mirrors the Java TestAnswerRequest record
export interface TestAnswerRequest {
    answerText: string;
    correct: boolean;
}

// Mirrors the Java TestQuestionRequest record — sent as POST body when adding a question
export interface TestQuestionRequest {
    questionText: string;
    answers: TestAnswerRequest[];
}

// Mirrors the Map returned by /api/users/me/courses
export interface MyCourseEntry {
    courseResponse: CourseResponse;
    userProgressResponse: UserProgressResponse;
}

// Mirrors the Java UserProgressResponse record
export interface UserProgressResponse {
    userResponse: UserResponse;
    completedSections: number;
    progressPercentage: number;
}

// Shape returned by the API for courses — mirrors the Java CourseResponse class
export interface CourseResponse {
    id:          number;
    title:       string;
    description: string;
    createdBy:   string;
    //Optional assistantId
    assistantId?: string;
}

export interface ChatMessage {
    id: string;
    role: "user" | "assistant";
    content: string;
    timestamp: string;
}

export interface AssistantAdminResponse {
    id: string;
    name: string;
    description: string;
}
