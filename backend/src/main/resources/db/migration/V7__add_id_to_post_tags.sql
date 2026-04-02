-- V7: post_tags 테이블에 id 컬럼 추가
-- 기존 prod DB에 id 컬럼이 없어 Hibernate 스키마 검증 실패하는 문제 수정
-- PostTag 엔티티의 @Id 필드와 일치시킨다

ALTER TABLE post_tags ADD COLUMN IF NOT EXISTS id BIGSERIAL PRIMARY KEY;
