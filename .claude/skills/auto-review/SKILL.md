---
name: auto-review
description: 기능/변경사항을 1~3순위 관점에서 종합 검토하고, 결과를 docs/reviews/ 폴더에 자동 저장 + plan.md에 요약 추가
---

# Auto Multi-Perspective Review Skill (자동 기록 버전)

사용자가 제공한 기능 설명이나 코드 변경사항에 대해 아래 순서로 검토한다.

**1순위 (필수)**

- Backend Developer → .claude/perspectives/backend-developer.md
- Frontend Developer → .claude/perspectives/frontend-developer.md

**2순위**

- DBA → .claude/perspectives/dba.md
- DevOps → .claude/perspectives/devops.md
- Product Owner / 담당자 → .claude/perspectives/product-owner.md

**3순위**

- QA/Tester → .claude/perspectives/qa-tester.md
- Security → .claude/perspectives/security.md
- Designer → .claude/perspectives/designer.md

각 관점별로:

- 강점
- 개선점 또는 리스크 (High / Medium / Low 표시)
- 구체적인 개선 제안

마지막에 **종합 추천**과 **즉시 수정해야 할 Top 3**를 정리한다.

### 자동 기록 규칙 (반드시 실행)

1. 검토가 끝난 후, 결과를 `docs/reviews/` 폴더에 Markdown 파일로 저장한다.
    - 파일명 형식: `YYYY-MM-DD_짧은-제목.md` (예: 2026-04-01_sqlviz-blocked-button.md)
    - 파일 상단에 원본 요청 내용 + 검토 일시를 포함한다.

2. 저장 완료 후, `docs/plan.md`에 아래 형식으로 요약 메모를 추가한다.