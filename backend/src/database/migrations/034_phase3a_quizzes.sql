-- Phase 3A: "How well do you know me?" quizzes. One person writes questions and marks the right option; the partner answers once.
CREATE TABLE couple_quizzes (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id UUID NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    author_id       UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title           TEXT NOT NULL,
    questions       JSONB NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX couple_quizzes_conversation_idx ON couple_quizzes (conversation_id, created_at);

CREATE TABLE couple_quiz_results (
    quiz_id    UUID NOT NULL REFERENCES couple_quizzes(id) ON DELETE CASCADE,
    user_id    UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    answers    JSONB NOT NULL,
    score      SMALLINT NOT NULL,
    total      SMALLINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (quiz_id, user_id)
);
