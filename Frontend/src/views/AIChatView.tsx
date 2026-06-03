import { useEffect, useRef, useState } from "react";
import { useMsal } from "@azure/msal-react";

import ChatInput from "../components/ChatInput";
import ChatWindow from "../components/ChatWindow";

import {
    createAiSession,
    sendAiMessage,
    getAiMessages
} from "../api/api";

import type { ChatMessage } from "../types";

import "../styles/ai-chat.css";

interface Props {
    courseId: number;
}

export default function AIChatView({ courseId }: Props) {

    const { instance } = useMsal();

    const [messages, setMessages] =
        useState<ChatMessage[]>([]);

    const [loading, setLoading] =
        useState(false);

    const [sessionId, setSessionId] =
        useState<number | null>(null);

    const initialized = useRef(false);

    useEffect(() => {

        if (initialized.current) return;
        initialized.current = true;

        const initializeSession = async () => {

            try {

                setLoading(true);

                // =========================
                // CREATE / REUSE SESSION
                // =========================

                const createdSessionId =
                    await createAiSession(
                        instance,
                        courseId
                    );

                setSessionId(createdSessionId);

                // =========================
                // LOAD HISTORY
                // =========================

                const history =
                    await getAiMessages(
                        instance,
                        createdSessionId
                    );

                setMessages(history);

            } catch (error) {

                console.error(
                    "Failed to initialize AI session",
                    error
                );

            } finally {

                setLoading(false);
            }
        };

        initializeSession();

    }, [instance, courseId]);

    const sendMessage = async (text: string) => {

        if (!sessionId) {
            console.error("No AI session available");
            return;
        }

        const userMessage: ChatMessage = {
            id: crypto.randomUUID(),
            role: "user",
            content: text,
            timestamp: new Date().toLocaleTimeString(),
        };

        setMessages((prev) => [...prev, userMessage]);
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

            setMessages((prev) => [...prev, aiMessage]);

        } catch (error) {

            console.error("Failed to send message", error);

            const errorMessage: ChatMessage = {
                id: crypto.randomUUID(),
                role: "assistant",
                content: "Something went wrong while contacting the AI assistant.",
                timestamp: new Date().toLocaleTimeString(),
            };

            setMessages((prev) => [...prev, errorMessage]);

        } finally {

            setLoading(false);
        }
    };

    return (
        <div className="ai-chat-page">
            <div className="chat-header">
                <h1>AI Assistant</h1>
            </div>
            <ChatWindow messages={messages} loading={loading} />
            <ChatInput onSend={sendMessage} loading={loading || !sessionId} />
        </div>
    );
}