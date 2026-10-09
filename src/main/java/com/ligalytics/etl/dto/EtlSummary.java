package com.ligalytics.etl.dto;

public record EtlSummary(
        String source,
        String reference,
        int fetched,
        int parsed,
        int created,
        int updated,
        int skipped,
        String location
) {
}
