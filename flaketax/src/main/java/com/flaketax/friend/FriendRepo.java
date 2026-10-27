package com.flaketax.friend;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FriendRepo extends JpaRepository<Friend, Long> {
    Optional<Friend> findByAAndB(Long a, Long b);
    @Query("select f from Friend f where f.a = :uid or f.b = :uid order by f.id desc")
    List<Friend> forUser(@Param("uid") Long uid);
}