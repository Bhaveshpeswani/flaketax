package com.flaketax.pact;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberRepo extends JpaRepository<Member, Long> {
    @Query("select (count(m) > 0) from Member m where m.user.id = :id and m.pact.status = 'OPEN'")
    boolean hasOpenPacts(@Param("id") Long id);
    Optional<Member> findByPactIdAndUserId(Long pactId, Long userId);
    List<Member> findByPactIdOrderByUserIdAsc(Long pactId);
    boolean existsByPactIdAndUserId(Long pactId, Long userId);
    long countByPactId(Long pactId);
}