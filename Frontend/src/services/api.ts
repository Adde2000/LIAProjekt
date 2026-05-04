import type { IPublicClientApplication } from "@azure/msal-browser";
import { getAccessToken } from "../auth/getAccessToken";

const BASE_URL = "http://localhost:8080";

export async function getHealth(instance: IPublicClientApplication) {

    const token = await getAccessToken(instance);

    const res = await fetch(`${BASE_URL}/health`, {
        headers: {
            Authorization: `Bearer ${token}`
        }
    });

    if (!res.ok) throw new Error("Failed health");

    return res.json();
}

export async function getGraphUsers(instance: IPublicClientApplication) {

    const token = await getAccessToken(instance);

    const res = await fetch(`${BASE_URL}/graph/users`, {
        headers: {
            Authorization: `Bearer ${token}`
        }
    });

    if (!res.ok) throw new Error("Failed graph users");

    return res.json();
}