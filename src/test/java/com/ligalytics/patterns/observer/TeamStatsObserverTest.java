package com.ligalytics.patterns.observer;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDateTime;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import com.ligalytics.model.Match;
import com.ligalytics.model.Team;
import com.ligalytics.model.TeamStats;
import com.ligalytics.repository.MatchRepository;
import com.ligalytics.repository.TeamRepository;
import com.ligalytics.repository.TeamStatsRepository;

@DataJpaTest
@Import(TeamStatsObserver.class)
class TeamStatsObserverTest {

    @Autowired
    private TeamStatsObserver observer;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private MatchRepository matchRepository;

    @Autowired
    private TeamStatsRepository teamStatsRepository;

    @Test
    void recomputesTeamStatsFromPersistedMatches() {
        Team realMadrid = teamRepository.save(Team.builder().name("Real Madrid").build());
        Team barcelona = teamRepository.save(Team.builder().name("FC Barcelona").build());

        matchRepository.save(Match.builder()
                .matchDate(LocalDateTime.of(2025, 3, 1, 21, 0))
                .homeTeam(realMadrid).awayTeam(barcelona)
                .fullTimeHomeGoals(2).fullTimeAwayGoals(1)
                .corners(11).yellowCards(4)
                .build());
        matchRepository.save(Match.builder()
                .matchDate(LocalDateTime.of(2025, 3, 8, 21, 0))
                .homeTeam(barcelona).awayTeam(realMadrid)
                .fullTimeHomeGoals(0).fullTimeAwayGoals(0)
                .corners(9).yellowCards(6)
                .build());

        EtlEvent event = EtlEvent.of("football-data", "E0-2025", "2024/2025",
                Set.of(realMadrid.getId(), barcelona.getId()), 2, 0, 0, "https://example.com");

        observer.onEtlCompleted(event);

        TeamStats madridStats = teamStatsRepository
                .findByTeamIdAndSeason(realMadrid.getId(), "2024/2025").orElseThrow();
        assertEquals(2, madridStats.getMatchesPlayed());
        assertEquals(1, madridStats.getWins());
        assertEquals(1, madridStats.getDraws());
        assertEquals(0, madridStats.getLosses());
        assertEquals(2, madridStats.getGoalsFor());
        assertEquals(1, madridStats.getGoalsAgainst());
        assertEquals(4, madridStats.getPoints());
        assertEquals(10.0, madridStats.getAverageCorners(), 1e-9);
        assertEquals(5.0, madridStats.getAverageYellowCards(), 1e-9);

        TeamStats barcelonaStats = teamStatsRepository
                .findByTeamIdAndSeason(barcelona.getId(), "2024/2025").orElseThrow();
        assertEquals(2, barcelonaStats.getMatchesPlayed());
        assertEquals(0, barcelonaStats.getWins());
        assertEquals(1, barcelonaStats.getDraws());
        assertEquals(1, barcelonaStats.getLosses());
        assertEquals(1, barcelonaStats.getGoalsFor());
        assertEquals(2, barcelonaStats.getGoalsAgainst());
        assertEquals(1, barcelonaStats.getPoints());
    }

    @Test
    void ignoresEventWithoutAffectedTeams() {
        observer.onEtlCompleted(EtlEvent.of("test", "ref", "2024", Set.of(), 0, 0, 5, "url"));

        assertEquals(0, teamStatsRepository.count());
    }
}
