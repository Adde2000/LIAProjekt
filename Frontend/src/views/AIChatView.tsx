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

export default function AIChatView() {

    const { instance } = useMsal();

    const [messages, setMessages] =
        useState<ChatMessage[]>([]);

    const [loading, setLoading] =
        useState(false);

    const [sessionId, setSessionId] =
        useState<number | null>(null);

    /**
     * Prevent duplicate session creation
     * caused by React StrictMode
     */
    const initialized = useRef(false);

    /**
     * Create or reuse AI session
     * and load history
     */
    useEffect(() => {

        /**
         * Prevent double execution
         */
        if (initialized.current) {
            return;
        }

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
                        1, // userId
                        1  // courseId
                    );

                setSessionId(createdSessionId);

                console.log(
                    "AI session ready:",
                    createdSessionId
                );

                // =========================
                // LOAD HISTORY
                // =========================

                const history =
                    await getAiMessages(
                        instance,
                        createdSessionId
                    );

                /**
                 * Backend already returns history
                 * from Azure thread
                 */
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

    }, [instance]);

    /**
     * Send message to AI
     */
    const sendMessage = async (
        text: string
    ) => {

        /**
         * Ensure session exists
         */
        if (!sessionId) {

            console.error(
                "No AI session available"
            );

            return;
        }

        // =========================
        // USER MESSAGE
        // =========================

        const userMessage: ChatMessage = {
            id: crypto.randomUUID(),
            role: "user",
            content: text,
            timestamp: new Date()
                .toLocaleTimeString(),
        };

        /**
         * Show immediately
         */
        setMessages((prev) => [
            ...prev,
            userMessage
        ]);

        setLoading(true);

        try {

            // =========================
            // SEND TO BACKEND
            // =========================

            const data =
                await sendAiMessage(
                    instance,
                    sessionId,
                    text
                );

            // =========================
            // AI RESPONSE
            // =========================

            const aiMessage: ChatMessage = {
                id: crypto.randomUUID(),
                role: "assistant",
                content: data.response,
                timestamp: new Date()
                    .toLocaleTimeString(),
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

            // =========================
            // ERROR MESSAGE
            // =========================

            const errorMessage: ChatMessage = {
                id: crypto.randomUUID(),
                role: "assistant",
                content:
                    "Something went wrong while contacting the AI assistant.",
                timestamp: new Date()
                    .toLocaleTimeString(),
            };

            setMessages((prev) => [
                ...prev,
                errorMessage
            ]);

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