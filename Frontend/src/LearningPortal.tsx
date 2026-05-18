import { type JSX, useState } from "react";
import type { ViewKey } from "./types";
import { VIEWS } from "./data";
import { CoursesView } from "./views/CoursesView";
import { AdminView }   from "./views/AdminView";

// CSS — one import per concern, all pulled in here
import "./styles/global.css";
import "./styles/layout.css";
import "./styles/components.css";
import "./styles/courses.css";
import "./styles/admin.css";

export default function LearningPortal() {
    const [view, setView] = useState<ViewKey>("courses");

    const viewMap: Record<ViewKey, JSX.Element> = {
        courses: <CoursesView />,
        quizzes: <></>,          // reserved for future QuizzesView
        admin:   <AdminView />,
    };

    return (
        <div className="vmv">
            <header className="vmv-header">
                <h1 className="vmv-title">Lärportal</h1>
                <p className="vmv-subtitle">Här står det mer text om man vill ha en undertitel</p>
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
