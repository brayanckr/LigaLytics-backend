package com.ligalytics.patterns.proxy;

public interface ExternalDataSourceService {

    String sourceName();

    String fetch(String url);

    default String fetch(String url, boolean forceRefresh) {
        return fetch(url);
    }
}
