import { useCallback, useEffect, useRef, useState } from "react";
import { useMsal } from "@azure/msal-react";
import type { CourseResponse, LoadState, SectionResponse } from "../types";
import { getCourseSections, getSectionMaterials, getStreamToken, getDownloadUrl } from "../api/api";
import { FetchState } from "../components/FetchState";
import { QuizView } from "./QuizView";
import AIChatView from "./AIChatView";

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

interface ActiveStream {
    material: MaterialItem;
    streamUrl: string;
    fileId: string;
    expiresIn: number;
}

function extOf(filename: string): string {
    return filename.split(".").pop()?.toLowerCase() ?? "";
}

function fileIcon(ext: string) {
    if (ext === "pdf") return "📄";
    if (["mp4", "mov", "avi", "mkv"].includes(ext)) return "🎬";
    return "📎";
}

function SectionItem({ section, onOpen, onOpenQuiz }: { section: SectionResponse; onOpen: (s: ActiveStream) => void; onOpenQuiz: (s: SectionResponse) => void }) {
    const { instance } = useMsal();
    const [expanded, setExpanded] = useState(false);
    const [materials, setMaterials] = useState<MaterialItem[]>([]);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const fetchedRef = useRef(false);
    const [openingId, setOpeningId] = useState<string | null>(null);
    const [streamError, setStreamError] = useState<string | null>(null);

    async function openMaterial(m: MaterialItem) {
        if (openingId) return;
        setOpeningId(m.fileId);
        setStreamError(null);
        try {
            const isPdf = extOf(m.originalName) === "pdf";
            if (isPdf) {
                const downloadUrl = await getDownloadUrl(instance, m.fileId);
                const a = document.createElement("a");
                a.href = downloadUrl;
                a.download = m.originalName;
                document.body.appendChild(a);
                a.click();
                document.body.removeChild(a);
            } else {
                const result = await getStreamToken(instance, m.fileId);
                const fullUrl = `${import.meta.env.VITE_API_BASE_URL ?? ""}${result.streamUrl}`;
                onOpen({ material: m, streamUrl: fullUrl, fileId: m.fileId, expiresIn: result.expiresIn });
            }
        } catch (err) {
            setStreamError(err instanceof Error ? err.message : "Kunde inte öppna filen");
        } finally {
            setOpeningId(null);
        }
    }

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
                                            className={`vmv-section-material-row vmv-section-material-row--clickable${openingId === m.fileId ? " vmv-section-material-row--loading" : ""}`}
                                            onClick={() => openMaterial(m)}
                                            title={ext === "pdf" ? "Ladda ned" : "Öppna"}
                                        >
                                            <span className="vmv-section-material-icon">{fileIcon(ext)}</span>
                                            <span className="vmv-section-material-name">{m.originalName}</span>
                                            <span className="vmv-section-material-type">{ext.toUpperCase()}</span>
                                            <span className="vmv-section-material-open">
                                                {openingId === m.fileId ? "…" : ext === "pdf" ? "⬇" : "▶"}
                                            </span>
                                        </div>
                                    );
                                })}
                            </div>
                        )}
                        {!loading && !error && (
                            <div className="vmv-section-quiz-footer">
                                <button
                                    className="vmv-quiz-start"
                                    onClick={() => onOpenQuiz(section)}
                                >
                                    Ta quiz ↗
                                </button>
                            </div>
                        )}
                    </div>
                )}
            </div>

            {streamError && (
                <div className="vmv-section-material-status vmv-section-material-status--error" style={{ padding: "0.5rem 1rem" }}>
                    Fel: {streamError}
                </div>
            )}

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

function MediaView({ stream, onBack }: { stream: ActiveStream; onBack: () => void }) {
    const { instance } = useMsal();
    const [videoUrl, setVideoUrl] = useState(stream.streamUrl);
    const ext = extOf(stream.material.originalName);
    const isVideo = ["mp4", "mov", "avi", "mkv"].includes(ext);
    const refreshTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

    function scheduleRefresh(expiresIn: number) {
        if (refreshTimerRef.current) clearTimeout(refreshTimerRef.current);
        const delay = Math.max((expiresIn * 0.8) * 1000, 5000);
        refreshTimerRef.current = setTimeout(async () => {
            try {
                const result = await getStreamToken(instance, stream.fileId);
                setVideoUrl(`${import.meta.env.VITE_API_BASE_URL ?? ""}${result.streamUrl}`);
                scheduleRefresh(result.expiresIn);
            } catch { /* silently ignore */ }
        }, delay);
    }

    useEffect(() => {
        if (isVideo) scheduleRefresh(stream.expiresIn);
        return () => {
            if (refreshTimerRef.current) clearTimeout(refreshTimerRef.current);
        };
    }, []);

    return (
        <>
            <button className="vmv-back-btn" onClick={onBack}>
                ← Tillbaka
            </button>
            <div className="vmv-section-head">{stream.material.originalName}</div>
            <div className="vmv-media-view">
                {isVideo && (
                    <video className="vmv-media-view-video" src={videoUrl} controls autoPlay />
                )}
                {!isVideo && (
                    <div className="vmv-section-material-status">
                        <a href={stream.streamUrl} download={stream.material.originalName}>
                            Ladda ned {stream.material.originalName}
                        </a>
                    </div>
                )}
            </div>
        </>
    );
}

export function CourseSectionView({ course, onBack }: Props) {
    const { instance } = useMsal();
    const [activeStream, setActiveStream] = useState<ActiveStream | null>(null);
    const [quizSection, setQuizSection]   = useState<SectionResponse | null>(null);
    const [showChat, setShowChat]         = useState(false);
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
                    setState({ data: null, loading: false, error: "Kunde inte hämta kursens avsnitt." });
            });
        return () => { cancelled = true; };
    }, [instance, course.id, fetchKey]);

    const sorted = [...(state.data ?? [])].sort((a, b) => a.orderIndex - b.orderIndex);

    if (quizSection) {
        return <QuizView
            section={quizSection}
            onDone={() => { setFetchKey((k) => k + 1); setQuizSection(null); }}
        />;
    }

    if (activeStream) {
        return <MediaView stream={activeStream} onBack={() => setActiveStream(null)} />;
    }

    return (
        <>
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

            {/* ── AI Chat toggle ── */}
            <button
                className="vmv-quiz-start"
                onClick={() => setShowChat((prev) => !prev)}
            >
                {showChat ? "Stäng AI-assistenten" : "Öppna AI-assistenten 🤖"}
            </button>

            {showChat && <AIChatView courseId={course.id} />}

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
                            <SectionItem key={section.id} section={section} onOpen={setActiveStream} onOpenQuiz={setQuizSection} />
                        ))
                    )}
                </div>
            )}
        </>
    );
}