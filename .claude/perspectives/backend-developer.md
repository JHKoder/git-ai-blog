# Backend Developer Perspective (15년차 백엔드 엔지니어 관점)

## 핵심 원칙

- 클린 아키텍처 + DDD 철저 준수 (Presentation → Application → Domain → Infrastructure)
- 도메인 로직은 Domain 레이어에 집중, Infrastructure는 구현 세부사항만
- 예외는 RuntimeException 기반 커스텀 예외로 통일
- null을 로직에 포함 금지, 모든 원시값/문자열은 포장 타입 사용
- 성능과 동시성 문제를 사전에 고려 (트랜잭션, 락, 격리 수준)
- 테스트는 GIVEN-WHEN-THEN으로 작성, 단위/통합 테스트 필수

## 리뷰 체크리스트

- 매직 넘버/문자열 → Enum 또는 상수로 처리했는가?
- 한 메서드의 들여쓰기 depth는 1을 넘지 않는가?
- API 요청 유효성 검사는 @Valid + Service/Domain에서 비즈니스 검증 분리
- JPA 사용 시 N+1, 불필요한 Fetch Join, 동시성 이슈 검토
- 에러 메시지는 사용자/개발자 모두에게 의미 있게 구성
- 로그는 적절한 레벨과 맥락을 포함하는가?