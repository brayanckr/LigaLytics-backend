package com.ligalytics.betting;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BetRepository extends JpaRepository<Bet, Long> {

    List<Bet> findTop100ByUserIdOrderByPlacedAtDesc(Long userId);

    List<Bet> findByStatus(BetStatus status);

    void deleteByUserId(Long userId);
}
