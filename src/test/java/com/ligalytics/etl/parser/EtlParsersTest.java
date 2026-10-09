package com.ligalytics.etl.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.ligalytics.etl.dto.RawMatch;
import com.ligalytics.etl.dto.RawTeamMarketValue;

class EtlParsersTest {

    private final FootballDataCsvParser csvParser = new FootballDataCsvParser();
    private final TransfermarktHtmlParser transfermarktParser = new TransfermarktHtmlParser();

    @Test
    void footballDataCsvIsNormalized() {
        String csv = "Div,Date,Time,HomeTeam,AwayTeam,FTHG,FTAG,FTR,HS,AS,HST,AST,HC,AC,HY,AY,HR,AR\n"
                + "E0,16/08/2024,12:30,Manchester United,Fulham,1,0,H,14,10,6,3,5,7,2,3,0,1\n";

        List<RawMatch> matches = csvParser.parse(csv);

        assertEquals(1, matches.size());
        RawMatch match = matches.get(0);
        assertEquals("E0", match.division());
        assertEquals("Manchester United", match.homeTeam());
        assertEquals("Fulham", match.awayTeam());
        assertEquals(LocalDateTime.of(2024, 8, 16, 12, 30), match.matchDate());
        assertEquals(1, match.homeGoals());
        assertEquals(0, match.awayGoals());
        assertEquals(12, match.corners());
        assertEquals(5, match.yellowCards());
        assertEquals(1, match.redCards());
        assertNull(match.homeXg());
    }

    @Test
    void footballDataSkipsRowsWithoutTeams() {
        String csv = "Div,Date,Time,HomeTeam,AwayTeam,FTHG,FTAG\n"
                + "E0,16/08/2024,12:30,Manchester United,Fulham,1,0\n"
                + "\n";
        assertEquals(1, csvParser.parse(csv).size());
    }

    @Test
    void transfermarktMarketValuesAreParsed() {
        assertEquals(0, new BigDecimal("1200000000.00").compareTo(
                transfermarktParser.parseMarketValue("\u20ac1.20bn")));
        assertEquals(0, new BigDecimal("900000000.00").compareTo(
                transfermarktParser.parseMarketValue("\u20ac900.00m")));
        assertEquals(0, new BigDecimal("500000.00").compareTo(
                transfermarktParser.parseMarketValue("\u20ac500Th.")));
        assertNull(transfermarktParser.parseMarketValue("-"));
    }

    @Test
    void transfermarktHtmlTableIsParsed() {
        String html = "<table class=\"items\"><tbody>"
                + "<tr><td class=\"hauptlink\"><a>Real Madrid</a></td>"
                + "<td class=\"rechts hauptlink\">\u20ac1.20bn</td></tr>"
                + "<tr><td class=\"hauptlink\"><a>FC Barcelona</a></td>"
                + "<td class=\"rechts hauptlink\">\u20ac900.00m</td></tr>"
                + "</tbody></table>";

        List<RawTeamMarketValue> values = transfermarktParser.parse(html);

        assertEquals(2, values.size());
        assertEquals("Real Madrid", values.get(0).teamName());
        assertTrue(values.get(0).marketValue().compareTo(new BigDecimal("1000000000")) > 0);
    }
}
