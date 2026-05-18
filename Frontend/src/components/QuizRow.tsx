import type { Quiz } from "../types";
import { pad } from "./Shared";

export function QuizRow({ quiz, index }: { quiz: Quiz; index: number }) {
    return (
        <div className="vmv-quiz">
            <div className="vmv-quiz-num">{pad(index + 1)}</div>
            <div className="vmv-quiz-body">
                <div className="vmv-quiz-title">{quiz.title}</div>
                <div className="vmv-quiz-course">{quiz.course}</div>
            </div>
            {quiz.done ? (
                <div className="vmv-quiz-score">{quiz.score}</div>
            ) : (
                <button className="vmv-quiz-start">Start ↗</button>
            )}
        </div>
    );
}
