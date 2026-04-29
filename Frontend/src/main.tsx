import React from "react";
import ReactDOM from "react-dom/client";
import App from "./App";
import { msalInstance } from "./auth/msalInstance";
import { handleRedirect } from "./auth/authRedirect";

await msalInstance.initialize();
await handleRedirect(msalInstance);

ReactDOM.createRoot(document.getElementById("root")!).render(
    <React.StrictMode>
        <App msalInstance={msalInstance} />
    </React.StrictMode>
);