import "../styles/confirm-dialog.css";

export function ConfirmDialog({
    title,
    message,
    confirmLabel,
    onConfirm,
    onCancel,
    danger = false,
    disabled = false,
}: {
    title: string;
    message: string;
    confirmLabel: string;
    onConfirm: () => void;
    onCancel: () => void;
    danger?: boolean;
    disabled?: boolean;
}) {
    return (
        <div className="vmv-dialog-backdrop" onClick={onCancel}>
            <div className="vmv-dialog" onClick={(e) => e.stopPropagation()}>
                <div className="vmv-dialog-title">{title}</div>
                <div className="vmv-dialog-message">{message}</div>
                <div className="vmv-dialog-actions">
                    <button
                        className="vmv-quiz-start"
                        onClick={onCancel}
                        disabled={disabled}
                    >
                        Avbryt
                    </button>
                    <button
                        className={`vmv-quiz-start ${danger ? "vmv-quiz-start--danger" : ""}`}
                        onClick={onConfirm}
                        disabled={disabled}
                    >
                        {disabled ? "Tar bort..." : confirmLabel}
                    </button>
                </div>
            </div>
        </div>
    );
}
