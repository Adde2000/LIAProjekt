import { PublicClientApplication, type Configuration } from "@azure/msal-browser";

const clientId = import.meta.env.VITE_CLIENT_ID;
const tenantId = import.meta.env.VITE_TENANT_ID;

if (!clientId) {
    throw new Error("Missing VITE_CLIENT_ID in .env");
}

if (!tenantId) {
    throw new Error("Missing VITE_TENANT_ID in .env");
}

const msalConfig: Configuration = {
    auth: {
        clientId,
        authority: `https://login.microsoftonline.com/${tenantId}/v2.0`,
        redirectUri: "http://localhost:5173"
    },
    cache: {
        cacheLocation: "sessionStorage"
    }
};

export const msalInstance = new PublicClientApplication(msalConfig);

// init + handle redirect response
export async function initMsal() {
    await msalInstance.initialize();

    const result = await msalInstance.handleRedirectPromise();

    if (result?.account) {
        msalInstance.setActiveAccount(result.account);
    } else {
        const accounts = msalInstance.getAllAccounts();
        if (accounts.length > 0) {
            msalInstance.setActiveAccount(accounts[0]);
        }
    }

    console.log("MSAL initialized");
}