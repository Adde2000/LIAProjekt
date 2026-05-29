import { useEffect } from "react";
import { useMsal } from "@azure/msal-react";
import { getAccessToken } from "./getAccessToken";

/**
 * Registers the stream service worker and posts a fresh token to it
 * whenever the hook mounts or the MSAL instance changes.
 * Mount this once near the root of the app.
 */
export function useStreamServiceWorker() {
    const { instance } = useMsal();

    useEffect(() => {
        if (!("serviceWorker" in navigator)) return;

        let registration: ServiceWorkerRegistration | null = null;

        async function setup() {
            try {
                registration = await navigator.serviceWorker.register(
                    "/stream-sw.js",
                    { scope: "/" }
                );

                await navigator.serviceWorker.ready;
                await pushToken();
            } catch (err) {
                console.warn("Stream SW registration failed:", err);
            }
        }

        async function pushToken() {
            const target =
                navigator.serviceWorker.controller ??
                registration?.active;

            if (!target) return;

            try {
                const token = await getAccessToken(instance);
                target.postMessage({ type: "SET_TOKEN", token });
            } catch {
                // Not logged in yet — token will be pushed on next call
            }
        }

        setup();

        // Refresh the token in the SW every 10 minutes so it never expires mid-stream
        const interval = setInterval(pushToken, 10 * 60 * 1000);

        return () => clearInterval(interval);
    }, [instance]);
}
