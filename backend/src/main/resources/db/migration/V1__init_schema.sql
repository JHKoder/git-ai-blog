-- V1: 초기 스키마 생성
-- 기준: 현재 Entity 구조 (2026-04-01)

CREATE TABLE IF NOT EXISTS members
(
    id                      BIGSERIAL PRIMARY KEY,
    github_id               VARCHAR(255) NOT NULL UNIQUE,
    username                VARCHAR(255) NOT NULL,
    avatar_url              VARCHAR(255),
    hashnode_token          TEXT,
    hashnode_publication_id TEXT,
    hashnode_tags           TEXT,
    claude_api_key          TEXT,
    grok_api_key            TEXT,
    gpt_api_key             TEXT,
    gemini_api_key          TEXT,
    github_token            TEXT,
    ai_daily_limit          INTEGER,
    claude_daily_limit      INTEGER,
    grok_daily_limit        INTEGER,
    gpt_daily_limit         INTEGER,
    gemini_daily_limit      INTEGER,
    created_at              TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS posts
(
    id           BIGSERIAL PRIMARY KEY,
    member_id    BIGINT       NOT NULL,
    title        VARCHAR(255) NOT NULL,
    content      TEXT         NOT NULL,
    content_type VARCHAR(50)  NOT NULL,
    status       VARCHAR(50)  NOT NULL,
    hashnode_id  VARCHAR(255),
    hashnode_url VARCHAR(255),
    view_count   INTEGER      NOT NULL DEFAULT 0,
    created_at   TIMESTAMP    NOT NULL,
    updated_at   TIMESTAMP
);

CREATE TABLE IF NOT EXISTS post_tags
(
    id      BIGSERIAL PRIMARY KEY,
    post_id BIGINT       NOT NULL REFERENCES posts (id),
    tag     VARCHAR(255) NOT NULL
);

CREATE TABLE IF NOT EXISTS ai_suggestions
(
    id                BIGSERIAL PRIMARY KEY,
    post_id           BIGINT       NOT NULL,
    member_id         BIGINT       NOT NULL,
    suggested_content TEXT         NOT NULL,
    suggested_title   TEXT,
    model             VARCHAR(255) NOT NULL,
    extra_prompt      TEXT,
    duration_ms       BIGINT,
    suggested_tags    TEXT,
    created_at        TIMESTAMP    NOT NULL
);

CREATE TABLE IF NOT EXISTS repos
(
    id           BIGSERIAL PRIMARY KEY,
    member_id    BIGINT       NOT NULL,
    owner        VARCHAR(255) NOT NULL,
    repo_name    VARCHAR(255) NOT NULL,
    collect_type VARCHAR(50)  NOT NULL,
    webhook_id   BIGINT,
    wiki_last_sha VARCHAR(255),
    created_at   TIMESTAMP    NOT NULL
);

CREATE TABLE IF NOT EXISTS repo_collect_history
(
    id           BIGSERIAL PRIMARY KEY,
    repo_id      BIGINT      NOT NULL,
    ref_type     VARCHAR(50) NOT NULL,
    ref_id       VARCHAR(255) NOT NULL,
    post_id      BIGINT,
    collected_at TIMESTAMP   NOT NULL,
    CONSTRAINT uq_repo_collect_history UNIQUE (repo_id, ref_type, ref_id)
);

CREATE TABLE IF NOT EXISTS prompts
(
    id          BIGSERIAL PRIMARY KEY,
    member_id   BIGINT       NOT NULL,
    title       VARCHAR(255) NOT NULL,
    content     TEXT         NOT NULL,
    meta_prompt TEXT,
    usage_count INTEGER      NOT NULL DEFAULT 0,
    is_public   BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMP    NOT NULL,
    updated_at  TIMESTAMP
);

CREATE TABLE IF NOT EXISTS sqlviz_widgets
(
    id              BIGSERIAL PRIMARY KEY,
    member_id       BIGINT       NOT NULL,
    title           VARCHAR(100) NOT NULL,
    sqls_json       TEXT         NOT NULL,
    sqls_hash       VARCHAR(64),
    scenario        VARCHAR(50)  NOT NULL,
    isolation_level VARCHAR(50)  NOT NULL,
    simulation_json TEXT,
    created_at      TIMESTAMP    NOT NULL
);
