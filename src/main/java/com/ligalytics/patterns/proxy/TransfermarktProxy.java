package com.ligalytics.patterns.proxy;

import org.springframework.stereotype.Component;

import com.ligalytics.config.EtlProperties;

@Component
public class TransfermarktProxy extends CachingExternalDataSourceProxy {

    public static final String SOURCE = "transfermarkt";
    private static final String DEFAULT_BASE_URL = "https://www.transfermarkt.com";

    private final String baseUrl;

    public TransfermarktProxy(HttpExternalDataSource delegate, EtlProperties properties) {
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

    public String pageUrl(String path) {
        String normalized = path.startsWith("/") ? path : "/" + path;
        return baseUrl + normalized;
    }

    public String fetchPage(String path) {
        return fetch(pageUrl(path));
    }
}
