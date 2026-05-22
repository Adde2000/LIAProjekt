import type { IPublicClientApplication } from "@azure/msal-browser";
import { getAccessToken } from "../auth/getAccessToken";
import type { CourseRequest, UserResponse } from "../types";

const BASE_URL = import.meta.env.VITE_API_BASE_URL;

if (!BASE_URL) {
    console.error("Missing VITE_API_BASE_URL in environment variables");
}

async function safeFetch(url: string, token: string, errorMessage: string) {
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

        return await res.json();
    } catch (err) {
        console.error("Network/API failure:", err);
        throw err;
    }
}

async function safePost(url: string, token: string, body: unknown, errorMessage: string) {
    try {
        const res = await fetch(url, {
            method:  "POST",
            headers: {
                Authorization:  `Bearer ${token}`,
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

export async function getHealth(instance: IPublicClientApplication) {
    if (!BASE_URL) return null;

    const token = await getAccessToken(instance);

    return safeFetch(
        `${BASE_URL}/health`,
        token,
        "Failed health request"
    );
}

export async function getUsers(instance: IPublicClientApplication) {
    if (!BASE_URL) return null;

    const token = await getAccessToken(instance);

    return safeFetch(
        `${BASE_URL}/api/users/all`,
        token,
        "Failed to fetch users"
    );
}

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

export async function getMyCourses(instance: IPublicClientApplication) {
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

    const res = await fetch(`${BASE_URL}/api/courses/${courseId}`, {
        method: "DELETE",
        headers: { Authorization: `Bearer ${token}` },
    });

    if (!res.ok) {
        const text = await res.text().catch(() => "");
        console.error("API error:", res.status, text);
        throw new Error("Failed to delete course");
    }
}

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

export async function streamMaterial(
    instance: IPublicClientApplication,
    fileId: string,
    rangeHeader?: string
): Promise<Response> {
    if (!BASE_URL) throw new Error("Missing VITE_API_BASE_URL");

    const token = await getAccessToken(instance);

    const headers: Record<string, string> = {
        Authorization: `Bearer ${token}`,
    };
    if (rangeHeader) {
        headers["Range"] = rangeHeader;
    }

    const res = await fetch(
        `${BASE_URL}/api/material/stream/${encodeURIComponent(fileId)}`,
        { headers }
    );

    if (!res.ok && res.status !== 206) {
        throw new Error(`Stream request failed: ${res.status}`);
    }

    return res;
}
