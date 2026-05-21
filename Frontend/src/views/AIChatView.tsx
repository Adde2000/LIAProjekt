import { useEffect, useState } from "react";
import { useMsal } from "@azure/msal-react";

import ChatInput from "../components/ChatInput";
import ChatWindow from "../components/ChatWindow";

import {
    createAiSession,
    sendAiMessage
} from "../api/api";

import type { ChatMessage } from "../types";

import "../styles/ai-chat.css";

export default function AIChatView() {

    const { instance } = useMsal();

    const [messages, setMessages] = useState<ChatMessage[]>([]);
    const [loading, setLoading] = useState(false);
    const [sessionId, setSessionId] = useState<number | null>(null);

    /**
     * Create AI session when component loads
     */
    useEffect(() => {

        const initializeSession = async () => {

            try {

                const data = await createAiSession(
                    instance,
                    1, // userId
                    1, // courseId
                    1  // characterId
                );

                setSessionId(data);

                console.log("AI session created:", data);

            } catch (error) {

                console.error(
                    "Failed to create AI session",
                    error
                );
            }
        };

        initializeSession();

    }, [instance]);

    /**
     * Send message to AI
     */
    const sendMessage = async (text: string) => {

        const userMessage: ChatMessage = {
            id: crypto.randomUUID(),
            role: "user",
            content: text,
            timestamp: new Date().toLocaleTimeString(),
        };

        /**
         * Show user message immediately
         */
        setMessages((prev) => [
            ...prev,
            userMessage
        ]);

        /**
         * Ensure session exists
         */
        if (!sessionId) {

            console.error("No AI session available");

            return;
        }

        setLoading(true);

        try {

            const data = await sendAiMessage(
                instance,
                sessionId,
                text
            );

            const aiMessage: ChatMessage = {
                id: crypto.randomUUID(),
                role: "assistant",
                content: data.response,
                timestamp: new Date().toLocaleTimeString(),
            };

            setMessages((prev) => [
                ...prev,
                aiMessage
            ]);

        } catch (error) {

            console.error(
                "Failed to send message",
                error
            );

        } finally {

            setLoading(false);
        }
    };

    return (
        <div className="ai-chat-page">

            <div className="chat-header">
                <h1>AI Assistant</h1>
            </div>

            <ChatWindow
                messages={messages}
                loading={loading}
            />

            <ChatInput
                onSend={sendMessage}
                loading={loading || !sessionId}
            />

        </div>
    );
}