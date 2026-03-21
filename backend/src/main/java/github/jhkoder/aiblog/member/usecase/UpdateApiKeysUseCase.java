package github.jhkoder.aiblog.member.usecase;

import github.jhkoder.aiblog.common.exception.NotFoundException;
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

    @Transactional
    public MemberResponse execute(Long memberId, ApiKeyUpdateRequest request) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new NotFoundException("회원을 찾을 수 없습니다."));
        if (request.getClaudeApiKey() != null) {
            member.updateClaudeApiKey(request.getClaudeApiKey());
        }
        if (request.getGrokApiKey() != null) {
            member.updateGrokApiKey(request.getGrokApiKey());
        }
        if (request.getGptApiKey() != null) {
            member.updateGptApiKey(request.getGptApiKey());
        }
        if (request.getGeminiApiKey() != null) {
            member.updateGeminiApiKey(request.getGeminiApiKey());
        }
        if (request.getGithubToken() != null) {
            member.updateGithubCredentials(request.getGithubToken());
        }
        return MemberResponse.from(member);
    }
}
