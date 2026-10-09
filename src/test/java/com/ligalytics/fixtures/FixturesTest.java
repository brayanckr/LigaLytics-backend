package com.ligalytics.fixtures;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.ligalytics.model.Team;
import com.ligalytics.repository.TeamRepository;

class FixturesTest {

    private static final String JSON = """
            {"matches":[
              {"id":1,"utcDate":"2026-10-10T19:00:00Z","status":"TIMED","matchday":8,
               "homeTeam":{"name":"Club Atlético de Madrid","crest":"a.png"},
               "awayTeam":{"name":"Real Oviedo","crest":"b.png"},
               "score":{"fullTime":{"home":null,"away":null}}},
              {"id":2,"utcDate":"2026-10-10T17:00:00Z","status":"IN_PLAY","matchday":8,
               "homeTeam":{"name":"FC Barcelona"},"awayTeam":{"name":"Equipo Desconocido"},
               "score":{"fullTime":{"home":1,"away":0}}},
              {"id":3,"utcDate":"2026-10-03T17:00:00Z","status":"FINISHED","matchday":7,
               "homeTeam":{"name":"Sevilla FC"},"awayTeam":{"name":"Girona FC"},
               "score":{"fullTime":{"home":2,"away":2}}}
            ]}
            """;

    private final FootballDataOrgParser parser = new FootballDataOrgParser();

    @Test
    void parsesStatusesAndScores() {
        List<Fixture> fixtures = parser.parse(JSON);

        assertEquals(3, fixtures.size());
        assertEquals("SCHEDULED", fixtures.get(0).status());
        assertNull(fixtures.get(0).homeGoals());
        assertEquals("LIVE", fixtures.get(1).status());
        assertEquals(1, fixtures.get(1).homeGoals());
        assertEquals("FINISHED", fixtures.get(2).status());
        assertEquals(8, fixtures.get(0).matchday());
    }

    @Test
    void serviceMapsTeamNamesAndFiltersByDate() {
        FixturesProvider provider = new FixturesProvider() {
            @Override
            public String name() {
                return "fake";
            }

            @Override
            public boolean isConfigured() {
                return true;
            }

            @Override
            public List<Fixture> fetchSeason() {
                return parser.parse(JSON);
            }
        };
        TeamRepository teams = mock(TeamRepository.class);
        when(teams.findAll()).thenReturn(List.of(
                Team.builder().id(1L).name("Atletico Madrid").build(),
                Team.builder().id(2L).name("Oviedo").build(),
                Team.builder().id(3L).name("Barcelona").build()));
        FixturesService service = new FixturesService(provider, teams, mock(com.ligalytics.external.FootballChartsClient.class));

        List<FixtureDto> day = service.between(LocalDate.of(2026, 10, 10), LocalDate.of(2026, 10, 10), ZoneId.of("UTC"));

        assertEquals(2, day.size());
        FixtureDto atletico = day.stream().filter(f -> f.id().equals("1")).findFirst().orElseThrow();
        assertEquals("Atletico Madrid", atletico.homeTeam());
        assertEquals(1L, atletico.homeTeamId());
        assertEquals(2L, atletico.awayTeamId());
        FixtureDto barca = day.stream().filter(f -> f.id().equals("2")).findFirst().orElseThrow();
        assertNull(barca.awayTeamId());

        assertEquals(1, service.live().size());
        assertEquals(Instant.parse("2026-10-10T19:00:00Z"), atletico.utcDate());
    }
}
