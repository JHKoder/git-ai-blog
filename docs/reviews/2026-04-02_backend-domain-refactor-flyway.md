# 검토: 백엔드 도메인 리팩토링 및 Flyway 마이그레이션 도입

- **검토 일시**: 2026-04-02
- **대상 커밋**: `a6fcef6` ~ `7d553dd` (4커밋)
- **주요 변경**: 도메인 엔티티 리팩토링 (Post/PostTag/Member/AiSuggestion/SqlVizWidget), Flyway V1~V3 추가, CLAUDE.md + .claude/ 구조 도입

---

## 1순위: Backend Developer 관점

### 강점
- `Post.create()` 팩토리 메서드 + `@NoArgsConstructor(PROTECTED)` 패턴 올바름
- `markAiSuggested()` / `accept()` / `revertFromAiSuggested()` 상태 전이 메서드로 도메인 로직 캡슐화
- `normalizeTags()` 태그 정규화 로직이 Domain 레이어에 위치 — 올바른 설계
- `StreamAiSuggestionUseCase`의 `saveResult()` 분리로 리액터 스레드 @Transactional 이슈 해결
- `isDuplicate` 검사로 동일 제안 중복 저장 방지 — 멱등성 확보

### 개선점 / 리스크
- **[High]** `Member.disconnectHashnode()` 에서 null 직접 할당 — code-style.md "null 로직 포함 금지" 위반. `Optional`이나 유효 상태값으로 대체 필요
- **[High]** `StreamAiSuggestionUseCase` 내 `java.util.ArrayList`, `java.util.List`, `java.util.stream.Collectors` 를 FQN 인라인 사용 — import 누락으로 인한 임시 처리 추정. 명시적 import로 정리 필요
- **[Medium]** `Post.accept()` 메서드: `DRAFT`, `ACCEPTED`, `PUBLISHED` 상태에서도 수락 허용하는데, 상태 전이 허용 범위가 주석에만 명시됨. `PostStatus` Enum에 전이 가능 상태 매트릭스 정의 권장
- **[Medium]** `Post.updateTags()` 에서 `postTags.clear()` 후 루프로 재추가 — orphanRemoval=true라 DB 삭제 후 INSERT 발생. 변경 없을 때 불필요한 DELETE+INSERT를 유발할 수 있음
- **[Low]** `StreamAiSuggestionUseCase` 클래스 길이 약 280줄 — `parseTitle`, `parseTags`, `removeTagsLine`, `removeAiAuthorLine` 을 별도 `AiResponseParser` 유틸로 분리 고려

---

## 1순위: Frontend Developer 관점

### 강점
- `AppRouter.tsx` PrivateRoute 패턴 깔끔, `<Layout wide>` prop으로 PostDetailPage 넓은 레이아웃 지원
- OAuth 콜백에서 fragment(`#token=`) 방식으로 토큰 전달 — 서버 로그 노출 차단

### 개선점 / 리스크
- **[Medium]** `OAuthCallback` 컴포넌트 `useEffect` 의존성 배열이 비어있음 (`[]` 생략) — `setToken`, `navigate` 가 의존성에 빠져 있어 ESLint `exhaustive-deps` 경고 발생. 추가 필요
- **[Low]** `<p>로그인 처리 중...</p>` 단순 텍스트 — 토스 스타일 스피너/스켈레톤으로 교체 권장
- **[Low]** `SqlVizEmbedPage` 는 PrivateRoute 없이 공개 접근 — 의도적인지 확인 필요 (embed 공유 링크라면 OK)

---

## 2순위: DBA 관점

### 강점
- Flyway V1~V3 단계적 마이그레이션 전략 — `ddl-auto: validate` + Flyway 조합은 프로덕션 적합
- V2에서 복합 인덱스 (`member_id, status`, `member_id, created_at`) 추가 — 페이지네이션 쿼리 최적화
- `sqls_hash` (SHA-256) 기반 중복 방지 인덱스 추가 — 해시 충돌 가능성 극히 낮음

### 개선점 / 리스크
- **[High]** V1 스키마에서 `posts.member_id`, `ai_suggestions.post_id`, `ai_suggestions.member_id` 등에 **FK 제약 없음** — `post_tags`만 `REFERENCES posts(id)` 있고 나머지는 없음. 참조 무결성 미보장
- **[Medium]** `SqlVizWidget` Entity의 `@Index`는 `memberId` 단일 인덱스만 포함 — V2에서 `(member_id, sqls_hash, scenario)` 복합 인덱스를 SQL로 추가했으나 Entity 어노테이션에는 미반영 → `ddl-auto: validate` 통과 여부 확인 필요
- **[Medium]** `ai_suggestions` 테이블에 `member_id` FK 인덱스 없음 — 회원별 집계 쿼리 시 Full Table Scan 위험
- **[Low]** `repo_collect_history.ref_id`가 `VARCHAR(255)` — git SHA나 위키 슬러그는 길이 무제한일 수 있어 `TEXT`로 변경 고려

