package github.jhkoder.aiblog.infra.ai.prompt;

import github.jhkoder.aiblog.post.domain.ContentType;
import org.springframework.stereotype.Component;

@Component
public class PromptBuilder {

    private static final String SYSTEM_ROLE = """
            당신은 백엔드 개발자를 위한 실무 기술 블로그 전문 작가이자 30년차 고성능 시스템 엔지니어입니다.
            목표: 독자가 "이 글은 실무에서 바로 써먹을 수 있다"고 느끼게 하는 고품질 기술 블로그 글 작성.
            """;

    private static final String COMMON_RULES = """
            ## 출력 규칙 (반드시 준수)
            - 최종 출력은 **순수 Markdown**만 반환. 설명, 분석, 메타 정보 절대 포함 금지.
            - 첫 줄: `# 제목` (직접적이고 명확한 "~이다" 스타일, "완전 정복" 금지)
            - 본문 끝 바로 위: `TAGS: tag1,tag2,tag3` (영문 소문자, 하이픈 사용, 3~8개)
            - 맨 마지막: `> 이 글은 {모델명}이 작성을 도왔습니다.`
            
            **구조**:
            - 문제 인식 → 원인 분석 → 해결 방안 → Before/After 비교 → 실무 팁 → 체크리스트 → 다음 단계
            
            **스타일**:
            - 전문적이면서 친근한 톤
            - 한 문단은 3줄 이하
            - 코드 블록은 실행 가능하게 (import, 버전 주석 포함)
            - 성능 개선은 반드시 수치로 명시 (ms, %, 배수)
            """;

    /**
     * Claude System Prompt용 — cache_control: ephemeral 적용 대상.
     * 고정 규칙 전체(SYSTEM_ROLE + COMMON_RULES + 4단계 파이프라인 + 컨벤션 + ContentType 규칙) 포함.
     * TTL 5분 내 동일 ContentType 반복 요청 시 입력 토큰 절감.
     */
    public String buildSystemPrompt(ContentType contentType) {
        return SYSTEM_ROLE + "\n" + COMMON_RULES + buildPipelineInstructions()
                + "\n" + getBaseInstruction() + "\n" + getContentTypeInstruction(contentType);
    }

    /**
     * User Prompt — system/user 분리 스트리밍 시 가변 부분만 담는다.
     * extraPrompt + 개선할 포스트 원문만 포함.
     */
    public String buildUserPrompt(String content, String extraPrompt) {
        StringBuilder sb = new StringBuilder();
        if (extraPrompt != null && !extraPrompt.isBlank()) {
            sb.append("## 추가 요청\n").append(extraPrompt).append("\n\n");
        }
        sb.append("## 개선할 포스트\n\n").append(content);
        return sb.toString();
    }

    /**
     * AI 개선 프롬프트를 빌드한다.
     * content 30자 미만: 짧은 설명 요청으로 판단 → 4단계 파이프라인 미적용, 단순 개선 프롬프트 사용.
     * content 30자 이상: 4단계 파이프라인(설계→작성→리뷰→압축) + 평가 기준 6개 통합 적용.
     */
    public String build(ContentType contentType, String content, String extraPrompt) {
        if (content == null || content.trim().length() < 30) {
            return buildSimple(contentType, content, extraPrompt);
        }
        return buildFull(contentType, content, extraPrompt);
    }

