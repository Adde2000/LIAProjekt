import { useState, useEffect, useRef } from "react";
import { useMsal } from "@azure/msal-react";
import type { CourseResponse, SectionResponse, LoadState } from "../types";
import { idle } from "../types";
import { getCourseSections as getSections, addCourseSection as addSection, uploadMaterial, deleteMaterial, getSectionMaterials } from "../api/api";
import { FetchState } from "./FetchState";
import { pad } from "./Shared";

// ── File helpers ──────────────────────────────────────────────────────────────

const ACCEPTED_TYPES = ".pdf,.mp4,.mov,.avi,.mkv";

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

// ── Upload dialog ─────────────────────────────────────────────────────────────

function UploadDialog({
                          onConfirm,
                          onCancel,
                          uploading,
                          uploadErr,
                      }: {
    onConfirm: (file: File, aiOnly: boolean) => void;
    onCancel: () => void;
    uploading: boolean;
    uploadErr: string | null;
}) {
    const fileInputRef = useRef<HTMLInputElement>(null);
    const [selectedFile, setSelectedFile] = useState<File | null>(null);
    const [aiOnly, setAiOnly] = useState(false);

    function handleFileChange(e: React.ChangeEvent<HTMLInputElement>) {
        setSelectedFile(e.target.files?.[0] ?? null);
    }

    function handleConfirm() {
        if (!selectedFile) return;
        onConfirm(selectedFile, aiOnly);
    }

    return (
        <div className="vmv-dialog-backdrop" onClick={onCancel}>
            <div className="vmv-dialog" onClick={(e) => e.stopPropagation()}>
                <div className="vmv-dialog-title">Ladda upp material</div>

                <div style={{ marginBottom: "1.25rem" }}>
                    <input
                        ref={fileInputRef}
                        type="file"
                        accept={ACCEPTED_TYPES}
                        style={{ display: "none" }}
                        onChange={handleFileChange}
                    />
                    <button
                        className="vmv-quiz-start"
                        onClick={() => fileInputRef.current?.click()}
                        disabled={uploading}
                    >
                        {selectedFile ? `📎 ${selectedFile.name}` : "Välj fil…"}
                    </button>
                </div>

                <label className="vmv-mgmt-ai-only-label" style={{ marginBottom: "1.5rem", display: "flex", alignItems: "center", gap: "0.5rem", fontSize: "13px", cursor: "pointer", userSelect: "none" }}>
                    <input
                        type="checkbox"
                        checked={aiOnly}
                        onChange={(e) => setAiOnly(e.target.checked)}
                        disabled={uploading}
                    />
                    Endast för AI (dölj för studenter)
                </label>

                {uploadErr && (
                    <div className="vmv-form-feedback vmv-form-feedback--error" style={{ marginBottom: "1rem" }}>
                        Fel: {uploadErr}
                    </div>
                )}

                <div className="vmv-dialog-actions">
                    <button
                        className="vmv-quiz-start"
                        onClick={onCancel}
                        disabled={uploading}
                    >
                        Avbryt
                    </button>
                    <button
                        className="vmv-quiz-start"
                        onClick={handleConfirm}
                        disabled={!selectedFile || uploading}
                    >
                        {uploading ? "Laddar upp…" : "Ladda upp ↗"}
                    </button>
                </div>
            </div>
        </div>
    );
}

// ── Section row ───────────────────────────────────────────────────────────────

