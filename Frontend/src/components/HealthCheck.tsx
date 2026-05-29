import { useEffect, useState } from "react";
import type { IPublicClientApplication } from "@azure/msal-browser";
import { getHealth } from "../api/api";

export default function HealthCheck({ instance }: { instance: IPublicClientApplication }) {

    const [data, setData] = useState<any>();

    useEffect(() => {
        const run = async () => {
            try {
                const result = await getHealth(instance);
                setData(result);
            } catch (e) {
                console.error(e);
            }
        };

        run();
    }, []);

    return (
        <pre>{JSON.stringify(data, null, 2)}</pre>
    );
}