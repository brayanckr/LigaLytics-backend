package com.ligalytics.patterns.proxy;

import org.springframework.stereotype.Component;

import com.ligalytics.config.EtlProperties;

@Component
public class UnderstatProxy extends CachingExternalDataSourceProxy {

    public static final String SOURCE = "understat";
    private static final String DEFAULT_BASE_URL = "https://understat.com";

    private final String baseUrl;

    public UnderstatProxy(HttpExternalDataSource delegate, EtlProperties properties) {
        super(delegate, properties);
        this.baseUrl = properties.getBaseUrls().getOrDefault(SOURCE, DEFAULT_BASE_URL);
    }

    @Override
    public String sourceName() {
        return SOURCE;
    }

    @Override
    protected String cacheExtension() {
        return ".html";
    }

    public String leagueUrl(String leagueCode, String season) {
        return baseUrl + "/league/" + leagueCode + "/" + season;
    }

    public String fetchLeague(String leagueCode, String season) {
        return fetch(leagueUrl(leagueCode, season));
    }
}
