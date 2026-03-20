package github.jhkoder.aiblog.post.usecase;

import github.jhkoder.aiblog.common.exception.BusinessRuleException;
import github.jhkoder.aiblog.common.exception.NotFoundException;
import github.jhkoder.aiblog.infra.ai.ImageUsageLimiter;
import github.jhkoder.aiblog.infra.image.CloudinaryClient;
import github.jhkoder.aiblog.infra.image.GptImageClient;
import github.jhkoder.aiblog.member.domain.Member;
import github.jhkoder.aiblog.member.domain.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 이미지 생성 UseCase.
 * GPT 모델(gpt-4o, gpt-4o-mini) 선택 시에만 DALL-E 3으로 이미지를 생성한다.
 * ImageUsageLimiter로 일별 사용량을 제한한다.
 */
@Service
@RequiredArgsConstructor
public class GenerateImageUseCase {

    private final GptImageClient gptImageClient;
    private final CloudinaryClient cloudinaryClient;
    private final ImageUsageLimiter imageUsageLimiter;
    private final MemberRepository memberRepository;

    @Value("${ai.gpt.api-key:}")
    private String serverGptApiKey;

    /**
     * GPT 모델로 이미지를 생성하고 Cloudinary URL을 반환한다.
     *
     * @param memberId 요청 회원 ID
     * @param prompt   이미지 설명
     * @param model    선택된 AI 모델 (gpt- 접두어 필수)
     * @return Cloudinary secure_url
     */
    public String execute(Long memberId, String prompt, String model) {
        if (model == null || !model.startsWith("gpt-")) {
            throw new BusinessRuleException("이미지 생성은 GPT 모델 선택 시에만 가능합니다. 현재 모델: " + model);
        }

        imageUsageLimiter.checkDaily(memberId);

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new NotFoundException("회원을 찾을 수 없습니다."));

        String apiKey = (member.getGptApiKey() != null && !member.getGptApiKey().isBlank())
                ? member.getGptApiKey()
                : serverGptApiKey;

        byte[] imageBytes = gptImageClient.generateImage(prompt, apiKey);
        String publicId = "aiblog/" + System.currentTimeMillis();
        String url = cloudinaryClient.upload(imageBytes, publicId);

        imageUsageLimiter.increment(memberId);
        return url;
    }
}
