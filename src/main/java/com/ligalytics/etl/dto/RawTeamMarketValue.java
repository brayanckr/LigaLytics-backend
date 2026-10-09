package com.ligalytics.etl.dto;

import java.math.BigDecimal;

public record RawTeamMarketValue(
        String teamName,
        BigDecimal marketValue
) {
}
