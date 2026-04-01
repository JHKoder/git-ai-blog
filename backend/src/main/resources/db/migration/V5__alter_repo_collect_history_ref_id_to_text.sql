-- V5: repo_collect_history.ref_id 길이 제한 제거
-- git SHA(40자), 위키 슬러그 등 가변 길이 식별자 지원을 위해 TEXT로 변경

ALTER TABLE repo_collect_history
    ALTER COLUMN ref_id TYPE TEXT;