    /**
     * AI 평가 프롬프트를 빌드한다.
     * 개선이 아닌 분석/평가 목적 — 6가지 기준 점수 + 필수 피드백 + 추천 개선 요청사항 출력.
     */
    public String buildEvaluation(ContentType contentType, String content) {
        return """
                당신은 기술 블로그 품질 평가 전문가입니다.
                아래 블로그 글을 엄격하게 평가하고, 즉시 실행 가능한 개선 방향을 제시하세요.
                
                ## 출력 규칙 (반드시 준수)
                
                **형식**
                - 순수 Markdown으로만 출력한다.
                - 섹션은 반드시 아래 순서대로 출력한다. 순서 변경 금지.
                
                **강조 규칙**
                - 🚨 치명적인 문제 → **굵게** + 이모지
                - ✅ 개선안 → 코드 블록 또는 인용문(`>`) 박스
                - 📊 점수 → 표 유지 + 핵심 단어 **Bold**
                - 💡 인사이트 → 인용문(`>`) 사용
                - Java 코드 예시는 반드시 ` ```java ``` ` 언어 태그 사용
                - 중요 내용은 아래 형태로 감싸라:
                  > 🔥 핵심 요약
                  > 내용
                
                **가독성 규칙**
                - 한 문단 3줄 이하
                - 긴 문장은 줄바꿈 강제
                - 리스트 적극 사용
                - "텍스트 벽" 절대 금지
                - 💡 팁 남발 금지 (동일 패턴 반복 금지)
                - 모든 내용을 같은 중요도로 다루지 말 것
                
                ---
                
                ## 섹션 1: 🔥 한 줄 총평
                글 전체를 한 문장으로 요약한다. 가장 위에 배치.
                
                > 🔥 (총평 한 줄)
                
                ---
                
                ## 섹션 2: 📊 점수 요약
                
                | 기준 | 점수 (10점 만점) | 이유 | 개선안 |
                |------|:--------------:|------|-------|
                | **Structure** (구조 설계) | | | |
                | **Practicality** (실무 적합성) | | | |
                | **Depth** (깊이) | | | |
                | **Evidence** (데이터 기반) | | | |
                | **Readability** (가독성) | | | |
                | **Originality** (차별성) | | | |
                
                ---
                
                ## 섹션 3: 🚨 치명적인 문제 TOP 3
                실무에서 즉시 위험하거나 독자가 오해할 수 있는 부분만. 3개를 초과하지 않는다.
                
                ---
                
                ## 섹션 4: 🧠 핵심 개선 포인트
                점수를 가장 크게 올릴 수 있는 2~3가지만. 설명은 간결하게.
                
                ---
                
                ## 섹션 5: ✂️ 제거/축소 대상
                가치 대비 길이가 긴 구간. 구체적인 섹션명 또는 내용을 지목한다.
                
                ---
                
                ## 섹션 6: 🏗 구조 개선안
                목차 재구성 제안. 현재 구조 → 개선 구조 형태로 비교.
                
                ---
                
                ## 섹션 7: 💎 전문가 한 줄
                글의 급을 올리는 문장 1개. 독자가 "이 글은 다르다"고 느낄 핵심.
                
                ---
                
                ## 섹션 8: 추천 AI 개선 요청사항
                아래 내용을 "추가 요청사항"에 그대로 입력하면 AI 개선 품질이 높아집니다:
                
                ```
                (여기에 위 평가 결과 기반 구체적인 개선 지시 2~3줄)
                ```
                
                ---
                
                ## 평가할 글
                
                """
                + content;
    }

    /**
     * 30자 미만 짧은 요청 — 단순 개선 프롬프트
     */
    private String buildSimple(ContentType contentType, String content, String extraPrompt) {
        StringBuilder sb = new StringBuilder();
        sb.append(getBaseInstruction()).append("\n").append(getContentTypeInstruction(contentType)).append("\n\n");
        sb.append("다음 포스트 내용을 개선해주세요:\n\n");
        sb.append(content);
        if (extraPrompt != null && !extraPrompt.isBlank()) {
            sb.append("\n\n추가 요청: ").append(extraPrompt);
        }
        sb.append("\n\n개선된 포스트 내용만 반환해주세요. 설명이나 메타 정보는 제외합니다.");
        return sb.toString();
    }

    /**
     * 30자 이상 — 4단계 파이프라인 + 평가 기준 통합 프롬프트 (단일 전송용 폴백).
     * Claude 스트리밍 시에는 buildSystemPrompt + buildUserPrompt 분리 방식을 우선 사용.
     */
    private String buildFull(ContentType contentType, String content, String extraPrompt) {
        StringBuilder sb = new StringBuilder();

        sb.append(SYSTEM_ROLE).append("\n\n").append(COMMON_RULES);
        sb.append(buildPipelineInstructions());
        sb.append(getBaseInstruction()).append("\n").append(getContentTypeInstruction(contentType)).append("\n\n");

        if (extraPrompt != null && !extraPrompt.isBlank()) {
            sb.append("## 추가 요청\n").append(extraPrompt).append("\n\n");
        }

        sb.append("## 개선할 포스트\n\n").append(content);

        return sb.toString();
    }

