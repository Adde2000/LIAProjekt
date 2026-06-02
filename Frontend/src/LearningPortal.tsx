import { type JSX, useState } from "react";
import { useStreamServiceWorker } from "./auth/useStreamServiceWorker";
import { useHasRole } from "./auth/useRoles";
import { RequireRole } from "./components/RequireRole";
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
    const isAdmin       = useHasRole("admin");
    const isCourseAdmin = useHasRole("courseAdmin");
    const isStudent     = useHasRole("student");
    const canSeeAdmin   = isAdmin || isCourseAdmin;

    // `selected` is what the user clicked; `view` is what actually renders —
    // clamped to a tab they're allowed to see. This avoids setState-in-effect.
    const [selected, setSelected] = useState<ViewKey>("courses");

    function permittedView(key: ViewKey): boolean {
        if (key === "courses") return isStudent;
        if (key === "admin")   return canSeeAdmin;
        return true;
    }

    const view: ViewKey = permittedView(selected)
        ? selected
        : isStudent ? "courses" : canSeeAdmin ? "admin" : "aiChat";

    const viewMap: Record<ViewKey, JSX.Element> = {
        courses: (
            <RequireRole role="student">
                <CoursesView />
            </RequireRole>
        ),
        quizzes: <></>,          // reserved for future QuizzesView
        admin: (
            <RequireRole role={["admin", "courseAdmin"]}>
                <AdminView />
            </RequireRole>
        ),
        aiChat: <AIChatView />,
    };

    // Hide nav tabs the user has no access to
    const visibleViews = VIEWS.filter((v) => {
        if (v.key === "courses") return isStudent;
        if (v.key === "admin")   return canSeeAdmin;
        return true;
    });

    return (
        <div className="vmv">
            <header className="vmv-header">
                <h1 className="vmv-title">Lärportal</h1>
                <p className="vmv-subtitle">Här står det mer text om man vill ha en undertitel</p>
            </header>

            <nav className="vmv-nav">
                {visibleViews.map((v) => (
                    <button
                        key={v.key}
                        className={view === v.key ? "active" : ""}
                        onClick={() => setSelected(v.key)}
                    >
                        {v.label}
                    </button>
                ))}
            </nav>

            <main>{viewMap[view]}</main>
        </div>
    );
}
