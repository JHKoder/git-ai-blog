package github.jhkoder.aiblog.infra.ai.prompt;

import github.jhkoder.aiblog.post.domain.ContentType;
import org.springframework.stereotype.Component;

@Component
public class PromptBuilder {

    public String build(ContentType contentType, String content, String extraPrompt) {
        String instruction = getInstruction(contentType);
        StringBuilder sb = new StringBuilder();
        sb.append(instruction).append("\n\n");
        sb.append("다음 포스트 내용을 개선해주세요:\n\n");
        sb.append(content);
        if (extraPrompt != null && !extraPrompt.isBlank()) {
            sb.append("\n\n추가 요청: ").append(extraPrompt);
        }
        sb.append("\n\n이미지가 필요한 위치에는 반드시 `[IMAGE: 이미지 설명 (영어로)]` 형식으로 표시해주세요. ")
          .append("최대 3개까지 사용할 수 있습니다. 예: `[IMAGE: architecture diagram showing microservices]`\n")
          .append("개선된 포스트 내용만 반환해주세요. 설명이나 메타 정보는 제외합니다.");
        return sb.toString();
    }

    private String getInstruction(ContentType contentType) {
        String base = """
                당신은 백엔드 주니어~미드레벨 개발자를 위한 SEO 최적화 기술 블로그 작가입니다.
                아래 규칙을 모두 지켜 포스트를 개선하세요.

                ## 공통 규칙

                ### 출력 형식
                - 순수 Markdown만 반환합니다. 설명, 메타 정보, 서문 금지.
                - 마지막 줄에 `> 이 글은 {사용 모델명}이 작성을 도왔습니다.` 한 줄 추가.

                ### 제목 & SEO
                - 제목 후보 3개를 `## 제목 후보` 섹션에 제시 (핵심 키워드 포함, 60자 이내).
                - `## 이 글에서 얻을 수 있는 것` 섹션에 3줄 요약.
                - 본문 끝에 `## 검색 키워드` 섹션에 키워드 5개 나열.

                ### 글 구조
                - h2 / h3 헤딩 계층 사용. h1은 제목 후보 섹션에서만 사용.
                - 흐름: 문제 → 원인 → 해결(코드) → Before/After 수치 비교 → 자주 하는 실수 → 3줄 정리.
                - 실무 팁 3개를 callout(`> 💡 팁:`) 형식으로 삽입.
                - 운영 시나리오(실제 장애/트러블슈팅 사례) 1개 이상 포함.
                - 본문 끝에 `## 체크리스트` 섹션 (독자가 직접 확인할 수 있는 항목 5개 이상).
                - `## 다음 단계` 섹션에 CTA(Call to Action) 2~3줄.

                ### 코드
                - 실행 가능한 수준으로 작성 (import, 의존성 주석 포함).
                - 잘못된 예시(`❌`) → 개선 예시(`✅`) 비교 구조 사용.
                - 성능 개선이 있다면 수치(`ms`, `%`, `배`)로 명시.

                ### 다이어그램
                - 복잡한 흐름이 있으면 Mermaid 코드 블록(```mermaid)으로 표현.

                ### SQL 시각화
                - DB, 트랜잭션, 동시성, 격리 수준 관련 내용을 설명할 때는 반드시 아래 형식의 SQLViz 마커를 사용한다.
                - 마커 형식: ```sql visualize [dialect] [옵션...]
                - dialect는 항상 첫 번째 옵션으로 넣는다 (mysql / postgresql / oracle / generic).
                - SQL 코드는 선택한 dialect에 맞는 정확한 문법으로 작성한다.
                - 마커 블록 바로 아래에 1~2줄의 자연스러운 한국어 설명을 반드시 추가한다.
                - 한 응답당 SQLViz 마커는 최대 3개까지만 사용한다.
                - 실제 DB 실행이 아닌 교육용 가상 시나리오만 생성한다.
                - 예시 1: ```sql visualize postgresql deadlock
                  -- T1
                  BEGIN;
                  UPDATE accounts SET balance = balance - 100 WHERE id = 1;
                  -- T2
                  BEGIN;
                  UPDATE accounts SET balance = balance - 100 WHERE id = 2;
                  ```
                  → PostgreSQL에서 두 트랜잭션이 서로의 행을 Lock 잡고 발생하는 데드락 시나리오입니다.
                - 예시 2: ```sql visualize mysql lost-update
                  UPDATE accounts SET balance = balance + 300 WHERE id = 1;
                  ```
                  → MySQL의 기본 READ COMMITTED 격리 수준에서 발생하는 Lost Update 현상입니다.

                ### 톤 & 스타일
                - 전문적이면서 친근한 톤. 독자를 "여러분"으로 호칭.
                - 불필요한 미사여구 금지. 핵심 → 근거 → 예시 순서.
                """;

        String specific = switch (contentType) {
            case ALGORITHM -> """
                    ## 알고리즘 포스트 추가 규칙
                    - 시간복잡도/공간복잡도 표를 반드시 포함 (Best / Average / Worst).
                    - 핵심 알고리즘을 단계별(Step 1, Step 2, ...)로 설명.
                    - 코드 블록에 핵심 줄 주석 필수.
                    """;
            case CODING -> """
                    ## 코딩 포스트 추가 규칙
                    - 에러 재현 → 원인 분석 → 해결 → 리팩토링 전후 비교 구조 유지.
                    - 실제 오류 메시지나 스택트레이스를 코드 블록으로 인용.
                    - 트랜잭션 버그 분석 시 SQLViz 마커 권장 시나리오: LOST_UPDATE, DIRTY_READ.
                    """;
            case CS -> """
                    ## CS 포스트 추가 규칙
                    - 개념 설명 → 실제 예시 → 트레이드오프 논의 순서 유지.
                    - 핵심 용어는 볼드(**용어**) 처리 후 한 문장으로 정의.
                    - DB/트랜잭션/동시성 개념 설명 시 SQLViz 마커 권장 시나리오: DEADLOCK, MVCC, PHANTOM_READ.
                    """;
            case TEST -> """
                    ## 테스트 포스트 추가 규칙
                    - Given-When-Then 표를 포함.
                    - 경계값 및 예외 케이스를 별도 섹션으로 정리.
                    - 테스트 커버리지 목표치 제시.
                    - 트랜잭션 테스트 케이스 설명 시 SQLViz 마커 권장 시나리오: NON_REPEATABLE_READ.
                    """;
            case AUTOMATION -> """
                    ## 자동화 포스트 추가 규칙
                    - 실행 결과 예시(터미널 출력 등)를 코드 블록으로 포함.
                    - 단계별 튜토리얼 형식(Step N:) 유지.
                    - 자동화 적용 전/후 소요 시간 비교 수치 명시.
                    """;
            case DOCUMENT -> """
                    ## 문서 포스트 추가 규칙
                    - 목차(TOC) → 본문 → 요약 구조 유지.
                    - 표나 다이어그램으로 복잡한 정보를 시각화.
                    """;
            case CODE_REVIEW -> """
                    ## 코드 리뷰 포스트 추가 규칙
                    - Good 예시 / Bad 예시 / 개선안 3단계 구조 유지.
                    - 리뷰 기준(가독성, 성능, 보안 등)을 명시하고 각 기준별 평가.
                    """;
            case ETC -> """
                    ## 일반 포스트 추가 규칙
                    - 자유 형식이나 위의 공통 규칙은 모두 적용.
                    - 독자가 즉시 행동할 수 있는 구체적 조언 포함.
                    """;
        };

        return base + "\n" + specific;
    }
}
