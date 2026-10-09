package com.ligalytics.etl.parser;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Comment;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import com.ligalytics.etl.dto.RawMatch;

@Component
public class FBrefHtmlParser {

    private static final DateTimeFormatter[] DATE_FORMATS = {
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd"),
            DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH)
    };

    private static final String SCORE_SEPARATOR = "[\\u2013\\u2014-]";

    public List<RawMatch> parse(String html) {
        List<RawMatch> matches = new ArrayList<>();
        if (html == null || html.isBlank()) {
            return matches;
        }

        Document document = Jsoup.parse(html);
        List<Element> tables = new ArrayList<>(document.select("table"));
        List<Comment> comments = new ArrayList<>();
        collectComments(document, comments);
        for (Comment comment : comments) {
            String data = comment.getData();
            if (data.contains("<table")) {
                tables.addAll(Jsoup.parse(data).select("table"));
            }
        }

        for (Element table : tables) {
            for (Element row : table.select("tbody tr")) {
                Element homeCell = row.selectFirst("td[data-stat=home_team]");
                Element awayCell = row.selectFirst("td[data-stat=away_team]");
                if (homeCell == null || awayCell == null) {
                    continue;
                }

                String homeTeam = homeCell.text().trim();
                String awayTeam = awayCell.text().trim();
                if (homeTeam.isEmpty() || awayTeam.isEmpty()) {
                    continue;
                }

                Integer[] goals = parseScore(row.selectFirst("td[data-stat=score]"));
                LocalDateTime date = parseDate(row.selectFirst("td[data-stat=date], th[data-stat=date]"));

                matches.add(new RawMatch(
                        null,
                        date,
                        homeTeam,
                        awayTeam,
                        goals[0],
                        goals[1],
                        null,
                        null,
                        null,
                        null,
                        decimal(row.selectFirst("td[data-stat=home_xg]")),
                        decimal(row.selectFirst("td[data-stat=away_xg]")),
                        null,
                        null,
                        null));
            }
        }
        return matches;
    }

    private Integer[] parseScore(Element scoreCell) {
        Integer[] goals = { null, null };
        if (scoreCell == null) {
            return goals;
        }
        String[] parts = scoreCell.text().trim().split(SCORE_SEPARATOR);
        if (parts.length == 2) {
            goals[0] = parseInt(parts[0]);
            goals[1] = parseInt(parts[1]);
        }
        return goals;
    }

    private Integer parseInt(String value) {
        if (value == null) {
            return null;
        }
        try {
            return Integer.valueOf(value.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Double decimal(Element cell) {
        if (cell == null) {
            return null;
        }
        String text = cell.text().trim();
        if (text.isEmpty() || "-".equals(text)) {
            return null;
        }
        try {
            return Double.valueOf(text);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private LocalDateTime parseDate(Element cell) {
        if (cell == null) {
            return null;
        }
        String text = cell.text().trim();
        for (DateTimeFormatter formatter : DATE_FORMATS) {
            try {
                return LocalDate.parse(text, formatter).atStartOfDay();
            } catch (DateTimeParseException ex) {
                // probar el siguiente formato
            }
        }
        return null;
    }

    private void collectComments(Node node, List<Comment> comments) {
        for (Node child : node.childNodes()) {
            if (child instanceof Comment comment) {
                comments.add(comment);
            }
            collectComments(child, comments);
        }
    }
}
