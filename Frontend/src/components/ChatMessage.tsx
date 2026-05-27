import type { ChatMessage as ChatMessageType } from "../types/index";

interface Props {
    message: ChatMessageType;
}

export default function ChatMessage({ message }: Props) {
    const isUser = message.role === "user";

    return (
        <div className={`chat-message ${isUser ? "user" : "assistant"}`}>
            <div className="chat-bubble">
                <p>{message.content}</p>
                <span className="timestamp">{message.timestamp}</span>
            </div>
        </div>
    );
}