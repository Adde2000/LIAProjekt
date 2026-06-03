import { useState } from "react";
import { UsersView } from "./UsersView";
import { CreateCourseView } from "./CreateCourseView";
import { ManageCoursesView } from "./ManageCoursesView.tsx";
import { AssistantsView } from "./AssistantView";
import { useHasRole } from "../../auth/useRoles";

type AdminTab = "users" | "create-course" | "manage-courses" | "assistants";

const ADMIN_TABS: { key: AdminTab; label: string; adminOnly?: boolean }[] = [
    { key: "users",          label: "Användare",      adminOnly: true  },
    { key: "create-course",  label: "Ny kurs",        adminOnly: true  },
    { key: "manage-courses", label: "Hantera kurser", adminOnly: false },
    { key: "assistants",     label: "AI Assistants",  adminOnly: true  },
];

export function AdminView() {
    const isAdmin = useHasRole("admin");

    const visibleTabs = ADMIN_TABS.filter((t) => isAdmin || !t.adminOnly);

    const [tab, setTab] = useState<AdminTab>(
        isAdmin ? "users" : "manage-courses"
    );

    return (
        <>
            <div className="vmv-admin-submenu">
                {visibleTabs.map((t) => (
                    <button
                        key={t.key}
                        className={`vmv-admin-subtab ${tab === t.key ? "active" : ""}`}
                        onClick={() => setTab(t.key)}
                    >
                        {t.label}
                    </button>
                ))}
            </div>

            {tab === "users"          && <UsersView />}
            {tab === "create-course"  && <CreateCourseView />}
            {tab === "manage-courses" && <ManageCoursesView />}
            {tab === "assistants"     && <AssistantsView />}
        </>
    );
}
