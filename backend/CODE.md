# 코드 컨벤션 · 규칙 · 테스트

---

## 코드 컨벤션

- OOP 원칙 준수
- 클린 아키텍처 + 클린 코드
- DDD 계층 분리: `Presentation / Application / Domain / Infrastructure`
- Google Java Style, 들여쓰기 스페이스 4칸
- 네이밍: 의도를 드러내는 풀네임 (축약 금지)
- 오류 코드: Enum 관리, 코드·메시지 분리
- 로그: INFO/WARN/ERROR 레벨 구분

---

## 코드 규칙

| 규칙 | 설명 |
|------|------|
| 단일 들여쓰기 | 메서드 내 들여쓰기 한 단계만 |
| else 금지 | `else` 예약어 사용 금지 — early return 사용 |
| null 로직 금지 | `null` 직접 비교 엄격히 금지 — `Optional` 사용 |
| 원시값 포장 | 모든 원시값과 문자열을 포장 |
| 일급 컬렉션 | 컬렉션 필드는 일급 컬렉션으로 감싸기 |
| 한 줄 한 점 | 한 줄에 `.` 하나만 (체이닝 분리) |
| 엔티티 작게 | 모든 엔티티(클래스)는 50줄 이하 목표 |
| getter/setter 금지 | 외부에 상태 직접 노출 금지 — 도메인 메서드로만 |
| 메서드 13줄 이하 | 메서드 본문 13줄 초과 금지 |
| 매직 상수 금지 | 숫자·문자열 리터럴은 반드시 상수 또는 Enum |
| private 순서 | **모든 private 메서드는 public 메서드 아래 위치** |

---

## 중요 규칙

- 중복 코드 제거 → 공통 모듈 분리
- API Request: `@Valid`로 1차 유효성 검사
- 비즈니스 규칙 검증: Service/Domain에서 수행
- 예외: `RuntimeException` 기반 커스텀 예외로 통일
- 예외: 반드시 의미 있는 도메인 단위로 정의

---

## AI가 자주 위반하는 규칙 (주의)

> Claude가 구현 시 아래 항목을 특히 자주 위반함. 구현 후 반드시 검증.

1. **else 사용** — `if (...) { ... } else { ... }` 패턴 → early return으로 교체
2. **null 직접 비교** — `if (x != null)` → `Optional.ofNullable(x).ifPresent(...)` 또는 `Optional.map()`
3. **private이 public 위에 위치** — 메서드 순서: public 전부 → private 전부
4. **메서드 13줄 초과** — 큰 메서드는 의미 단위로 private 메서드 분리
5. **매직 상수** — `"prod"`, `600_000`, `16000` 등 → 클래스 상단 `static final` 상수로 추출
6. **체이닝 과다** — `.getA().getB().getC()` → 중간 변수 추출

---

## 제약사항

- 기술 스택 명시 버전 이상 유지 (다운그레이드 금지)
- 공식 문서 기반 구현, 비권장 방식 사용 시 사전 검증 필수
- ORM(JPA) 기본 사용:
  - 성능 이슈 → QueryDSL 또는 Native Query
  - 동시성 이슈 → 트랜잭션 격리수준 / Optimistic·Pessimistic Lock 고려
- 핵심 도메인일수록 위 규칙을 더 엄격히 적용

---

## 테스트 전략

- 3단계: 단위(Unit) → 통합(Integration) → E2E
- GIVEN-WHEN-THEN 패턴
- 테스트 메서드명: **한글**로 작성
- 커버리지 80% 이상 유지
- 테스트는 독립 실행 가능, 외부 시스템 의존 금지
- 테스트 데이터는 실행마다 초기화
- 실패 시 명확한 에러 메시지 제공
- 전체 테스트 스위트 5분 이내 완료
- CI/CD 파이프라인 자동 실행 통합
- 수동(외부 API 직접 호출) 테스트 경로 분리: `/test/` 대신 `/external/` 사용

---

## 코드 감사 결과 (2026-03-31)

### 수정 완료

| 파일 | 위반 | 수정 |
|------|------|------|
| `PublishPostUseCase` | else 사용, execute 38줄, `"prod"` / `6` 매직 상수 | 상수화, early return, 메서드 분리 |
| `StreamAiSuggestionUseCase` | stream() 52줄, `600_000`/`3`/`10` 매직 숫자 | 상수화, `prepareSafeContent()` 등 분리 |
| `Post` | `if (tags != null)` null 직접 비교 | `Optional.ofNullable()` 교체 |
| `AcceptSuggestionUseCase` | `suggestedTags != null` null 직접 비교 | `Optional.ofNullable().map()` 교체 |
| `GlobalExceptionHandler` | 3점 체이닝 `.getBindingResult().getFieldErrors().stream()` | `extractFirstFieldError()` 분리 |
| `AiUsageLimiter` | `modelLimitOf()` private 위치 오류 | public 아래로 이동 |
| `ClaudeClient` | private 메서드 위치 오류 | `callApi()`, `parseLong()` public 아래 이동 |

### 잘 지켜지고 있는 항목

- DDD 계층 분리 — controller/usecase/domain/infra 일관 적용
- `@Valid` 1차 검사 + Service/Domain 2차 비즈니스 검증 분리
- `RuntimeException` 기반 커스텀 예외 5종 도메인 단위 분리
- 팩토리 메서드 패턴 — `Post.create()`, `AiSuggestion.createWithDuration()`
- `@Getter` + protected 생성자 — Setter 노출 없음
- 예외 HTTP 상태 매핑 — `GlobalExceptionHandler` 일괄 처리
