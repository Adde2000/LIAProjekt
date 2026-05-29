/**
 * stream-sw.js
 * Service worker that intercepts /api/material/stream/* requests and injects
 * the Authorization header, enabling native browser range-request streaming.
 *
 * The app posts the current access token via postMessage whenever it changes.
 */

let accessToken = null;

self.addEventListener("message", (event) => {
    if (event.data?.type === "SET_TOKEN") {
        accessToken = event.data.token;
    }
});

self.addEventListener("fetch", (event) => {
    const url = new URL(event.request.url);

    if (!url.pathname.includes("/api/material/stream/")) return;
    if (!accessToken) return;

    const modifiedRequest = new Request(event.request, {
        headers: new Headers({
            ...Object.fromEntries(event.request.headers.entries()),
            Authorization: `Bearer ${accessToken}`,
        }),
    });

    event.respondWith(fetch(modifiedRequest));
});
