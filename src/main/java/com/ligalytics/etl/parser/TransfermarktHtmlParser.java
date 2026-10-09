package com.ligalytics.etl.parser;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import com.ligalytics.etl.dto.RawTeamMarketValue;

@Component
public class TransfermarktHtmlParser {

    private static final Pattern MARKET_VALUE = Pattern.compile("([\\d.,]+)\\s*(bn|m|th|k)?", Pattern.CASE_INSENSITIVE);

    public List<RawTeamMarketValue> parse(String html) {
        List<RawTeamMarketValue> values = new ArrayList<>();
        if (html == null || html.isBlank()) {
            return values;
        }

        Document document = Jsoup.parse(html);
        for (Element row : document.select("table.items tbody tr, table#yw1 tbody tr")) {
            Element nameLink = row.selectFirst("td.hauptlink a, td:nth-child(2) a");
            Element valueCell = row.selectFirst("td.rechts.hauptlink, td.rechts");
            if (nameLink == null || valueCell == null) {
                continue;
            }
            BigDecimal marketValue = parseMarketValue(valueCell.text());
            if (marketValue == null) {
                continue;
            }
            values.add(new RawTeamMarketValue(nameLink.text().trim(), marketValue));
        }
        return values;
    }

    public BigDecimal parseMarketValue(String raw) {
        if (raw == null) {
            return null;
        }
        String cleaned = raw.replace("\u00a0", " ").trim();
        if (cleaned.isEmpty() || "-".equals(cleaned)) {
            return null;
        }

        Matcher matcher = MARKET_VALUE.matcher(cleaned);
        if (!matcher.find()) {
            return null;
        }

        String number = matcher.group(1);
        if (number.contains(",") && number.contains(".")) {
            number = number.replace(",", "");
        } else if (number.contains(",")) {
            number = number.replace(",", ".");
        }

        BigDecimal value;
        try {
            value = new BigDecimal(number);
        } catch (NumberFormatException ex) {
            return null;
        }

        BigDecimal multiplier = switch (matcher.group(2) == null ? "" : matcher.group(2).toLowerCase(Locale.ROOT)) {
            case "bn" -> BigDecimal.valueOf(1_000_000_000L);
            case "m" -> BigDecimal.valueOf(1_000_000L);
            case "th", "k" -> BigDecimal.valueOf(1_000L);
            default -> BigDecimal.ONE;
        };
        return value.multiply(multiplier).setScale(2, RoundingMode.HALF_UP);
    }
}
