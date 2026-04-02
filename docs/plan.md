# AI Blog Automation — 작업 체크리스트

**최종 수정:** 2026-04-02

---

## 진행중 작업 (항상 최상단에 유지)

현재 진행중인 작업 없음.

---

## 다음 작업 후보

### 긴급 — prod 배포 블로커

- [ ] **prod Flyway V6 누락 문제 해결**
  - 증상: prod 실행 시 `Schema validation: missing table [ai_evaluations]` 오류
  - 원인: Supabase prod DB에 V6 마이그레이션이 적용되지 않은 상태
  - 해결 방향:
    1. Supabase prod DB에 V6 SQL 수동 실행 (`docs/db/V6__create_ai_evaluations.sql`)
    2. 또는 prod 배포 파이프라인에서 Flyway migrate 자동 실행 확인
    3. Flyway 적용 후 prod 재기동 검증 테스트 추가 (`FlywayMigrationTest`)

### 이슈로 보류

- [ ] REST Docs Spring Boot 4 호환 지원 (라이브러리 출시 후 구현 예정)

---

## 최근 완료 작업 (최근 2주 이내)

**2026-04-02**

- 로그인 테스트 3종 추가 (JwtProviderTest, MockLoginControllerTest, OAuth2SuccessHandlerTest, AuthControllerTest)
- OAuth2SuccessHandler `#token=` → `?token=` 수정 (프론트엔드 OAuthCallback과 일관성)
- PromptBuilder 중복 규칙 제거 + ContentType별 규칙 경량화 (~50% 토큰 축소)
- AI 평가 이력 저장 (`AiEvaluation` 엔티티 + Flyway V6 + 스트리밍 완료 후 DB 저장)
- 태그 기반 포스트 필터링 UI / 다크모드 — 이미 구현되어 있음 확인

- SSE 스트리밍 System Prompt 캐싱 적용  
  → Claude 라우팅 시 system/user 분리 + cache_control: ephemeral, TTL 5분
- 토큰 최적화 3종 적용  
  → `CONTENT_MAX_CHARS` 600K→3K / Haiku 임계값 1K→3K / 평가 샘플링 HEAD 2K+TAIL 500
- 프로파일별 ddl-auto 분리 (local=create, dev=update, prod=validate+Flyway)
- 코드 품질 대규모 개선 및 Flyway V4/V5 마이그레이션  
  → 상세: [`reviews/2026-04-02_backend-domain-refactor-flyway.md`](reviews/2026-04-02_backend-domain-refactor-flyway.md)
- AI PromptBuilder 4단계 리뉴얼 + Haiku 자동 라우팅
- PostStatus 상태 전이 매트릭스 + 태그 정규화 로직 개선
- SqlVizWidget 복합 인덱스 추가 및 중복 감지 로직
- OAuthCallback useEffect 최적화 + 토스 스타일 스피너 적용

**2026-03-30 ~ 2026-04-01**

- SQL Visualization Widget v2 완성 (재생 애니메이션, BLOCKED 수동 진행, TX별 SQL 표시, embed 다크모드)
- AI 사용량 제한 + SSE 스트리밍 + Prompt Caching 적용
- Flyway 도입 및 DB 인덱스 대규모 정리
- ElementCollection N+1 문제 해결 (`PostTag` 별도 엔티티 분리)

---

## 아카이브 & 참고 문서

- **전체 완료 이력**: `docs/reviews/` 폴더 참고
- **DB 개선 이력**: [`research.md`](research.md)
- **SQLViz 상세 설계**: [`sqlviz.md`](sqlviz.md)
- **인프라/배포**: [`infra.md`](infra.md)

---

## 개발 규칙

- 새로운 작업 시작 시 **진행중 작업** 섹션에 먼저 기록
- 완료 즉시 **진행중 작업**에서 제거 + **최근 완료 작업**으로 이동
- 완료된 작업들은 모두 테스트 까지 작업되야한다. (문서에서 유지보수가 아닌 테스트에서 유지보수를 의미 )
- 완료된 대규모 작업의 상세는 `reviews/`에 기록
- plan.md는 **현재 진행 상황을 한눈에 파악**하는 용도로만 유지
