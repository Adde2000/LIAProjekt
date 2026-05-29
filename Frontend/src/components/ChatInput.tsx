import { useState } from "react";

interface Props {
    onSend: (message: string) => void;
    loading: boolean;
}

export default function ChatInput({ onSend, loading }: Props) {
    const [input, setInput] = useState("");

    const handleSend = () => {
        if (!input.trim()) return;

        onSend(input);
        setInput("");
    };

    return (
        <div className="chat-input-container">
            <input
                type="text"
                placeholder="Skriv ett meddelande..."
                value={input}
                onChange={(e) => setInput(e.target.value)}
                onKeyDown={(e) => e.key === "Enter" && handleSend()}
            />

            <button onClick={handleSend} disabled={loading}>
                {loading ? "..." : "Skicka"}
            </button>
        </div>
    );
}