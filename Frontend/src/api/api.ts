import type { IPublicClientApplication } from "@azure/msal-browser";
import { getAccessToken } from "../auth/getAccessToken";
import type {
    CourseRequest,
    UserResponse,
    ChatMessage
} from "../types";

const BASE_URL = import.meta.env.VITE_API_BASE_URL;

if (!BASE_URL) {
    console.error(
        "Missing VITE_API_BASE_URL in environment variables"
    );
}

// =========================
// GENERIC FETCH HELPERS
// =========================

async function safeFetch(
    url: string,
    token: string,
    errorMessage: string
) {

    try {

        const res = await fetch(url, {
            headers: {
                Authorization: `Bearer ${token}`
            }
        });

        if (!res.ok) {

            const text = await res.text()
                .catch(() => "");

            console.error(
                "API error:",
                res.status,
                text
            );

            throw new Error(errorMessage);
        }

        return await res.json();

    } catch (err) {

        console.error(
            "Network/API failure:",
            err
        );

        throw err;
    }
}

async function safePost(
    url: string,
    token: string,
    body: unknown,
    errorMessage: string
) {

    try {

        const res = await fetch(url, {
            method: "POST",
            headers: {
                Authorization: `Bearer ${token}`,
                "Content-Type": "application/json",
            },
            body: JSON.stringify(body),
        });

        if (!res.ok) {

            const text = await res.text()
                .catch(() => "");

            console.error(
                "API error:",
                res.status,
                text
            );

            throw new Error(errorMessage);
        }

        return await res.json();

    } catch (err) {

        console.error(
            "Network/API failure:",
            err
        );

        throw err;
    }
}

async function safeDelete(
    url: string,
    token: string,
    errorMessage: string
) {

    try {

        const res = await fetch(url, {
            method: "DELETE",
            headers: {
                Authorization: `Bearer ${token}`,
            },
        });

        if (!res.ok) {

            const text = await res.text()
                .catch(() => "");

            console.error(
                "API error:",
                res.status,
                text
            );

            throw new Error(errorMessage);
        }

    } catch (err) {

        console.error(
            "Network/API failure:",
            err
        );

        throw err;
    }
}

async function safePut(
    url: string,
    token: string,
    errorMessage: string
) {

    try {

        const res = await fetch(url, {
            method: "PUT",
            headers: {
                Authorization: `Bearer ${token}`,
            },
        });

        if (!res.ok) {

            const text = await res.text()
                .catch(() => "");

            console.error(
                "API error:",
                res.status,
                text
            );

            throw new Error(errorMessage);
        }

    } catch (err) {

        console.error(
            "Network/API failure:",
            err
        );

        throw err;
    }
}

// =========================
// HEALTH
// =========================

export async function getHealth(
    instance: IPublicClientApplication
) {

    if (!BASE_URL) return null;

    const token = await getAccessToken(instance);

    return safeFetch(
        `${BASE_URL}/health`,
        token,
        "Failed health request"
    );
}

// =========================
// USERS
// =========================

export async function getUsers(
    instance: IPublicClientApplication
) {

    if (!BASE_URL) return null;

    const token = await getAccessToken(instance);

    return safeFetch(
        `${BASE_URL}/api/users/all`,
        token,
        "Failed to fetch users"
    );
}

// =========================
// COURSES
// =========================

export async function createCourse(
    instance: IPublicClientApplication,
    course: CourseRequest
) {

    if (!BASE_URL) return null;

    const token = await getAccessToken(instance);

    return safePost(
        `${BASE_URL}/api/courses`,
        token,
        course,
        "Failed to create course"
    );
}

export async function getCourses(
    instance: IPublicClientApplication
) {

    if (!BASE_URL) return null;

    const token = await getAccessToken(instance);

    return safeFetch(
        `${BASE_URL}/api/courses`,
        token,
        "Failed to fetch courses"
    );
}

