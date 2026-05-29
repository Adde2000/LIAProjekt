import type { Course } from "../types";
import { pad, ProgressBar, StatusBadge } from "./Shared";

export function CourseCard({ course }: { course: Course }) {
    return (
        <div className={`vmv-course ${course.status === "in-progress" ? "highlight" : ""}`}>
            <div className="vmv-course-num">{pad(course.id)}</div>
            <div className="vmv-course-title">{course.title}</div>
            <div className="vmv-course-meta">{course.sections} avsnitt</div>
            <ProgressBar value={course.progress} />
            <div className="vmv-course-footer">
                <span className="vmv-course-pct">{course.progress}%</span>
                <StatusBadge status={course.status} />
            </div>
        </div>
    );
}
