package github.jhkoder.aiblog.infra.ai.prompt;

import github.jhkoder.aiblog.post.domain.ContentType;
import org.springframework.stereotype.Component;

@Component
public class PromptBuilder {

    /**
     * AI 개선 프롬프트를 빌드한다.
     * content 30자 미만: 짧은 설명 요청으로 판단 → 4단계 파이프라인 미적용, 단순 개선 프롬프트 사용.
     * content 30자 이상: 4단계 파이프라인(설계→작성→리뷰→압축) + 평가 기준 6개 통합 적용.
     */
    public String build(ContentType contentType, String content, String extraPrompt) {
        boolean isShortRequest = content == null || content.trim().length() < 30;

        if (isShortRequest) {
            return buildSimple(contentType, content, extraPrompt);
        }
        return buildFull(contentType, content, extraPrompt);
    }

    /** 30자 미만 짧은 요청 — 단순 개선 프롬프트 */
    private String buildSimple(ContentType contentType, String content, String extraPrompt) {
        String instruction = getBaseInstruction(contentType);
        StringBuilder sb = new StringBuilder();
        sb.append(instruction).append("\n\n");
        sb.append("다음 포스트 내용을 개선해주세요:\n\n");
        sb.append(content);
        if (extraPrompt != null && !extraPrompt.isBlank()) {
            sb.append("\n\n추가 요청: ").append(extraPrompt);
        }
        sb.append("\n\n개선된 포스트 내용만 반환해주세요. 설명이나 메타 정보는 제외합니다.");
        return sb.toString();
    }

    /** 30자 이상 — 4단계 파이프라인 + 평가 기준 통합 프롬프트 */
    private String buildFull(ContentType contentType, String content, String extraPrompt) {
        String baseInstruction = getBaseInstruction(contentType);
        StringBuilder sb = new StringBuilder();

        sb.append("""
                당신은 백엔드 주니어~미드레벨 개발자를 위한 실무 기술 블로그 작가이자 30년차 고성능 튜닝 엔지니어입니다.
                아래 4단계 프로세스로 블로그 글을 개선합니다.

                ## 출력 규칙 (반드시 준수)
                - 최종 출력: 순수 Markdown만 반환합니다. 단계별 설명, 분석 결과, 메타 정보 출력 금지.
                - 첫 줄: `# {제목}` 형식으로 시작. 제목은 "~의(는) ~다" 스타일. "완전 정복" 금지. 후보 나열 금지.
                - 마지막 줄: `> 이 글은 {사용 모델명}이 작성을 도왔습니다.` 한 줄 추가.

                """);

        sb.append("""
                ## 1단계: 구조 설계
                다음 요소를 내부적으로 설계합니다 (출력 금지):
                - "문제 → 원인 → 해결 → 검증" 흐름 설계
                - 독자를 끌어들이는 Hook 문장 3개 구상
                - 반드시 포함할 실무 장애 사례 1개 선정

                ## 2단계: 초안 작성
                설계를 기반으로 작성합니다:
                - 이론 최소화, "왜 장애가 나는지" 중심
                - Before/After 비교 포함
                - 단정적 문체 ("~일 수 있다" 금지)

                ## 3단계: 리뷰 (내부 평가 — 출력 금지)
                아래 6가지 기준으로 초안을 평가하고 개선점을 도출합니다:
                1. Structure: 문제→원인→해결→검증 흐름이 논리적인가
                2. Practicality: 실제 장애 상황에서 바로 적용 가능한가
                3. Depth: "왜 그렇게 되는지" Thread/Queue/OS/JVM까지 설명하는가
                4. Evidence: 수치, Before/After가 충분한가
                5. Readability: 불필요한 반복, 난독 구간이 없는가
                6. Originality: 구글 검색 결과와 차별화되는 인사이트가 있는가

                필수 점검:
                - 치명적 문제 TOP 3 식별 후 반드시 수정
                - 빠진 핵심 내용 추가 (OS backlog, keepalive, GC 영향 등 해당되는 것)
                - 가치 대비 긴 구간 삭제

                ## 4단계: 압축
                - 분량 20~30% 축소, 정보량 유지
                - 코드블록·수치·Before/After 표 절대 삭제 금지
                - 한 문장 = 하나의 메시지
                - 각 섹션 첫 줄에 핵심 한 줄 요약 추가

                """);

        sb.append(baseInstruction).append("\n\n");

        if (extraPrompt != null && !extraPrompt.isBlank()) {
            sb.append("## 추가 요청\n").append(extraPrompt).append("\n\n");
        }

        sb.append("## 개선할 포스트\n\n").append(content);

        return sb.toString();
    }

    private String getBaseInstruction(ContentType contentType) {
        String base = """
                ## 공통 규칙

                ### 제목 & SEO
                - `## 이 글에서 얻을 수 있는 것` 섹션에 3줄 요약.
                - 본문 끝에 `## 검색 키워드` 섹션에 키워드 5개 나열.

                ### 글 구조
                - h2 / h3 헤딩 계층 사용. h1은 문서 제목에서만 사용.
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
                - `flowchart TD` 단독 나열 금지.
                - 다이어그램 위에 반드시 한 줄 핵심 요약 선행.
                - 타입 선택 기준:
                  * 트랜잭션 간 상호작용 / 시간 순서 / Lock 획득·대기 상태 전이 / 여러 주체 간 통신 흐름 → `sequenceDiagram`
                  * 단순 인과관계 / 한 방향 원인→결과 체인 / 프로세스 단계 나열 → `flowchart LR`
                  * 노드 6개 이상이고 단계 구분이 의미 있을 때만 `subgraph` 추가

                ### SQL 코드블록 규칙
                - 일반 SQL 코드블록은 반드시 ` ```sql ` 만 사용한다. dialect를 ` ```sql ` 뒤에 붙이지 않는다.
                - 올바른 예: ` ```sql `  /  잘못된 예: ` ```sql mysql `

                ### SQL 시각화
                - DB, 트랜잭션, 동시성, 격리 수준 관련 내용을 설명할 때는 반드시 아래 형식의 SQLViz 마커를 사용한다.
                - 마커 형식: ```sql visualize [dialect] [옵션...]
                - dialect는 항상 첫 번째 옵션으로 넣는다 (mysql / postgresql / oracle / generic).
                - 마커 블록 바로 아래에 1~2줄의 자연스러운 한국어 설명을 반드시 추가한다.
                - 한 응답당 SQLViz 마커는 최대 3개까지만 사용한다.

                ### 톤 & 스타일
                - 전문적이면서 친근한 톤. 독자를 "여러분"으로 호칭.
                - 핵심 → 근거 → 예시 순서.
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
