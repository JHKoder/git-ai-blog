package github.jhkoder.aiblog.security;

import github.jhkoder.aiblog.member.domain.Member;
import github.jhkoder.aiblog.member.domain.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final MemberRepository memberRepository;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);

        Map<String, Object> attributes = oAuth2User.getAttributes();
        String githubId = String.valueOf(attributes.get("id"));
        String username = (String) attributes.getOrDefault("login", "unknown");
        String avatarUrl = (String) attributes.get("avatar_url");

        Member member = memberRepository.findByGithubId(githubId)
                .orElseGet(() -> memberRepository.save(Member.create(githubId, username, avatarUrl)));

        member.updateProfile(username, avatarUrl);
        memberRepository.save(member);

        Map<String, Object> modifiedAttributes = new HashMap<>(attributes);
        modifiedAttributes.put("memberId", member.getId());

        return new DefaultOAuth2User(List.of(), modifiedAttributes, "id");
    }
}
