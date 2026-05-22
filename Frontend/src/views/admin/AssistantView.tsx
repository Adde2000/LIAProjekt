import { useEffect, useState } from "react";
import { useMsal } from "@azure/msal-react";

import { getAssistants } from "../../api/api";

import type { AssistantAdminResponse } from "../../types";

export function AssistantsView() {

    const { instance } = useMsal();

    const [assistants, setAssistants] = useState<AssistantAdminResponse[]>([]);
    const [loading, setLoading] = useState(true);

    useEffect(() => {

        const loadAssistants = async () => {

            try {

                const data = await getAssistants(instance);

                setAssistants(data);

            } catch (error) {

                console.error(
                    "Failed to load assistants",
                    error
                );

            } finally {

                setLoading(false);
            }
        };

        loadAssistants();

    }, [instance]);

    if (loading) {
        return <p>Laddar assistants...</p>;
    }

    return (
        <div>

            <h2>AI Assistants</h2>

            <div className="assistant-grid">

                {assistants.map((assistant) => (

                    <div
                        key={assistant.id}
                        className="assistant-card"
                    >

                        <h3>{assistant.name}</h3>

                        <p>{assistant.description}</p>

                        <small>{assistant.id}</small>

                    </div>
                ))}

            </div>

        </div>
    );
}