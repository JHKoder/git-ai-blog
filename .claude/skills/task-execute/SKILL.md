---
name: task-execute
description: plan.md의 특정 task를 완전 구현하고 문서 업데이트
version: 1.0
model: sonnet
---

# Implement Task Skill

사용자가 task를 지정하면:

- CLAUDE.md + .claude/rules/ 전체 규칙 100% 준수
- Java/React 타입 안전하게 (any/unknown 절대 금지)
- 지속적으로 typecheck 실행 (새 문제 발생 금지)
- 완료 즉시 plan.md 체크 + architecture.md 필요 시 업데이트
- 모든 변경은 git diff로 보여주기
- 모든 작업과 단계가 완료될 때까지 멈추지 않기

작업 시작 전에 "plan.md의 [우선순위 X번 task]를 구현합니다"라고 명시.