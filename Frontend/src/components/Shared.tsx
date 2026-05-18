import type { Status } from "../types";

export function pad(n: number): string {
    return n < 10 ? `0${n}` : `${n}`;
}

export function StatusBadge({ status }: { status: Status }) {
    const map: Record<Status, { cls: string; label: string }> = {
        "completed":   { cls: "vmv-status vmv-status--done", label: "✓ Klar"        },
        "in-progress": { cls: "vmv-status vmv-status--wip",  label: "Pågående"      },
        "not-started": { cls: "vmv-status vmv-status--todo", label: "Inte startad"  },
    };
    const { cls, label } = map[status];
    return <span className={cls}>{label}</span>;
}

export function ProgressBar({ value }: { value: number }) {
    return (
        <div className="vmv-progress-bg">
            <div className="vmv-progress-fill" style={{ width: `${value}%` }} />
        </div>
    );
}
