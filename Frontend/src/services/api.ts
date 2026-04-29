export type HealthResponse = {
    status: string;
    service: string;
    timestamp: string;
};
const BASE_URL = "http://localhost:8080";

/**
 * Hämtar health status från backend
 * Spring Boot endpoint: /health
 */
export async function getHealth(): Promise<HealthResponse> {

    const response =
        await fetch(`${BASE_URL}/health`);

    if (!response.ok) {
        throw new Error("Failed to fetch health");
    }

    return response.json();
}