import { type JSX, useState } from "react";
import { useStreamServiceWorker } from "./auth/useStreamServiceWorker";
import type { ViewKey } from "./types";
import { VIEWS } from "./data";
import { CoursesView } from "./views/CoursesView";
import { AdminView }   from "./views/admin/AdminView";
import AIChatView from "./views/AIChatView";

// CSS — one import per concern, all pulled in here
import "./styles/global.css";
import "./styles/layout.css";
import "./styles/components.css";
import "./styles/courses.css";
import "./styles/admin-layout.css";
import "./styles/admin-users.css";
import "./styles/admin-courses.css";
import "./styles/admin-forms.css";
import "./styles/confirm-dialog.css";
import "./styles/ai-chat.css";

export default function LearningPortal() {
    useStreamServiceWorker();
    const [view, setView] = useState<ViewKey>("courses");

    const viewMap: Record<ViewKey, JSX.Element> = {
        courses: <CoursesView />,
        quizzes: <></>,          // reserved for future QuizzesView
        admin:   <AdminView />,
        aiChat: <AIChatView />,
    };

    return (
        <div className="vmv">
            <header className="vmv-header">
                <h1 className="vmv-title">Vinkelboda Mekaniska Verkstad</h1>
                <p className="vmv-subtitle">GRUNDAT 1932 • KVALITET SEDAN STARTEN</p>
            </header>

            <nav className="vmv-nav">
                {VIEWS.map((v) => (
                    <button
                        key={v.key}
                        className={view === v.key ? "active" : ""}
                        onClick={() => setView(v.key)}
                    >
                        {v.label}
                    </button>
                ))}
            </nav>

            <main>{viewMap[view]}</main>
        </div>
    );
}
