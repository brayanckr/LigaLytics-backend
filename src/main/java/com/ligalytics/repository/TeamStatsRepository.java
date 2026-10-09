package com.ligalytics.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.ligalytics.model.TeamStats;

@Repository
public interface TeamStatsRepository extends JpaRepository<TeamStats, Long> {

    Optional<TeamStats> findByTeamIdAndSeason(Long teamId, String season);

    List<TeamStats> findBySeason(String season);
}
