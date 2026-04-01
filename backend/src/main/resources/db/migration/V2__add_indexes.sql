-- V2: 성능 인덱스 추가 (DBA 분석 기반)

-- posts
CREATE INDEX IF NOT EXISTS idx_posts_member_id ON posts (member_id);
CREATE INDEX IF NOT EXISTS idx_posts_member_status ON posts (member_id, status);
CREATE INDEX IF NOT EXISTS idx_posts_member_created ON posts (member_id, created_at);

-- post_tags
CREATE INDEX IF NOT EXISTS idx_post_tags_post_id ON post_tags (post_id);
CREATE INDEX IF NOT EXISTS idx_post_tags_tag ON post_tags (tag);

-- ai_suggestions
CREATE INDEX IF NOT EXISTS idx_ai_suggestions_post_id ON ai_suggestions (post_id);
CREATE INDEX IF NOT EXISTS idx_ai_suggestions_post_created ON ai_suggestions (post_id, created_at);
CREATE INDEX IF NOT EXISTS idx_ai_suggestions_model ON ai_suggestions (model);

-- repos
CREATE INDEX IF NOT EXISTS idx_repos_member_id ON repos (member_id);

-- prompts
CREATE INDEX IF NOT EXISTS idx_prompts_member_usage ON prompts (member_id, usage_count);
CREATE INDEX IF NOT EXISTS idx_prompts_public_usage ON prompts (is_public, usage_count);

-- sqlviz_widgets
CREATE INDEX IF NOT EXISTS idx_sqlviz_member_id ON sqlviz_widgets (member_id);
CREATE INDEX IF NOT EXISTS idx_sqlviz_member_hash_scenario ON sqlviz_widgets (member_id, sqls_hash, scenario);
