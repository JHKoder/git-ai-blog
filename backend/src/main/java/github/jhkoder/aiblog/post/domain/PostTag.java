package github.jhkoder.aiblog.post.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "post_tags", indexes = {
        @Index(name = "idx_post_tags_post_id", columnList = "post_id"),
        @Index(name = "idx_post_tags_tag", columnList = "tag")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PostTag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    @Column(name = "tag", nullable = false)
    private String tag;

    public static PostTag of(Post post, String tag) {
        PostTag postTag = new PostTag();
        postTag.post = post;
        postTag.tag = tag;
        return postTag;
    }
}