export async function getMyCourses(
    instance: IPublicClientApplication
) {

    if (!BASE_URL) return null;

    const token = await getAccessToken(instance);

    return safeFetch(
        `${BASE_URL}/api/users/me/courses`,
        token,
        "Failed to fetch your courses"
    );
}

export async function deleteCourse(
    instance: IPublicClientApplication,
    courseId: number
) {

    if (!BASE_URL) return null;

    const token = await getAccessToken(instance);

    return safeDelete(
        `${BASE_URL}/api/courses/${courseId}`,
        token,
        "Failed to delete course"
    );
}

// =========================
// COURSE STUDENTS
// =========================

export async function getCourseStudents(
    instance: IPublicClientApplication,
    courseId: number
) {

    if (!BASE_URL) return null;

    const token = await getAccessToken(instance);

    return safeFetch(
        `${BASE_URL}/api/courses/${courseId}/students`,
        token,
        "Failed to fetch course students"
    );
}

export async function addStudentsToCourse(
    instance: IPublicClientApplication,
    courseId: number,
    students: UserResponse[]
) {

    if (!BASE_URL) return null;

    const token = await getAccessToken(instance);

    return safePost(
        `${BASE_URL}/api/courses/${courseId}/students`,
        token,
        students,
        "Failed to add students to course"
    );
}

// =========================
// COURSE SECTIONS
// =========================

export async function getCourseSections(
    instance: IPublicClientApplication,
    courseId: number
) {

    if (!BASE_URL) return null;

    const token = await getAccessToken(instance);

    return safeFetch(
        `${BASE_URL}/api/courses/${courseId}/sections`,
        token,
        "Failed to fetch course sections"
    );
}

export async function addCourseSection(
    instance: IPublicClientApplication,
    courseId: number,
    title: string
) {

    if (!BASE_URL) return null;

    const token = await getAccessToken(instance);

    return safePost(
        `${BASE_URL}/api/courses/${courseId}/sections`,
        token,
        {
            title
        } satisfies import("../types").SectionRequest,
        "Failed to add section"
    );
}

// =========================
// AI SESSION
// =========================

export async function createAiSession(
    instance: IPublicClientApplication,
    userId: number,
    courseId: number,
) {

    if (!BASE_URL) return null;

    const token = await getAccessToken(instance);

    return safePost(
        `${BASE_URL}/api/ai/session?userId=${userId}&courseId=${courseId}`,
        token,
        {},
        "Failed to create AI session"
    );
}

// =========================
// AI CHAT
// =========================

export async function sendAiMessage(
    instance: IPublicClientApplication,
    sessionId: number,
    message: string
) {

    if (!BASE_URL) return null;

    const token = await getAccessToken(instance);

    return safePost(
        `${BASE_URL}/api/ai/chat`,
        token,
        {
            sessionId,
            message,
        },
        "Failed to send AI message"
    );
}

/**
 * IMPORTANT:
 * This requires a backend endpoint:
 *
 * GET /api/ai/session/{sessionId}
 *
 * Since Azure Threads already store the history,
 * you do NOT need your own DB chat history.
 */
export async function getAiMessages(
    instance: IPublicClientApplication,
    sessionId: number
): Promise<ChatMessage[]> {

    if (!BASE_URL) return [];

    const token = await getAccessToken(instance);

    return safeFetch(
        `${BASE_URL}/api/ai/history/${sessionId}`,
        token,
        "Failed to fetch AI messages"
    );
}

// =========================
// AI ASSISTANTS
// =========================

export async function getAssistants(
    instance: IPublicClientApplication
) {

    if (!BASE_URL) return null;

    const token = await getAccessToken(instance);

    return safeFetch(
        `${BASE_URL}/api/ai/assistants`,
        token,
        "Failed to fetch assistants"
    );
}

export async function assignAssistantToCourse(
    instance: IPublicClientApplication,
    courseId: number,
    assistantId: string
) {

    if (!BASE_URL) return null;

    const token = await getAccessToken(instance);

    return safePut(
        `${BASE_URL}/api/courses/${courseId}/assistant/${assistantId}`,
        token,
        "Failed to assign assistant"
    );
}