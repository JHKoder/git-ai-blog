package github.jhkoder.aiblog.member.usecase;

import github.jhkoder.aiblog.common.exception.NotFoundException;
import github.jhkoder.aiblog.infra.ai.ClaudeClient;
import github.jhkoder.aiblog.infra.ai.GeminiClient;
import github.jhkoder.aiblog.infra.ai.GptClient;
import github.jhkoder.aiblog.infra.ai.GrokClient;
import github.jhkoder.aiblog.member.domain.Member;
import github.jhkoder.aiblog.member.domain.MemberRepository;
import github.jhkoder.aiblog.member.dto.ApiKeyUpdateRequest;
import github.jhkoder.aiblog.member.dto.MemberResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UpdateApiKeysUseCase {

    private final MemberRepository memberRepository;
    private final ClaudeClient claudeClient;
    private final GrokClient grokClient;
    private final GptClient gptClient;
    private final GeminiClient geminiClient;

    @Transactional
    public MemberResponse execute(Long memberId, ApiKeyUpdateRequest request) {
        // 키 검증은 DB 저장 전에 수행 (트랜잭션 불필요)
        if (request.getClaudeApiKey() != null) claudeClient.validate(request.getClaudeApiKey());
        if (request.getGrokApiKey() != null) grokClient.validate(request.getGrokApiKey());
        if (request.getGptApiKey() != null) gptClient.validate(request.getGptApiKey());
        if (request.getGeminiApiKey() != null) geminiClient.validate(request.getGeminiApiKey());

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new NotFoundException("회원을 찾을 수 없습니다."));
        if (Boolean.TRUE.equals(request.getClearClaudeApiKey())) {
            member.updateClaudeApiKey(null);
        } else if (request.getClaudeApiKey() != null) {
            member.updateClaudeApiKey(request.getClaudeApiKey());
        }
        if (Boolean.TRUE.equals(request.getClearGrokApiKey())) {
            member.updateGrokApiKey(null);
        } else if (request.getGrokApiKey() != null) {
            member.updateGrokApiKey(request.getGrokApiKey());
        }
        if (Boolean.TRUE.equals(request.getClearGptApiKey())) {
            member.updateGptApiKey(null);
        } else if (request.getGptApiKey() != null) {
            member.updateGptApiKey(request.getGptApiKey());
        }
        if (Boolean.TRUE.equals(request.getClearGeminiApiKey())) {
            member.updateGeminiApiKey(null);
        } else if (request.getGeminiApiKey() != null) {
            member.updateGeminiApiKey(request.getGeminiApiKey());
        }
        if (Boolean.TRUE.equals(request.getClearGithubToken())) {
            member.updateGithubCredentials(null);
        } else if (request.getGithubToken() != null) {
            member.updateGithubCredentials(request.getGithubToken());
        }
        if (request.getAiDailyLimit() != null) {
            member.updateAiDailyLimit(request.getAiDailyLimit());
        }
        if (request.getClaudeDailyLimit() != null) {
            member.updateClaudeDailyLimit(request.getClaudeDailyLimit());
        }
        if (request.getGrokDailyLimit() != null) {
            member.updateGrokDailyLimit(request.getGrokDailyLimit());
        }
        if (request.getGptDailyLimit() != null) {
            member.updateGptDailyLimit(request.getGptDailyLimit());
        }
        if (request.getGeminiDailyLimit() != null) {
            member.updateGeminiDailyLimit(request.getGeminiDailyLimit());
        }
        if (request.getHashnodeTags() != null) {
            member.updateHashnodeTags(request.getHashnodeTags());
        }
        return MemberResponse.from(member);
    }
}