    private String getBaseInstruction() {
        return """
                ## 공통 규칙
                
                **제목·SEO**: `## 이 글에서 얻을 수 있는 것` 3줄 요약 / 본문 끝 `## 검색 키워드` 5개.
                **글 구조**: h2/h3 헤딩 / 문제→원인→해결(코드)→Before/After 수치→자주 하는 실수→3줄 정리 / 실무 팁 3개 callout(`> 💡 팁:`) / 운영 시나리오 1개 이상 / `## 체크리스트` 5항목 / `## 다음 단계` CTA 2~3줄.
                **코드**: 실행 가능(import·의존성 주석 포함) / 잘못된 예(`❌`)→개선 예(`✅`) / 성능 개선 수치(ms·%·배) 명시.
                **다이어그램**: 복잡 흐름→Mermaid(```mermaid) / flowchart TD 단독 나열 금지 / 다이어그램 위 한 줄 요약 선행 / 상호작용·시간순·Lock 흐름→sequenceDiagram / 단순 인과→flowchart LR / 6노드 이상+단계 구분 필요 시만 subgraph.
                **SQL**: 일반 블록은 ```sql만 사용(dialect 붙이기 금지).
                **SQLViz**: DB·트랜잭션·동시성·격리수준 설명 시 `--SQLViz: [dialect] [시나리오]` 마커 SQL 블록 첫 줄 / dialect: postgresql·mysql·oracle·generic / 시나리오: deadlock·lost-update·dirty-read·non-repeatable·phantom-read·mvcc / 마커 아래 1~2줄 한국어 설명 / 최대 3개 / 실제 DB 실행 아님 명시.
                **톤**: 전문적·친근 / 독자="여러분" / 핵심→근거→예시 순서.""";
    }

    private String getContentTypeInstruction(ContentType contentType) {
        return switch (contentType) {
            case ALGORITHM -> """
                    ## 알고리즘 추가 규칙
                    - 시간/공간 복잡도 표 (Best·Average·Worst) 필수
                    - 핵심 알고리즘 단계별(Step N) 설명
                    - 코드 블록 핵심 줄 주석 필수""";
            case CODING -> """
                    ## 코딩 추가 규칙
                    - 에러 재현→원인 분석→해결→리팩토링 전후 비교
                    - 실제 오류 메시지/스택트레이스 코드 블록 인용
                    - 트랜잭션 버그 시 SQLViz 권장: LOST_UPDATE·DIRTY_READ""";
            case CS -> """
                    ## CS 추가 규칙
                    - 개념→실제 예시→트레이드오프 순서
                    - 핵심 용어 **볼드** 후 한 문장 정의
                    - DB/트랜잭션/동시성 시 SQLViz 권장: DEADLOCK·MVCC·PHANTOM_READ""";
            case TEST -> """
                    ## 테스트 추가 규칙
                    - Given-When-Then 표 포함
                    - 경계값·예외 케이스 별도 섹션
                    - 커버리지 목표치 제시
                    - 트랜잭션 테스트 시 SQLViz 권장: NON_REPEATABLE_READ""";
            case AUTOMATION -> """
                    ## 자동화 추가 규칙
                    - 실행 결과 예시(터미널 출력) 코드 블록 포함
                    - 단계별 튜토리얼(Step N) 형식
                    - 자동화 전/후 소요 시간 비교 수치 명시""";
            case DOCUMENT -> """
                    ## 문서 추가 규칙
                    - 목차(TOC)→본문→요약 구조
                    - 표·다이어그램으로 복잡 정보 시각화""";
            case CODE_REVIEW -> """
                    ## 코드 리뷰 추가 규칙
                    - Good 예시·Bad 예시·개선안 3단계 구조
                    - 리뷰 기준(가독성·성능·보안) 명시 후 기준별 평가""";
            case ETC -> """
                    ## 일반 추가 규칙
                    - 자유 형식이나 공통 규칙 전체 적용
                    - 독자가 즉시 행동할 수 있는 구체적 조언 포함""";
        };
    }

    /**
     * 4단계 파이프라인 지시 — system prompt와 단일 프롬프트 양쪽에서 재사용.
     */
    private String buildPipelineInstructions() {
        return """
                ## 1단계: 구조 설계 (내부, 출력 금지)
                - "문제→원인→해결→검증" 흐름 설계
                - Hook 문장 3개 구상
                - 실무 장애 사례 1개 선정

                ## 2단계: 초안 작성
                - 이론 최소화, "왜 장애가 나는지" 중심
                - Before/After 비교 포함
                - 단정적 문체 ("~일 수 있다" 금지)

                ## 3단계: 리뷰 (내부 평가, 출력 금지)
                6기준 평가: Structure·Practicality·Depth·Evidence·Readability·Originality
                - 치명적 문제 TOP 3 수정
                - 빠진 핵심 내용 추가 (OS backlog·keepalive·GC 등)
                - 가치 대비 긴 구간 삭제

                ## 4단계: 압축
                - 분량 20~30% 축소, 정보량 유지
                - 코드블록·수치·Before/After 표 절대 삭제 금지
                - 한 문장 = 하나의 메시지
                - 각 섹션 첫 줄에 핵심 한 줄 요약 추가
                """;
    }
}
