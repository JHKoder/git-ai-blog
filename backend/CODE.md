# 코드 컨벤션 및 규칙 및 검증 과 테스트

## 코드 컨벤션

- 객체 지향 프로그래밍(OOP) 원칙을 따른다.
- 클린 아키텍처 및 클린 코드 원칙을 따른다.
- DDD(Domain-Driven Design) 기반으로 계층을 분리한다. (Presentation / Application / Domain / Infrastructure)
- Google Java Style을 따르되, 들여쓰기는 스페이스 4칸을 사용한다.
- 네이밍은 의도를 명확히 드러내는 풀네임을 사용한다. (축약 금지)
- 오류 코드는 Enum으로 관리하고, 오류 코드와 메시지를 분리한다.
- 로그는 레벨(INFO/WARN/ERROR)을 구분하여 의미 있게 남긴다.

## 코드 규칙

- 한 메서드에 오직 한 단계의 들여쓰기만 한다.
- else 예약어(keyword)는 쓰지 않는다.
- 모든 원시값과 문자열을 포장한다.
- 일급(first-class) 콜렉션을 쓴다.
- 한 줄에 점을 하나만 찍는다.
- 줄여쓰지 않는다(축약 금지).
- 모든 엔티티(entity)를 작게 유지한다.
- 게터(getter)/세터(setter)/프로퍼티(property)를 쓰지 않는다.
- 메소드 내용 13중 이내로 마든다.
- null 을 로직에 포함하는 것을 엄격히 금지한다.
- 모든 private 메소드는 public 아래 위치해야한

## 중요 규칙 (강화)

- 매직 넘버 / 문자열은 상수 또는 Enum으로 관리한다.
- 중복 코드는 제거하고 공통 모듈로 분리한다.
- API Request에서는 @Valid로 1차 유효성 검사를 수행한다.
- 비즈니스 규칙 검증은 Service/Domain에서 수행한다. (중요)
- 예외는 RuntimeException 기반 커스텀 예외로 통일한다.
- 예외는 반드시 의미 있는 도메인 단위로 정의한다.

## 제약사항 (정제됨)

- 기술 스택은 명시된 버전 이상을 유지한다. (다운그레이드 금지)
- 공식 문서 기반으로 구현하며, 비권장 방식 사용 시 사전 검증 필수
- ORM(JPA)은 기본 사용하되:
    - 성능 이슈 발생 시 QueryDSL 또는 Native Query 사용
    - 동시성 이슈는 다음을 고려하여 해결한다:
- 트랜잭션
    - 격리 수준
    - 락 (Optimistic / Pessimistic)
- 핵심 도메인일수록 위 규칙을 더 엄격히 적용한다.

# 테스트 전략

- 테스트는 다음 3단계로 구분한다:
- 단위 테스트 (Unit Test)
- 통합 테스트 (Integration Test)
- E2E 테스트

## 검증 및 테스트

- 기능 추가 수정 시 정상적으로 돌아가기 위한 테스트 작성 GIVEN-WHEN-THEN 패턴으로 작성
- 테스트는 단위 테스트, 통합 테스트, E2E 테스트로 나눠서 작성
- 테스트 커버리지 80% 이상 유지
- 테스트는 독립적으로 실행 가능해야 하며, 외부 시스템에 의존하지 않아야 한다.
- 테스트 데이터는 테스트 실행 시마다 초기화되어야 한다.
- 테스트는 실패 시 명확한 에러 메시지를 제공해야 한다.
- 테스트는 가능한 한 빠르게 실행되어야 하며, 전체 테스트 스위트는 5분 이내에 완료되어야 한다.
- 테스트는 코드 변경 시 자동으로 실행되어야 하며, CI/CD 파이프라인에 통합되어야 한다.
- 테스트는 다양한 시나리오와 엣지 케이스를 포함해야 하며, 예상치 못한 입력에 대한 처리를 검증해야 한다.
- 테스트는 지속적으로 유지보수되어야 하며, 코드 변경에 따라 테스트도 업데이트되어야 한다.
- 테스트는 가능한 한 자동화되어야 하며, 수동 테스트는 최소화해야 한다.
- 테스트메소드 이름은 한글로 보이도록 해야한다.
- 수동 테스트 즉 실제 외부 API 요청은 다른 경로를 다르게 한다 예시 기존 /test/../.. 대신 /external/../.. 등으로 구분한다

---

## 코드 감사 결과 (2026-03-31)

### 수정 완료 항목

| 파일                          | 위반                                                     | 수정 내용                                                                                                                                        |
|-----------------------------|--------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------|
| `PublishPostUseCase`        | else 사용, execute 38줄, `"prod"` 매직 문자열, `6` 매직 숫자       | `PROD_PROFILE`/`MIN_TITLE_LENGTH` 상수화, `publishToHashnode()` early return, `validatePublishConditions()` 등 메서드 분리                            |
| `StreamAiSuggestionUseCase` | stream() 52줄, `600_000`/`3`/`10` 매직 숫자                 | `CONTENT_MAX_CHARS`/`TAG_MIN_COUNT`/`TAG_MAX_COUNT` 상수화, `prepareSafeContent()`/`buildStream()`/`buildTokenStream()`/`buildSaveAndDone()` 분리 |
| `Post`                      | `if (tags != null)`, `accept()` 내 null 직접 비교           | `Optional.ofNullable()` 으로 교체                                                                                                                |
| `AcceptSuggestionUseCase`   | `suggestedTags != null` null 직접 비교                     | `Optional.ofNullable().map()` 으로 교체                                                                                                          |
| `GlobalExceptionHandler`    | `.getBindingResult().getFieldErrors().stream()` 3점 체이닝 | `extractFirstFieldError()` 메서드로 분리                                                                                                           |

### 잘 지켜지고 있는 항목

- DDD 계층 분리 — controller/usecase/domain/infra 패키지 일관 적용
- `@Valid` 1차 유효성 검사 + Service/Domain 2차 비즈니스 검증 분리
- `RuntimeException` 기반 커스텀 예외 5종 도메인 단위 분리
- 팩토리 메서드 생성 패턴 — `Post.create()`, `AiSuggestion.createWithDuration()`
- `@Getter` + protected 생성자 — Setter 노출 없음
- 예외 HTTP 상태 매핑 — `GlobalExceptionHandler` 일괄 처리