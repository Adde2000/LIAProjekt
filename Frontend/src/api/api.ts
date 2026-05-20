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
