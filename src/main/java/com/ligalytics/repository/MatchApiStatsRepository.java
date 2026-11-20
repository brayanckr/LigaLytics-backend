package com.ligalytics.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ligalytics.model.MatchApiStats;

public interface MatchApiStatsRepository extends JpaRepository<MatchApiStats, Long> {
}
