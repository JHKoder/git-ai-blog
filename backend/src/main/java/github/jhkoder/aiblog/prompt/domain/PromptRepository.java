package github.jhkoder.aiblog.prompt.domain;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PromptRepository extends JpaRepository<Prompt, Long> {

    List<Prompt> findByMemberIdOrderByUsageCountDesc(Long memberId);

    long countByMemberId(Long memberId);

    Optional<Prompt> findByIdAndMemberId(Long id, Long memberId);

    @Query("SELECT p FROM Prompt p WHERE p.isPublic = true ORDER BY p.usageCount DESC")
    List<Prompt> findPopularPublic(Pageable pageable);

    @Query("SELECT p FROM Prompt p WHERE p.memberId = :memberId AND p.isPublic = true ORDER BY p.usageCount DESC")
    List<Prompt> findPopularByMember(@Param("memberId") Long memberId, Pageable pageable);
}
