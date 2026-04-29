import { useEffect, useState } from "react";
import { getHealth, type HealthResponse } from "../services/api";

function HealthCheck() {

    const [health, setHealth] =
        useState<HealthResponse | null>(null);

    useEffect(() => {

        getHealth()
            .then((data) => {
                setHealth(data);
            })
            .catch((err) => {
                console.error(err);
            });

    }, []);

    if (!health) return <p>Loading...</p>;

    return (
        <div>

            <h2>Backend Health</h2>

            <p>Status: {health.status}</p>
            <p>Service: {health.service}</p>
            <p>Timestamp: {health.timestamp}</p>

        </div>
    );
}

export default HealthCheck;