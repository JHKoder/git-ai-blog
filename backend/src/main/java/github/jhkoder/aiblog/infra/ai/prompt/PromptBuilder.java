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
        return switch (contentType) {
            case ALGORITHM -> "다음 알고리즘 포스트를 개선하세요. 시간복잡도와 공간복잡도 표, 코드 블록, 단계별 설명을 반드시 포함하세요. 엄격하고 교육적인 톤으로 작성합니다.";
            case CODING -> "다음 코딩 포스트를 개선하세요. 에러 재현 → 해결 → 리팩토링 전후 비교 구조로 실무적이고 간결하게 작성합니다.";
            case CS -> "다음 CS 포스트를 개선하세요. 개념 설명 → 예시 → 트레이드오프 논의 순서로 학술적이고 깊이있게 작성합니다.";
            case TEST -> "다음 테스트 포스트를 개선하세요. Given-When-Then 표와 경계값 테스트 케이스를 포함해 꼼꼼하고 방어적인 톤으로 작성합니다.";
            case AUTOMATION -> "다음 자동화 포스트를 개선하세요. 단계별 코드와 실행 결과 예시를 포함한 실용적인 튜토리얼 형식으로 작성합니다.";
            case DOCUMENT -> "다음 문서 포스트를 개선하세요. 목차 → 본문 → 요약 구조로 명확하고 구조화되게 작성합니다.";
            case CODE_REVIEW -> "다음 코드 리뷰 포스트를 개선하세요. Good / Bad / 개선안 3단계 구조로 비판적이고 건설적인 톤으로 작성합니다.";
            case ETC -> "다음 포스트를 더 읽기 좋게 개선하세요. 자유 형식으로 작성합니다.";
        };
    }
}
