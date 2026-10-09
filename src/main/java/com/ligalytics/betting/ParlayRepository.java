package com.ligalytics.betting;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ParlayRepository extends JpaRepository<Parlay, Long> {

    List<Parlay> findTop100ByUserIdOrderByPlacedAtDesc(Long userId);

    List<Parlay> findByStatus(BetStatus status);

    void deleteByUserId(Long userId);
}
