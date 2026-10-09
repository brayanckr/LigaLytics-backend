package com.ligalytics.patterns.observer;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.ligalytics.model.Match;
import com.ligalytics.model.Team;
import com.ligalytics.model.TeamStats;
import com.ligalytics.repository.MatchRepository;
import com.ligalytics.repository.TeamRepository;
import com.ligalytics.repository.TeamStatsRepository;
import com.ligalytics.service.SeasonUtil;

/**
 * Observador concreto que recomputa y actualiza las estadísticas agregadas de
 * los equipos ({@link TeamStats}) tras una ingesta de partidos.
 */
@Component
public class TeamStatsObserver implements ETLObserver {

    private static final Logger log = LoggerFactory.getLogger(TeamStatsObserver.class);
    private static final String DEFAULT_SEASON = "current";

    private final TeamRepository teamRepository;
    private final MatchRepository matchRepository;
    private final TeamStatsRepository teamStatsRepository;

    public TeamStatsObserver(TeamRepository teamRepository,
            MatchRepository matchRepository,
            TeamStatsRepository teamStatsRepository) {
        this.teamRepository = teamRepository;
        this.matchRepository = matchRepository;
        this.teamStatsRepository = teamStatsRepository;
    }

    @Override
    public String name() {
        return "team-stats";
    }

    @Override
    @Transactional
    public void onEtlCompleted(EtlEvent event) {
        if (event.teamIds().isEmpty()) {
            return;
        }
        Integer seasonYear = seasonYear(event.season());
        String season = seasonYear != null
                ? SeasonUtil.label(seasonYear)
                : (event.season() == null || event.season().isBlank() ? DEFAULT_SEASON : event.season());
        for (Long teamId : event.teamIds()) {
            teamRepository.findById(teamId).ifPresent(team -> recompute(team, season, seasonYear));
        }
    }

    /**
     * Reconoce "2324" (codigo de football-data), "2023/2024" y "2023" como
     * temporada. Devuelve null si no se puede interpretar (se usan todos los partidos).
     */
    static Integer seasonYear(String season) {
        if (season == null) {
            return null;
        }
        String value = season.trim();
        if (value.matches("[0-9]{4}/[0-9]{4}")) {
            return Integer.parseInt(value.substring(0, 4));
        }
        if (value.matches("[0-9]{4}")) {
            int first = Integer.parseInt(value.substring(0, 2));
            int second = Integer.parseInt(value.substring(2));
            return (first + 1) % 100 == second ? SeasonUtil.startYearFromCode(value) : Integer.parseInt(value);
        }
        return null;
    }

    private void recompute(Team team, String season, Integer seasonYear) {
        List<Match> matches = matchRepository.findByHomeTeamIdOrAwayTeamId(team.getId(), team.getId()).stream()
                .filter(match -> seasonYear == null || (match.getMatchDate() != null
                        && SeasonUtil.startYear(match.getMatchDate()) == seasonYear))
                .toList();

        int played = 0;
        int wins = 0;
        int draws = 0;
        int losses = 0;
        int goalsFor = 0;
        int goalsAgainst = 0;
        int corners = 0;
        int yellowCards = 0;

        for (Match match : matches) {
            Integer homeGoals = match.getFullTimeHomeGoals();
            Integer awayGoals = match.getFullTimeAwayGoals();
            if (homeGoals == null || awayGoals == null) {
                continue;
            }
            boolean isHome = match.getHomeTeam().getId().equals(team.getId());
            int scored = isHome ? homeGoals : awayGoals;
            int conceded = isHome ? awayGoals : homeGoals;

            played++;
            goalsFor += scored;
            goalsAgainst += conceded;
            if (scored > conceded) {
                wins++;
            } else if (scored == conceded) {
                draws++;
            } else {
                losses++;
            }
            if (match.getCorners() != null) {
                corners += match.getCorners();
            }
            if (match.getYellowCards() != null) {
                yellowCards += match.getYellowCards();
            }
        }

        TeamStats stats = teamStatsRepository.findByTeamIdAndSeason(team.getId(), season)
                .orElseGet(() -> TeamStats.builder().team(team).season(season).build());

        stats.setMatchesPlayed(played);
        stats.setWins(wins);
        stats.setDraws(draws);
        stats.setLosses(losses);
        stats.setGoalsFor(goalsFor);
        stats.setGoalsAgainst(goalsAgainst);
        stats.setPoints(wins * 3 + draws);
        stats.setAverageGoalsFor(average(goalsFor, played));
        stats.setAverageGoalsAgainst(average(goalsAgainst, played));
        stats.setAverageCorners(average(corners, played));
        stats.setAverageYellowCards(average(yellowCards, played));

        teamStatsRepository.save(stats);
        log.debug("TeamStats recalculado para {} (temporada {}, {} partidos)", team.getName(), season, played);
    }

    private static double average(int total, int played) {
        return played == 0 ? 0.0 : (double) total / played;
    }
}