---

## 2순위: DevOps 관점

### 강점
- `application-dev.yml`에 `flyway.baseline-on-migrate: true` — 기존 DB에 Flyway 적용 시 안전한 시작점 설정
- `ddl-auto: validate` — 프로덕션에서 스키마 자동 변경 방지

### 개선점 / 리스크
- **[Medium]** `application-dev.yml`에 Jasypt 암호화된 GitHub OAuth client-id/secret 하드코딩 — `JASYPT_ENCRYPTOR_PASSWORD` 환경변수가 없으면 앱 구동 실패. 개발자 온보딩 가이드에 명시 필요
- **[Medium]** Flyway `baseline-on-migrate: true` 는 기존 DB가 있을 때만 필요 — 신규 DB에서는 V1부터 정상 적용되나, 기존 테이블이 있을 때 V1 건너뜀. 팀 내 DB 상태 공유 필요
- **[Low]** CI 파이프라인에서 Flyway 마이그레이션 유효성 검증 단계 없음 — `./gradlew flywayValidate` 추가 권장

---

## 2순위: Product Owner 관점

### 강점
- PostTag 분리로 태그 기반 검색/필터 기능 확장성 확보
- `AiSuggestion` 이력 보존으로 "거절 후 재수락" 시나리오 지원 — 사용자 편의성 향상

### 개선점 / 리스크
- **[Low]** `aiDailyLimit`, `claudeDailyLimit` 등 한도가 개별 설정인데 UI에서 이를 쉽게 관리할 수 있는지 확인 필요

---

## 3순위: QA / Tester 관점

### 강점
- `StreamAiSuggestionUseCaseTest` 존재 — 테스트 기반 있음

### 개선점 / 리스크
- **[High]** `Post.normalizeTags()` 에 대한 단위 테스트 없음 — 한글 태그, 특수문자, 30자 초과, 빈 문자열 등 엣지케이스 미검증
- **[Medium]** `Post.accept()` 의 상태 전이 허용 범위 (DRAFT/ACCEPTED/PUBLISHED → ACCEPTED) 에 대한 테스트 없음
- **[Low]** `parseTags()` / `parseTitle()` / `removeAiAuthorLine()` 파싱 메서드들이 private — 테스트 가능성을 위해 패키지 프라이빗 유틸로 분리 고려

---

## 3순위: Security 관점

### 강점
- `Member` 엔티티에서 API 키 전체를 `AesGcmEncryptionConverter` 로 암호화 저장 — 컬럼 수준 암호화
- OAuth 토큰을 `fragment(#)` 로 전달 — 서버 로그, Referer 헤더에 노출 안 됨
- Refresh Token을 `HttpOnly`, `SameSite=Strict`, `path=/api/auth` 쿠키로 제한

### 개선점 / 리스크
- **[High]** `application-dev.yml`의 Jasypt 암호문이 **버전 관리에 포함** — `JASYPT_ENCRYPTOR_PASSWORD` 가 외부에 노출되면 복호화 가능. Git history에 평문 노출 위험 없는지 확인 필요
- **[Medium]** `SqlVizWidget`에서 DB 직접 실행 금지는 `SimulationEngine` 수준에서만 보장 — 추후 새 개발자가 실수로 직접 쿼리 실행 경로를 추가할 가능성. 아키텍처 레벨에서 방어 강화 권장
- **[Low]** `refreshCookie.setSecure(request.isSecure())` — 로컬 개발(http)에서 secure=false가 되는데, 이는 의도적. 단 스테이징 환경이 http라면 보안 취약점

---

## 종합 추천

| 영역 | 상태 | 비고 |
|------|------|------|
| 도메인 설계 | 양호 | 상태 전이 매트릭스 정리 권장 |
| DB 마이그레이션 | 조건부 OK | FK 제약 추가 필요 |
| 보안 | 조건부 OK | Jasypt 키 관리 확인 필요 |
| 테스트 | 미흡 | normalizeTags 등 단위 테스트 추가 필요 |
| 프론트 | 양호 | useEffect 의존성 배열 수정 필요 |

---

## 즉시 수정 Top 3

1. **[Backend/Code-style] `Member.disconnectHashnode()` null 직접 할당 제거**  
   → `Optional` 또는 별도 "연결 해제 상태" 표현으로 대체 (null을 로직에 포함 금지 원칙)

2. **[Backend/Code-quality] `StreamAiSuggestionUseCase` inline `java.util.*` FQN 정리**  
   → `java.util.ArrayList`, `java.util.List`, `java.util.stream.Collectors` 를 import로 올리기

3. **[DB] Flyway V4 추가: 핵심 FK 제약 추가**  
   → `posts.member_id → members.id`, `ai_suggestions.post_id → posts.id` 등 참조 무결성 확보
