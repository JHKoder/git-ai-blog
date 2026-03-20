package github.jhkoder.aiblog.member.usecase;

import github.jhkoder.aiblog.common.exception.NotFoundException;
import github.jhkoder.aiblog.member.domain.Member;
import github.jhkoder.aiblog.member.domain.MemberRepository;
import github.jhkoder.aiblog.member.dto.HashnodeConnectRequest;
import github.jhkoder.aiblog.member.dto.MemberResponse;
import github.jhkoder.aiblog.post.usecase.ImportHashnodePostUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConnectHashnodeUseCase {

    private final MemberRepository memberRepository;
    private final ImportHashnodePostUseCase importHashnodePostUseCase;

    @Transactional
    public MemberResponse execute(Long memberId, HashnodeConnectRequest request) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new NotFoundException("회원을 찾을 수 없습니다."));
        member.connectHashnode(request.getToken(), request.getPublicationId());

        try {
            importHashnodePostUseCase.execute(memberId, request.getToken(), request.getPublicationId());
        } catch (Exception e) {
            log.warn("Hashnode 연동 성공, 게시글 자동 import 실패 (무시): {}", e.getMessage());
        }

        return MemberResponse.from(member);
    }
}
