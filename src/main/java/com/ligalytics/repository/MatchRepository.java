package com.ligalytics.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.ligalytics.model.Match;

@Repository
public interface MatchRepository extends JpaRepository<Match, Long> {

    List<Match> findByHomeTeamIdOrAwayTeamId(Long homeTeamId, Long awayTeamId);

    List<Match> findByMatchDateBetween(LocalDateTime from, LocalDateTime to);

    boolean existsByHomeTeamIdAndAwayTeamIdAndMatchDateBetween(
            Long homeTeamId, Long awayTeamId, LocalDateTime from, LocalDateTime to);

    Optional<Match> findFirstByHomeTeamIdAndAwayTeamIdAndMatchDateBetween(
            Long homeTeamId, Long awayTeamId, LocalDateTime from, LocalDateTime to);

    /** Todos los partidos con sus equipos ya cargados, en orden cronológico. */
    @Query("select m from Match m join fetch m.homeTeam join fetch m.awayTeam order by m.matchDate")
    List<Match> findAllWithTeams();

    /** Partidos de un rango de fechas [from, to) con sus equipos cargados. */
    @Query("select m from Match m join fetch m.homeTeam join fetch m.awayTeam "
            + "where m.matchDate >= :from and m.matchDate < :to order by m.matchDate")
    List<Match> findBetweenWithTeams(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("select min(m.matchDate) from Match m")
    LocalDateTime findEarliestMatchDate();

    @Query("select max(m.matchDate) from Match m")
    LocalDateTime findLatestMatchDate();
}
