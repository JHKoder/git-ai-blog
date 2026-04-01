# code-style.md

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
- 세터(setter)/프로퍼티(property)를 쓰지 않는다.
- 메소드 내용 13중 이내로 마든다.
- null 을 로직에 포함하는 것을 엄격히 금지한다.
- 모든 private 메소드는 public 아래 위치해야한