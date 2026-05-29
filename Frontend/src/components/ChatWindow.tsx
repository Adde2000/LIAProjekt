import type { ChatMessage as ChatMessageType } from "../types/index";
import ChatMessage from "./ChatMessage";
import TypingIndicator from "./TypingIndicator";

interface Props {
    messages: ChatMessageType[];
    loading: boolean;
}

export default function ChatWindow({ messages, loading }: Props) {
    return (
        <div className="chat-window">
            {messages.map((message) => (
                <ChatMessage key={message.id} message={message} />
            ))}

            {loading && <TypingIndicator />}
        </div>
    );
}