function SectionRow({ section, index, onOpenQuiz }: {
    section: SectionResponse;
    index: number;
    onOpenQuiz: (s: SectionResponse) => void;
}) {
    const { instance } = useMsal();
    const fetchedRef   = useRef(false);

    const [expanded,     setExpanded]     = useState(false);
    const [materials,    setMaterials]    = useState<MaterialItem[]>([]);
    const [loadingFiles, setLoadingFiles] = useState(false);
    const [loadErr,      setLoadErr]      = useState<string | null>(null);
    const [showUpload,   setShowUpload]   = useState(false);
    const [uploading,    setUploading]    = useState(false);
    const [uploadErr,    setUploadErr]    = useState<string | null>(null);
    const [deleting,     setDeleting]     = useState<string | null>(null);
    const [deleteErr,    setDeleteErr]    = useState<string | null>(null);

    async function loadMaterials() {
        setLoadingFiles(true);
        setLoadErr(null);
        try {
            const result = await getSectionMaterials(instance, section.id) as MaterialItem[];
            setMaterials(result ?? []);
        } catch (err) {
            setLoadErr(err instanceof Error ? err.message : "Kunde inte hämta filer");
        } finally {
            setLoadingFiles(false);
        }
    }

    function handleToggle() {
        setExpanded((prev) => {
            if (!prev && !fetchedRef.current) {
                fetchedRef.current = true;
                loadMaterials();
            }
            return !prev;
        });
    }

    async function handleUploadConfirm(file: File, aiOnly: boolean) {
        setUploading(true);
        setUploadErr(null);
        try {
            const result = await uploadMaterial(instance, section.id, file, aiOnly) as MaterialItem;
            setMaterials((prev) => [...prev, result]);
            setShowUpload(false);
        } catch (err) {
            setUploadErr(err instanceof Error ? err.message : "Okänt fel vid uppladdning");
        } finally {
            setUploading(false);
        }
    }

    async function handleDelete(fileId: string) {
        setDeleting(fileId);
        setDeleteErr(null);
        try {
            await deleteMaterial(instance, fileId);
            setMaterials((prev) => prev.filter((m) => m.fileId !== fileId));
        } catch (err) {
            setDeleteErr(err instanceof Error ? err.message : "Okänt fel vid borttagning");
        } finally {
            setDeleting(null);
        }
    }

    return (
        <div className="vmv-mgmt-section-row-wrap">
            <div
                className={`vmv-mgmt-section-row vmv-mgmt-section-row--clickable ${expanded ? "expanded" : ""}`}
                onClick={handleToggle}
            >
                <span className="vmv-mgmt-section-num">{pad(index + 1)}</span>
                <span className="vmv-mgmt-section-title">{section.title}</span>
                <span className="vmv-mgmt-section-chevron">{expanded ? "▲" : "▼"}</span>
            </div>

            {expanded && (
                <div className="vmv-mgmt-material-panel">
                    {loadingFiles ? (
                        <div className="vmv-mgmt-material-empty">Hämtar filer…</div>
                    ) : loadErr ? (
                        <div className="vmv-form-feedback vmv-form-feedback--error">Fel: {loadErr}</div>
                    ) : materials.length === 0 ? (
                        <div className="vmv-mgmt-material-empty">Inget material uppladdat ännu.</div>
                    ) : (
                        <div className="vmv-mgmt-material-list">
                            {materials.map((m) => (
                                <div key={m.fileId} className="vmv-mgmt-material-row">
                                    <span className="vmv-mgmt-material-icon">{fileIcon(extOf(m.originalName))}</span>
                                    <span className="vmv-mgmt-material-name">{m.originalName}</span>
                                    <span className="vmv-mgmt-material-type">{extOf(m.originalName).toUpperCase()}</span>
                                    <button
                                        className="vmv-mgmt-material-delete"
                                        disabled={deleting === m.fileId}
                                        onClick={(e) => { e.stopPropagation(); handleDelete(m.fileId); }}
                                        title="Ta bort"
                                    >
                                        {deleting === m.fileId ? "…" : "✕"}
                                    </button>
                                </div>
                            ))}
                        </div>
                    )}

                    {deleteErr && (
                        <div className="vmv-form-feedback vmv-form-feedback--error" style={{ marginTop: "0.5rem" }}>
                            Fel: {deleteErr}
                        </div>
                    )}

                    <div className="vmv-mgmt-material-footer">
                        <button
                            className="vmv-quiz-start"
                            onClick={(e) => { e.stopPropagation(); setShowUpload(true); setUploadErr(null); }}
                        >
                            Ladda upp material ↗
                        </button>
                    </div>

                    <div className="vmv-mgmt-quiz-section" onClick={(e) => e.stopPropagation()}>
                        <div className="vmv-mgmt-quiz-header">
                            <span className="vmv-mgmt-quiz-label">Quiz</span>
                            <button
                                className="vmv-quiz-start"
                                onClick={(e) => { e.stopPropagation(); onOpenQuiz(section); }}
                            >
                                Hantera quiz ↗
                            </button>
                        </div>
                    </div>
                </div>
            )}

            {showUpload && (
                <UploadDialog
                    onConfirm={handleUploadConfirm}
                    onCancel={() => { setShowUpload(false); setUploadErr(null); }}
                    uploading={uploading}
                    uploadErr={uploadErr}
                />
            )}
        </div>
    );
}

