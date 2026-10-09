package com.ligalytics.patterns.proxy;

import org.springframework.stereotype.Component;

import com.ligalytics.config.EtlProperties;

@Component
public class FootballDataProxy extends CachingExternalDataSourceProxy {

    public static final String SOURCE = "football-data";
    private static final String DEFAULT_BASE_URL = "https://www.football-data.co.uk";

    private final String baseUrl;

    public FootballDataProxy(HttpExternalDataSource delegate, EtlProperties properties) {
        super(delegate, properties);
        this.baseUrl = properties.getBaseUrls().getOrDefault(SOURCE, DEFAULT_BASE_URL);
    }

    @Override
    public String sourceName() {
        return SOURCE;
    }

    @Override
    protected String cacheExtension() {
        return ".csv";
    }

    public String seasonUrl(String seasonCode, String divisionCode) {
        return baseUrl + "/mmz4281/" + seasonCode + "/" + divisionCode + ".csv";
    }

    public String fetchSeason(String seasonCode, String divisionCode) {
        return fetch(seasonUrl(seasonCode, divisionCode));
    }

    public String fetchSeason(String seasonCode, String divisionCode, boolean forceRefresh) {
        return fetch(seasonUrl(seasonCode, divisionCode), forceRefresh);
    }
}
