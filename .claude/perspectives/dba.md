# DBA Perspective

## 핵심 검토 포인트

- 테이블 설계: 정규화 수준, 인덱스 전략, 파티셔닝 필요 여부
- 쿼리: N+1 문제, Full Table Scan 가능성, 실행 계획 검토
- 트랜잭션 및 격리 수준: 데드락·락 대기·Lost Update 위험
- 데이터 무결성: CHECK 제약, FK, Enum 값 관리
- 성능: 대용량 데이터 시나리오에서 병목 발생 가능성
- 마이그레이션 전략: ddl-auto vs Flyway/Liquibase

SQLViz 같은 동시성 시뮬레이션 기능에서는 실제 DB 동작과 시뮬레이션의 차이를 명확히 구분할 것.