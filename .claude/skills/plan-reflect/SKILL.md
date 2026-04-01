---
name: plan-reflect
description: 메모를 docs/plan.md와 architecture.md에 반영하고 문서만 업데이트 (구현 금지)
version: 1.0
model: sonnet
---

# Reflect Memo Skill

사용자가 "docs/plan.md와 architecture.md에 메모 몇 개 추가했어"라고 하면:

1. 모든 메모를 정확히 읽고 반영한다.
2. plan.md와 architecture.md만 업데이트한다.
3. 코드 구현은 **절대** 하지 않는다.
4. 업데이트 완료 후 "문서 업데이트 완료. 구현은 다음 단계에서 진행"이라고만 답변한다.

CLAUDE.md와 .claude/rules/의 모든 규칙을 100% 준수한다.