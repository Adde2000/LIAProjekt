import { useState } from "react";
import { UsersView } from "./UsersView";
import { CreateCourseView } from "./CreateCourseView";
import { ManageCoursesView } from "./ManageCoursesView.tsx";

type AdminTab = "users" | "create-course" | "manage-courses";

const ADMIN_TABS: { key: AdminTab; label: string }[] = [
    { key: "users",          label: "Användare"      },
    { key: "create-course",  label: "Ny kurs"        },
    { key: "manage-courses", label: "Hantera kurser" },
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
        </>
    );
}
