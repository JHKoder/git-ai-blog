-- V4: 핵심 테이블 FK 제약 추가 (참조 무결성 보장)

-- posts → members
ALTER TABLE posts
    ADD CONSTRAINT fk_posts_member_id
        FOREIGN KEY (member_id) REFERENCES members (id);

-- ai_suggestions → posts
ALTER TABLE ai_suggestions
    ADD CONSTRAINT fk_ai_suggestions_post_id
        FOREIGN KEY (post_id) REFERENCES posts (id);

-- ai_suggestions → members
ALTER TABLE ai_suggestions
    ADD CONSTRAINT fk_ai_suggestions_member_id
        FOREIGN KEY (member_id) REFERENCES members (id);

-- ai_suggestions.member_id 인덱스 추가 (회원별 집계 쿼리 성능)
CREATE INDEX IF NOT EXISTS idx_ai_suggestions_member_id ON ai_suggestions (member_id);

-- repos → members
ALTER TABLE repos
    ADD CONSTRAINT fk_repos_member_id
        FOREIGN KEY (member_id) REFERENCES members (id);

-- repo_collect_history → repos
ALTER TABLE repo_collect_history
    ADD CONSTRAINT fk_repo_collect_history_repo_id
        FOREIGN KEY (repo_id) REFERENCES repos (id);

-- prompts → members
ALTER TABLE prompts
    ADD CONSTRAINT fk_prompts_member_id
        FOREIGN KEY (member_id) REFERENCES members (id);

-- sqlviz_widgets → members
ALTER TABLE sqlviz_widgets
    ADD CONSTRAINT fk_sqlviz_widgets_member_id
        FOREIGN KEY (member_id) REFERENCES members (id);
