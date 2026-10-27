package com.flaketax.pact;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PactRepo extends JpaRepository<Pact, Long> {
    boolean existsByHostIdAndStatus(Long hostId, String status);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Pact p where p.id = :id")
    Optional<Pact> lock(@Param("id") Long id);
    @Query("select p.id from Pact p where p.status = 'OPEN' and p.reviewEnd <= :now order by p.id")
    List<Long> due(@Param("now") Instant now);
    @Query("select distinct p from Pact p left join Member m on m.pact = p " +
            "where (p.host.id = :uid or m.user.id = :uid) and p.archived = false order by p.id desc")
    List<Pact> mine(@Param("uid") Long uid);
    @Query("select p from Pact p where p.status = 'OPEN' and p.archived = false " +
            "and p.deadline > :now order by p.id desc")
    List<Pact> open(@Param("now") Instant now);
    @Query("select p from Pact p where p.status = 'OPEN' and p.archived = false " +
            "and p.host.id in :ids and p.deadline > :now order by p.id desc")
    List<Pact> friends(@Param("ids") List<Long> ids, @Param("now") Instant now);
}