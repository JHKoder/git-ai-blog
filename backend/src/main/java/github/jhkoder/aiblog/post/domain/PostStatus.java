package github.jhkoder.aiblog.post.domain;

import java.util.EnumSet;
import java.util.Set;

/**
 * Post 상태 머신.
 *
 * <pre>
 * DRAFT → AI_SUGGESTED → ACCEPTED → PUBLISHED
 *              ↓ (거절)
 *             DRAFT
 * accept()는 모든 상태 허용 — 거절 후 히스토리 제안 재수락 지원
 * </pre>
 */
public enum PostStatus {

    DRAFT {
        @Override
        public boolean canTransitionTo(PostStatus target) {
            return target == AI_SUGGESTED || target == ACCEPTED || target == PUBLISHED;
        }
    },
    AI_SUGGESTED {
        @Override
        public boolean canTransitionTo(PostStatus target) {
            return target == DRAFT || target == ACCEPTED || target == PUBLISHED;
        }
    },
    ACCEPTED {
        @Override
        public boolean canTransitionTo(PostStatus target) {
            return target == AI_SUGGESTED || target == PUBLISHED;
        }
    },
    PUBLISHED {
        @Override
        public boolean canTransitionTo(PostStatus target) {
            return target == AI_SUGGESTED || target == ACCEPTED;
        }
    };

    public abstract boolean canTransitionTo(PostStatus target);

    /** accept()가 허용되는 상태 집합 — 모든 상태에서 수락 가능 */
    public static final Set<PostStatus> ACCEPTABLE_FROM = EnumSet.allOf(PostStatus.class);
}
