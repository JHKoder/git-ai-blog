-- V6: AI 평가 이력 테이블 생성
CREATE TABLE IF NOT EXISTS ai_evaluations
(
    id                  BIGSERIAL PRIMARY KEY,
    post_id             BIGINT    NOT NULL,
    member_id           BIGINT    NOT NULL,
    evaluation_content  TEXT      NOT NULL,
    model               VARCHAR(255) NOT NULL,
    duration_ms         BIGINT,
    created_at          TIMESTAMP NOT NULL,
    CONSTRAINT fk_ai_evaluations_post   FOREIGN KEY (post_id)   REFERENCES posts(id),
    CONSTRAINT fk_ai_evaluations_member FOREIGN KEY (member_id) REFERENCES members(id)
);

CREATE INDEX idx_ai_evaluations_post_id        ON ai_evaluations (post_id);
CREATE INDEX idx_ai_evaluations_member_created ON ai_evaluations (member_id, created_at);
