package com.ligalytics.etl.parser;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.ligalytics.etl.dto.RawMatch;

@Component
public class FootballDataCsvParser {

    private static final DateTimeFormatter[] DATE_FORMATS = {
            DateTimeFormatter.ofPattern("d/M/yyyy"),
            DateTimeFormatter.ofPattern("d/M/yy"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd")
    };

    private static final DateTimeFormatter[] TIME_FORMATS = {
            DateTimeFormatter.ofPattern("H:mm"),
            DateTimeFormatter.ofPattern("HH:mm")
    };

    public List<RawMatch> parse(String csv) {
        List<RawMatch> matches = new ArrayList<>();
        if (csv == null || csv.isBlank()) {
            return matches;
        }

        Map<String, Integer> header = null;
        for (String line : csv.split("\\r?\\n")) {
            if (line == null || line.isBlank()) {
                continue;
            }
            List<String> cells = splitCsv(line);
            if (header == null) {
                Map<String, Integer> candidate = buildHeader(cells);
                if (candidate.containsKey("Date") && candidate.containsKey("HomeTeam")
                        && candidate.containsKey("AwayTeam")) {
                    header = candidate;
                }
                continue;
            }

            String homeTeam = string(cells, header, "HomeTeam");
            String awayTeam = string(cells, header, "AwayTeam");
            if (homeTeam == null || awayTeam == null) {
                continue;
            }

            LocalDateTime date = parseDate(string(cells, header, "Date"), string(cells, header, "Time"));
            if (date == null) {
                continue;
            }

            Integer corners = sum(intValue(cells, header, "HC"), intValue(cells, header, "AC"));
            Integer homeYellow = intValue(cells, header, "HY");
            Integer awayYellow = intValue(cells, header, "AY");
            Integer homeRed = intValue(cells, header, "HR");
            Integer awayRed = intValue(cells, header, "AR");
            Integer yellow = sum(homeYellow, awayYellow);
            Integer red = sum(homeRed, awayRed);

            matches.add(new RawMatch(
                    string(cells, header, "Div"),
                    date,
                    homeTeam,
                    awayTeam,
                    intValue(cells, header, "FTHG"),
                    intValue(cells, header, "FTAG"),
                    intValue(cells, header, "HS"),
                    intValue(cells, header, "AS"),
                    intValue(cells, header, "HST"),
                    intValue(cells, header, "AST"),
                    null,
                    null,
                    corners,
                    yellow,
                    red,
                    homeYellow,
                    awayYellow,
                    homeRed,
                    awayRed,
                    intValue(cells, header, "HF"),
                    intValue(cells, header, "AF")));
        }
        return matches;
    }

    private Map<String, Integer> buildHeader(List<String> cells) {
        Map<String, Integer> header = new HashMap<>();
        for (int i = 0; i < cells.size(); i++) {
            String name = cells.get(i).replace("\uFEFF", "").trim();
            if (!name.isEmpty()) {
                header.putIfAbsent(name, i);
            }
        }
        return header;
    }

    private List<String> splitCsv(String line) {
        List<String> cells = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if (c == ',' && !quoted) {
                cells.add(current.toString().trim());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        cells.add(current.toString().trim());
        return cells;
    }

    private String string(List<String> cells, Map<String, Integer> header, String column) {
        Integer index = header.get(column);
        if (index == null || index >= cells.size()) {
            return null;
        }
        String value = cells.get(index);
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    private Integer intValue(List<String> cells, Map<String, Integer> header, String column) {
        String raw = string(cells, header, column);
        if (raw == null) {
            return null;
        }
        try {
            return Integer.valueOf(raw);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Integer sum(Integer a, Integer b) {
        if (a == null && b == null) {
            return null;
        }
        return (a == null ? 0 : a) + (b == null ? 0 : b);
    }

    private LocalDateTime parseDate(String rawDate, String rawTime) {
        if (rawDate == null) {
            return null;
        }
        LocalDate date = null;
        for (DateTimeFormatter formatter : DATE_FORMATS) {
            try {
                date = LocalDate.parse(rawDate, formatter);
                break;
            } catch (DateTimeParseException ex) {
                // probar el siguiente formato
            }
        }
        if (date == null) {
            return null;
        }

        LocalTime time = LocalTime.NOON;
        if (rawTime != null) {
            String normalized = rawTime.trim().toUpperCase(Locale.ROOT);
            for (DateTimeFormatter formatter : TIME_FORMATS) {
                try {
                    time = LocalTime.parse(normalized, formatter);
                    break;
                } catch (DateTimeParseException ex) {
                    // probar el siguiente formato
                }
            }
        }
        return LocalDateTime.of(date, time);
    }
}
