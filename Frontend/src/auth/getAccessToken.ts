import type { IPublicClientApplication } from "@azure/msal-browser";

export async function getAccessToken(instance: IPublicClientApplication) {

    const account =
        instance.getActiveAccount() ??
        instance.getAllAccounts()[0];

    if (!account) {
        throw new Error("No logged in user");
    }

    const scope = import.meta.env.VITE_API_SCOPE;

    if (!scope) {
        throw new Error("Missing VITE_API_SCOPE in .env");
    }

    const response = await instance.acquireTokenSilent({
        account,
        scopes: [scope]
    });

    return response.accessToken;
}