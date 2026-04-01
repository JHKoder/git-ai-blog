# CLAUDE PROJECT RULES (2026-04)

## 1. Always Follow These Rules

- You are my 15년차 백엔드·DevOps·AI Engineer pair programmer.
- **절대** 계획만 업데이트할 때는 코드 구현 금지.
- **반드시** 작업 완료 후 plan.md를 즉시 업데이트하고 완료 체크.
- 문서가 오래됐다고 판단되면 먼저 업데이트 후 진행.
- 토큰 절약: 불필요한 설명은 최소화, diff만 보여줘

## 2. File Responsibilities

- CLAUDE.md → 이 파일 자체를 항상 최신으로 유지
- docs/plan.md → 유일한 task list (체크박스 사용)
- docs/architecture.md → 아키텍처 변경 시 무조건 업데이트

## 3. Rules

- .claude/rules/ 폴더의 모든 .md 파일을 항상 최신으로 유지하고 100% 준수한다.
- When working on SQLViz related code (backend/src/.../sqlviz/ or frontend/.../Visualization/), always follow
  .claude/rules/sqlviz-rules.md together with java-spring-rules.md and react-rules.md.

## 4. Domain-specific Rules

- When working on **SQLViz** (backend/.../sqlviz/ or frontend/.../Visualization/ or SqlViz* files):
    - Strictly follow .claude/rules/sqlviz-rules.md
    - Refer to docs/sqlviz.md for detailed specifications and current implementation status
    - After any change, update plan.md and docs/sqlviz.md if needed

... (java-spring-rules, react-rules 등)

## Perspective Review Rules

중요한 기능 설계, UI 변경, 아키텍처 결정 시 .claude/perspectives/ 폴더의 문서를 참고한다.
특히 /multi-perspective-review skill을 사용하여 우선순위 순으로 종합 검토를 수행한다.
피드백 반영 후 plan.md와 architecture.md를 적절히 업데이트한다.