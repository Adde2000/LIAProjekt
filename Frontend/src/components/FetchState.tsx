export function FetchState({ loading, error, onRetry }: {
    loading: boolean;
    error: string | null;
    onRetry?: () => void;
}) {
    if (loading) {
        return (
            <div className="vmv-fetch-state">
                <div className="vmv-fetch-spinner" />
                <span>Hämtar...</span>
            </div>
        );
    }
    if (error) {
        return (
            <div className="vmv-fetch-state vmv-fetch-state--error">
                <span>{error}</span>
                {onRetry && (
                    <button className="vmv-quiz-start" onClick={onRetry}>
                        Försök igen ↗
                    </button>
                )}
            </div>
        );
    }
    return null;
}
