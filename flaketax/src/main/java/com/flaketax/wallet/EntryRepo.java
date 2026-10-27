package com.flaketax.wallet;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EntryRepo extends JpaRepository<Entry, Long> {
    List<Entry> findTop50ByUserIdOrderByIdDesc(Long userId);
}
