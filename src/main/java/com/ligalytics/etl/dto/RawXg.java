package com.ligalytics.etl.dto;

import java.time.LocalDateTime;

public record RawXg(
        String homeTeam,
        String awayTeam,
        Double homeXg,
        Double awayXg,
        LocalDateTime matchDate
) {
}
