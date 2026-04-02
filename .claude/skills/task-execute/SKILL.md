---
name: task-execute
description: plan.md의 특정 task를 완전 구현하고 문서 업데이트
version: 1.0
model: sonnet
---

# task-execute Skill

사용자가 지정한 plan.md task를 효율적으로 완전 구현한다.

- 활성화된 CLAUDE.md와 rules만 100% 준수
- Java/React 타입 안전성 최우선 (any/unknown 절대 금지)
- 코드 변경 후 즉시 typecheck 수행하고 에러 즉시 수정
- 완료 즉시 plan.md 체크박스 처리 + 필요 시 architecture.md 업데이트
- 모든 코드 변경은 git diff로 제시 (신규 파일은 전체 코드)
- 작업 시작 전에 "**plan.md의 [우선순위 X번 task]를 구현합니다**"라고 명시
- 불필요한 설명과 반복은 최소화. 실질적인 코드와 diff 중심으로 진행
- 한 번에 너무 길어지지 않도록 단계별로 명확히 끊어서 진행