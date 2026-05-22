import { useCallback, useEffect, useRef, useState } from "react";
import { useMsal } from "@azure/msal-react";
import type { CourseResponse, LoadState, SectionResponse } from "../types";
import { getCourseSections, getSectionMaterials, streamMaterial } from "../api/api";
import { FetchState } from "../components/FetchState";

interface Props {
    course: CourseResponse;
    onBack: () => void;
}

function LockIcon() {
    return (
        <svg
            className="vmv-section-lock-icon"
            xmlns="http://www.w3.org/2000/svg"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            strokeWidth="2"
            strokeLinecap="round"
            strokeLinejoin="round"
            aria-label="Låst"
        >
            <rect x="3" y="11" width="18" height="11" rx="2" ry="2" />
            <path d="M7 11V7a5 5 0 0 1 10 0v4" />
        </svg>
    );
}

interface MaterialItem {
    fileId: string;
    originalName: string;
}

function extOf(filename: string): string {
    return filename.split(".").pop()?.toLowerCase() ?? "";
}

function fileIcon(ext: string) {
    if (ext === "pdf") return "📄";
    if (["mp4", "mov", "avi", "mkv"].includes(ext)) return "🎬";
    return "📎";
}

function MediaViewer({ material, onClose }: { material: MaterialItem; onClose: () => void }) {
    const { instance } = useMsal();
    const [objectUrl, setObjectUrl] = useState<string | null>(null);
    const [loadError, setLoadError] = useState<string | null>(null);
    const ext = extOf(material.originalName);
    const isVideo = ["mp4", "mov", "avi", "mkv"].includes(ext);
    const isPdf   = ext === "pdf";

    useEffect(() => {
        let url: string | null = null;
        streamMaterial(instance, material.fileId)
            .then((res) => res.blob())
            .then((blob) => {
                url = URL.createObjectURL(blob);
                setObjectUrl(url);
            })
            .catch((err) => setLoadError(err instanceof Error ? err.message : "Kunde inte ladda filen"));

        return () => { if (url) URL.revokeObjectURL(url); };
    }, [instance, material.fileId]);

    return (
        <div className="vmv-media-backdrop" onClick={onClose}>
            <div className="vmv-media-modal" onClick={(e) => e.stopPropagation()}>
                <div className="vmv-media-modal-header">
                    <span className="vmv-media-modal-title">{material.originalName}</span>
                    <button className="vmv-media-modal-close" onClick={onClose}>✕</button>
                </div>
                <div className="vmv-media-modal-body">
                    {loadError && (
                        <div className="vmv-section-material-status vmv-section-material-status--error">
                            Fel: {loadError}
                        </div>
                    )}
                    {!objectUrl && !loadError && (
                        <div className="vmv-section-material-status">Laddar…</div>
                    )}
                    {objectUrl && isVideo && (
                        <video
                            className="vmv-media-video"
                            src={objectUrl}
                            controls
                            autoPlay
                        />
                    )}
                    {objectUrl && isPdf && (
                        <iframe
                            className="vmv-media-pdf"
                            src={objectUrl}
                            title={material.originalName}
                        />
                    )}
                    {objectUrl && !isVideo && !isPdf && (
                        <div className="vmv-section-material-status">
                            Förhandsvisning ej tillgänglig.{" "}
                            <a href={objectUrl} download={material.originalName}>Ladda ned</a>
                        </div>
                    )}
                </div>
            </div>
        </div>
    );
}

