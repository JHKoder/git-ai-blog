-- V3: members 암호화 컬럼 길이를 TEXT로 통일
-- AES-256-GCM Base64 인코딩 오버헤드를 고려해 VARCHAR 제한 대신 TEXT 사용

ALTER TABLE members
    ALTER COLUMN hashnode_token          TYPE TEXT,
    ALTER COLUMN hashnode_publication_id TYPE TEXT,
    ALTER COLUMN claude_api_key          TYPE TEXT,
    ALTER COLUMN grok_api_key            TYPE TEXT,
    ALTER COLUMN gpt_api_key             TYPE TEXT,
    ALTER COLUMN gemini_api_key          TYPE TEXT,
    ALTER COLUMN github_token            TYPE TEXT;
