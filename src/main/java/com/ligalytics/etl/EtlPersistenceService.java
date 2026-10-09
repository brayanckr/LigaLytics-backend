package com.ligalytics.etl;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ligalytics.model.Match;
import com.ligalytics.model.Team;
import com.ligalytics.patterns.builder.MatchAnalysis;
import com.ligalytics.patterns.builder.AdvancedStats;
import com.ligalytics.repository.MatchRepository;
import com.ligalytics.repository.TeamRepository;

/**
 * Capa de persistencia del ETL. Encapsula el acceso a PostgreSQL a través de
 * los repositorios JPA ({@link TeamRepository} y {@link MatchRepository}) para
 * que el flujo de ingestión guarde directamente los equipos y partidos
 * procesados.
 */
@Service
public class EtlPersistenceService {

    private final TeamRepository teamRepository;
    private final MatchRepository matchRepository;

    public EtlPersistenceService(TeamRepository teamRepository, MatchRepository matchRepository) {
        this.teamRepository = teamRepository;
        this.matchRepository = matchRepository;
    }

    /**
     * Busca un equipo por nombre (sin distinguir mayúsculas) y lo crea si no
     * existe. Devuelve siempre una entidad gestionada con su id asignado.
     */
    @Transactional
    public Team resolveTeam(String canonicalName) {
        return teamRepository.findByNameIgnoreCase(canonicalName)
                .orElseGet(() -> teamRepository.save(Team.builder().name(canonicalName).build()));
    }

    public boolean matchExists(Long homeTeamId, Long awayTeamId, LocalDateTime from, LocalDateTime to) {
        return matchRepository.existsByHomeTeamIdAndAwayTeamIdAndMatchDateBetween(homeTeamId, awayTeamId, from, to);
    }

    /**
     * Persiste un partido a partir del análisis construido con el patrón
     * Builder, asociándolo a sus equipos ya resueltos.
     */
    @Transactional
    public Match saveAnalysis(MatchAnalysis analysis, Team homeTeam, Team awayTeam) {
        Match match = Match.builder()
                .matchDate(analysis.getMatchDate())
                .homeTeam(homeTeam)
                .awayTeam(awayTeam)
                .fullTimeHomeGoals(analysis.getHomeGoals())
                .fullTimeAwayGoals(analysis.getAwayGoals())
                .homeXg(analysis.getHomeXg())
                .awayXg(analysis.getAwayXg())
                .corners(analysis.getCorners())
                .yellowCards(analysis.getYellowCards())
                .redCards(analysis.getRedCards())
                .homeYellowCards(analysis.getHomeYellowCards())
                .awayYellowCards(analysis.getAwayYellowCards())
                .homeRedCards(analysis.getHomeRedCards())
                .awayRedCards(analysis.getAwayRedCards())
                .homeShots(intOrNull(analysis, AdvancedStats.HOME_SHOTS))
                .awayShots(intOrNull(analysis, AdvancedStats.AWAY_SHOTS))
                .homeShotsOnTarget(intOrNull(analysis, AdvancedStats.HOME_SHOTS_ON_TARGET))
                .awayShotsOnTarget(intOrNull(analysis, AdvancedStats.AWAY_SHOTS_ON_TARGET))
                .homeFouls(intOrNull(analysis, AdvancedStats.HOME_FOULS))
                .awayFouls(intOrNull(analysis, AdvancedStats.AWAY_FOULS))
                .build();
        return matchRepository.save(match);
    }

    private static Integer intOrNull(MatchAnalysis analysis, String key) {
        return analysis.hasAdvanced(key) ? (int) analysis.advanced(key, 0) : null;
    }
}