// ── Panel ─────────────────────────────────────────────────────────────────────

export function CourseSectionsPanel({course, onOpenQuiz,}: { course: CourseResponse; onOpenQuiz: (section: SectionResponse) => void; }) {
    const { instance } = useMsal();

    const [sections, setSections]             = useState<LoadState<SectionResponse[]>>(idle());
    const [showAddSection, setShowAddSection] = useState(false);
    const [sectionTitle, setSectionTitle]     = useState("");
    const [sectionStatus, setSectionStatus]   = useState<"idle" | "submitting" | "success" | "error">("idle");
    const [sectionError, setSectionError]     = useState<string | null>(null);

    useEffect(() => {
        let cancelled = false;
        getSections(instance, course.id)
            .then((data) => {
                if (!cancelled) setSections({ data: data as SectionResponse[], loading: false, error: null });
            })
            .catch((err) => {
                if (!cancelled) setSections({ data: null, loading: false, error: (err as Error).message });
            });
        return () => { cancelled = true; };
    }, [instance, course.id]);

    async function handleAddSection() {
        const title = sectionTitle.trim();
        if (!title) return;

        setSectionStatus("submitting");
        setSectionError(null);
        try {
            const added = await addSection(instance, course.id, title) as SectionResponse;
            setSections((prev) => ({ data: [...(prev.data ?? []), added], loading: false, error: null }));
            setSectionTitle("");
            setSectionStatus("success");
            setShowAddSection(false);
        } catch (err) {
            setSectionError(err instanceof Error ? err.message : "Okänt fel");
            setSectionStatus("error");
        }
    }

    return (
        <>
            <div className="vmv-section-head" style={{ marginTop: "1.5rem" }}>
                Avsnitt ({sections.data?.length ?? "–"})
            </div>

            <FetchState loading={sections.loading} error={sections.error} />

            {sections.data && (
                <>
                    {sections.data.length === 0 ? (
                        <div className="vmv-empty" style={{ border: "1px solid var(--color-border-tertiary)" }}>
                            Inga avsnitt tillagda ännu.
                        </div>
                    ) : (
                        <div className="vmv-mgmt-section-list">
                            {sections.data.map((s, i) => (
                                <SectionRow key={s.id} section={s} index={i} onOpenQuiz={onOpenQuiz} />
                            ))}
                        </div>
                    )}

                    <div className="vmv-mgmt-actions">
                        <button
                            className="vmv-quiz-start"
                            onClick={() => { setShowAddSection((p) => !p); setSectionTitle(""); setSectionStatus("idle"); }}
                        >
                            {showAddSection ? "Avbryt" : "Lägg till avsnitt ↗"}
                        </button>
                        {sectionStatus === "success" && (
                            <span className="vmv-form-feedback vmv-form-feedback--success">✓ Avsnitt tillagt.</span>
                        )}
                        {sectionStatus === "error" && (
                            <span className="vmv-form-feedback vmv-form-feedback--error">Fel: {sectionError}</span>
                        )}
                    </div>

                    {showAddSection && (
                        <div className="vmv-mgmt-add-panel">
                            <div className="vmv-section-head">Nytt avsnitt</div>
                            <div className="vmv-mgmt-section-form">
                                <input
                                    className="vmv-mgmt-section-input"
                                    type="text"
                                    placeholder="Avsnittets titel..."
                                    value={sectionTitle}
                                    onChange={(e) => setSectionTitle(e.target.value)}
                                    onKeyDown={(e) => { if (e.key === "Enter") handleAddSection(); }}
                                    autoFocus
                                />
                                <button
                                    className="vmv-quiz-start"
                                    onClick={handleAddSection}
                                    disabled={!sectionTitle.trim() || sectionStatus === "submitting"}
                                >
                                    {sectionStatus === "submitting" ? "Sparar..." : "Lägg till ↗"}
                                </button>
                            </div>
                        </div>
                    )}
                </>
            )}
        </>
    );
}