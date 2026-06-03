import type { IPublicClientApplication } from "@azure/msal-browser";
import { getAccessToken } from "../auth/getAccessToken";
import type {
    CourseRequest,
    UserResponse,
    UserProgressResponse,
    MyCourseEntry,
    ChatMessage,
    TestQuestionRequest,
    TestQuestionResponse,
    SubmitAnswerRequest,
    TestResultResponse
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
            const text = await res.text().catch(() => "");
            console.error("API error:", res.status, text);
            throw new Error(errorMessage);
        }

        const contentType = res.headers.get("content-type") ?? "";
        if (res.status === 204 || !contentType.includes("application/json")) return null;
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
            const text = await res.text().catch(() => "");
            console.error("API error:", res.status, text);
            throw new Error(errorMessage);
        }

        return await res.json();
    } catch (err) {
        console.error("Network/API failure:", err);
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

export async function getUsers(instance: IPublicClientApplication) {

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

export async function getCourses(instance: IPublicClientApplication) {
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
): Promise<MyCourseEntry[]> {

    if (!BASE_URL) return [];

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
): Promise<UserProgressResponse[]> {
    if (!BASE_URL) return [];

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
        { title } satisfies import("../types").SectionRequest,
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
export async function getSectionMaterials(
    instance: IPublicClientApplication,
    sectionId: number
) {
    if (!BASE_URL) return null;

    const token = await getAccessToken(instance);

    const res = await fetch(`${BASE_URL}/api/material/list/section/${sectionId}`, {
        headers: { Authorization: `Bearer ${token}` },
    });

    if (!res.ok) {
        const text = await res.text().catch(() => "");
        console.error("API error:", res.status, text);
        throw new Error("Failed to fetch section materials");
    }

    return await res.json();
}

export async function uploadMaterial(
    instance: IPublicClientApplication,
    sectionId: number,
    file: File
) {
    if (!BASE_URL) return null;

    const token = await getAccessToken(instance);

    const formData = new FormData();
    formData.append("file", file);
    formData.append("sectionId", String(sectionId));

    const res = await fetch(`${BASE_URL}/api/material/upload`, {
        method: "POST",
        headers: { Authorization: `Bearer ${token}` },
        body: formData,
    });

    if (!res.ok) {
        const text = await res.text().catch(() => "");
        console.error("API error:", res.status, text);
        throw new Error("Failed to upload material");
    }

    return await res.json();
}

export async function deleteMaterial(
    instance: IPublicClientApplication,
    fileId: string
) {
    if (!BASE_URL) return null;

    const token = await getAccessToken(instance);

    const res = await fetch(`${BASE_URL}/api/material/${encodeURIComponent(fileId)}`, {
        method: "DELETE",
        headers: { Authorization: `Bearer ${token}` },
    });

    if (!res.ok) {
        const text = await res.text().catch(() => "");
        console.error("API error:", res.status, text);
        throw new Error("Failed to delete material");
    }
}


export async function getStreamToken(
    instance: IPublicClientApplication,
    fileId: string
): Promise<{ streamToken: string; streamUrl: string; expiresIn: number }> {
    if (!BASE_URL) throw new Error("Missing VITE_API_BASE_URL");

    const token = await getAccessToken(instance);

    const res = await fetch(
        `${BASE_URL}/api/material/stream-token/${encodeURIComponent(fileId)}`,
        { headers: { Authorization: `Bearer ${token}` } }
    );

    if (!res.ok) {
        throw new Error(`Failed to get stream token: ${res.status}`);
    }

    return res.json();
}

export async function getDownloadUrl(
    instance: IPublicClientApplication,
    fileId: string
): Promise<string> {
    if (!BASE_URL) throw new Error("Missing VITE_API_BASE_URL");

    const token = await getAccessToken(instance);

    const res = await fetch(
        `${BASE_URL}/api/material/download/${encodeURIComponent(fileId)}`,
        { headers: { Authorization: `Bearer ${token}` } }
    );

    if (!res.ok) throw new Error(`Download failed: ${res.status}`);

    // Backend returns the pre-signed URL as a plain string or JSON
    const text = await res.text();
    try {
        const json = JSON.parse(text);
        return json.url ?? json.downloadUrl ?? json.sasUrl ?? text;
    } catch {
        return text.trim();
    }
}

export async function downloadMaterialBlob(
    instance: IPublicClientApplication,
    fileId: string
): Promise<Blob> {
    if (!BASE_URL) throw new Error("Missing VITE_API_BASE_URL");

    const token = await getAccessToken(instance);

    const res = await fetch(
        `${BASE_URL}/api/material/download/${encodeURIComponent(fileId)}`,
        { headers: { Authorization: `Bearer ${token}` } }
    );

    if (!res.ok) throw new Error(`Download failed: ${res.status}`);

    return res.blob();
}

export async function addTestQuestion(
    instance: IPublicClientApplication,
    sectionId: number,
    question: TestQuestionRequest
) {
    if (!BASE_URL) return null;

    const token = await getAccessToken(instance);

    return safePost(
        `${BASE_URL}/api/courses/sections/tests/${sectionId}/questions`,
        token,
        question,
        "Failed to add test question"
    );
}

export async function getTestQuestions(
    instance: IPublicClientApplication,
    sectionId: number
): Promise<TestQuestionResponse[]> {
    if (!BASE_URL) return [];

    const token = await getAccessToken(instance);

    return safeFetch(
        `${BASE_URL}/api/courses/sections/tests/${sectionId}/questions`,
        token,
        "Failed to fetch test questions"
    );
}

export async function updateTestQuestion(
    instance: IPublicClientApplication,
    sectionId: number,
    questionId: number,
    question: TestQuestionRequest
): Promise<void> {
    if (!BASE_URL) return;

    const token = await getAccessToken(instance);

    try {
        const res = await fetch(
            `${BASE_URL}/api/courses/sections/tests/${sectionId}/questions/${questionId}`,
            {
                method: "PUT",
                headers: {
                    Authorization: `Bearer ${token}`,
                    "Content-Type": "application/json",
                },
                body: JSON.stringify(question),
            }
        );

        if (!res.ok) {
            const text = await res.text().catch(() => "");
            console.error("API error:", res.status, text);
            throw new Error("Failed to update test question");
        }
    } catch (err) {
        console.error("Network/API failure:", err);
        throw err;
    }
}

export async function deleteTestQuestion(
    instance: IPublicClientApplication,
    sectionId: number,
    questionId: number
): Promise<void> {
    if (!BASE_URL) return;

    const token = await getAccessToken(instance);

    return safeDelete(
        `${BASE_URL}/api/courses/sections/tests/${sectionId}/questions/${questionId}`,
        token,
        "Failed to delete test question"
    );
}

export async function submitQuiz(
    instance: IPublicClientApplication,
    sectionId: number,
    answers: SubmitAnswerRequest[]
): Promise<TestResultResponse> {
    if (!BASE_URL) throw new Error("Missing VITE_API_BASE_URL");

    const token = await getAccessToken(instance);

    return safePost(
        `${BASE_URL}/api/courses/sections/tests/${sectionId}/submit`,
        token,
        answers,
        "Failed to submit quiz"
    );
}

export async function getMe(
    instance: IPublicClientApplication
): Promise<UserResponse | null> {
    if (!BASE_URL) return null;
    const token = await getAccessToken(instance);
    return safeFetch(
        `${BASE_URL}/api/users/me`,
        token,
        "Failed to fetch current user"
    );
}
