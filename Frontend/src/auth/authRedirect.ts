import type { IPublicClientApplication } from "@azure/msal-browser";

export function loginRedirect(instance: IPublicClientApplication) {
    return instance.loginRedirect({
        scopes: [import.meta.env.VITE_API_SCOPE]
    });
}

export async function handleRedirect(instance: IPublicClientApplication) {
    const result = await instance.handleRedirectPromise();

    if (result?.account) {
        instance.setActiveAccount(result.account);
    }

    const accounts = instance.getAllAccounts();
    if (accounts.length > 0) {
        instance.setActiveAccount(accounts[0]);
    }
}