function SectionItem({ section }: { section: SectionResponse }) {
    const { instance } = useMsal();
    const [expanded, setExpanded] = useState(false);
    const [materials, setMaterials] = useState<MaterialItem[]>([]);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [activeMaterial, setActiveMaterial] = useState<MaterialItem | null>(null);
    const fetchedRef = useRef(false);

    function handleToggle() {
        if (section.isLocked) return;
        setExpanded((prev) => {
            if (!prev && !fetchedRef.current) {
                fetchedRef.current = true;
                setLoading(true);
                setError(null);
                getSectionMaterials(instance, section.id)
                    .then((data) => setMaterials((data as MaterialItem[]) ?? []))
                    .catch((err) => setError(err instanceof Error ? err.message : "Kunde inte hämta material"))
                    .finally(() => setLoading(false));
            }
            return !prev;
        });
    }

    return (
        <div
            className={`vmv-section-item vmv-section-item--expandable${section.isLocked ? " vmv-section-item--locked" : ""}${expanded ? " vmv-section-item--open" : ""}`}
            aria-disabled={section.isLocked}
            title={section.isLocked ? "Slutför föregående avsnitt för att låsa upp" : undefined}
            onClick={handleToggle}
        >
            <div className="vmv-section-item-num">
                {String(section.orderIndex + 1).padStart(2, "0")}
            </div>

            <div className="vmv-section-item-body">
                <div className="vmv-section-item-title">{section.title}</div>

                {expanded && (
                    <div className="vmv-section-material-panel" onClick={(e) => e.stopPropagation()}>
                        {loading && (
                            <div className="vmv-section-material-status">Hämtar material…</div>
                        )}
                        {error && (
                            <div className="vmv-section-material-status vmv-section-material-status--error">
                                Fel: {error}
                            </div>
                        )}
                        {!loading && !error && materials.length === 0 && (
                            <div className="vmv-section-material-status">Inget material tillgängligt.</div>
                        )}
                        {!loading && !error && materials.length > 0 && (
                            <div className="vmv-section-material-list">
                                {materials.map((m) => {
                                    const ext = extOf(m.originalName);
                                    return (
                                        <div
                                            key={m.fileId}
                                            className="vmv-section-material-row vmv-section-material-row--clickable"
                                            onClick={() => setActiveMaterial(m)}
                                            title="Öppna"
                                        >
                                            <span className="vmv-section-material-icon">{fileIcon(ext)}</span>
                                            <span className="vmv-section-material-name">{m.originalName}</span>
                                            <span className="vmv-section-material-type">{ext.toUpperCase()}</span>
                                            <span className="vmv-section-material-open">↗</span>
                                        </div>
                                    );
                                })}
                            </div>
                        )}
                    </div>
                )}
                {activeMaterial && (
                    <MediaViewer material={activeMaterial} onClose={() => setActiveMaterial(null)} />
                )}
            </div>

            {section.isLocked ? (
                <div className="vmv-section-item-lock">
                    <LockIcon />
                    <span className="vmv-section-item-lock-label">Låst</span>
                </div>
            ) : (
                <span className="vmv-section-chevron">{expanded ? "▲" : "▼"}</span>
            )}
        </div>
    );
}

export function CourseSectionView({ course, onBack }: Props) {
    const { instance } = useMsal();
    const [fetchKey, setFetchKey] = useState(0);
    const [state, setState] = useState<LoadState<SectionResponse[]>>({
        data: null,
        loading: true,
        error: null,
    });

    const retry = useCallback(() => setFetchKey((k) => k + 1), []);

    useEffect(() => {
        let cancelled = false;

        getCourseSections(instance, course.id)
            .then((data) => {
                if (!cancelled)
                    setState({ data: data ?? [], loading: false, error: null });
            })
            .catch(() => {
                if (!cancelled)
                    setState({
                        data: null,
                        loading: false,
                        error: "Kunde inte hämta kursens avsnitt.",
                    });
            });

        return () => { cancelled = true; };
    }, [instance, course.id, fetchKey]);

    // Sort by orderIndex so display order always matches the server's intent
    const sorted = [...(state.data ?? [])].sort((a, b) => a.orderIndex - b.orderIndex);

    return (
        <>
            {/* ── Back button + course header ── */}
            <button className="vmv-back-btn" onClick={onBack}>
                ← Tillbaka
            </button>

            <div className="vmv-section-head">{course.title}</div>

            {course.description && (
                <p className="vmv-course-section-description">
                    {course.description}
                </p>
            )}

            <div className="vmv-course-section-meta">
                Skapad av {course.createdBy}
            </div>

            {/* ── Sections list ── */}
            <div className="vmv-section-head vmv-section-head--sub">Avsnitt</div>

            <FetchState loading={state.loading} error={state.error} onRetry={retry} />

            {!state.loading && !state.error && (
                <div className="vmv-sections">
                    {sorted.length === 0 ? (
                        <div className="vmv-empty">
                            Den här kursen har inga avsnitt ännu.
                        </div>
                    ) : (
                        sorted.map((section) => (
                            <SectionItem key={section.id} section={section} />
                        ))
                    )}
                </div>
            )}
        </>
    );
}
