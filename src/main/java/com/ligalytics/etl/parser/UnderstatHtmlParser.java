package com.ligalytics.etl.parser;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ligalytics.etl.dto.RawXg;

@Component
public class UnderstatHtmlParser {

    private static final Logger log = LoggerFactory.getLogger(UnderstatHtmlParser.class);

    private static final Pattern DATES_DATA = Pattern.compile(
            "datesData\\s*=\\s*JSON\\.parse\\('(.*?)'\\)", Pattern.DOTALL);
    private static final Pattern HEX_ESCAPE = Pattern.compile("\\\\x([0-9A-Fa-f]{2})");
    private static final Pattern UNICODE_ESCAPE = Pattern.compile("\\\\u([0-9A-Fa-f]{4})");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ObjectMapper objectMapper = new ObjectMapper();

    public List<RawXg> parse(String html) {
        List<RawXg> result = new ArrayList<>();
        if (html == null || html.isBlank()) {
            return result;
        }

        Matcher matcher = DATES_DATA.matcher(html);
        if (!matcher.find()) {
            return result;
        }

        try {
            JsonNode root = objectMapper.readTree(unescape(matcher.group(1)));
            if (!root.isArray()) {
                return result;
            }
            for (JsonNode node : root) {
                if (!node.path("isResult").asBoolean(false)) {
                    continue;
                }
                String homeTeam = text(node.path("h").path("title"));
                String awayTeam = text(node.path("a").path("title"));
                if (homeTeam == null || awayTeam == null) {
                    continue;
                }
                result.add(new RawXg(
                        homeTeam,
                        awayTeam,
                        decimal(node.path("xG").path("h")),
                        decimal(node.path("xG").path("a")),
                        parseDate(text(node.path("datetime")))));
            }
        } catch (Exception ex) {
            log.warn("No se pudo parsear datesData de Understat: {}", ex.getMessage());
        }
        return result;
    }

    private String text(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        String value = node.asText();
        return value.isBlank() ? null : value.trim();
    }

    private Double decimal(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        try {
            return node.asDouble();
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private LocalDateTime parseDate(String value) {
        if (value == null) {
            return null;
        }
        try {
            return LocalDateTime.parse(value, DATE_TIME);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private String unescape(String raw) {
        String value = raw.replace("\\'", "'").replace("\\\"", "\"").replace("\\/", "/");
        value = decode(HEX_ESCAPE, value, 16);
        value = decode(UNICODE_ESCAPE, value, 16);
        return value.replace("\\\\", "\\");
    }

    private String decode(Pattern pattern, String value, int radix) {
        Matcher matcher = pattern.matcher(value);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            char decoded = (char) Integer.parseInt(matcher.group(1), radix);
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(String.valueOf(decoded)));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }
}
