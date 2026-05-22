import { useState } from "react";
import { UsersView } from "./UsersView";
import { CreateCourseView } from "./CreateCourseView";
import { ManageCoursesView } from "./ManageCoursesView.tsx";
import { AssistantsView } from "./AssistantView";

type AdminTab = "users" | "create-course" | "manage-courses" | "assistants";

const ADMIN_TABS: { key: AdminTab; label: string }[] = [
    { key: "users",          label: "Användare"      },
    { key: "create-course",  label: "Ny kurs"        },
    { key: "manage-courses", label: "Hantera kurser" },
    { key: "assistants",     label: "AI Assistants"  },
];

export function AdminView() {
    const [tab, setTab] = useState<AdminTab>("users");

    return (
        <>
            <div className="vmv-admin-submenu">
                {ADMIN_TABS.map((t) => (
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
            {tab === "assistants" && <AssistantsView />}
        </>
    );
}
