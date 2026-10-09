package com.ligalytics.config;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "ligalytics.etl")
public class EtlProperties {

    private String cacheDir = "data/cache";
    private Duration cacheTtl = Duration.ofHours(24);
    private int maxRetries = 3;
    private long retryBackoffMillis = 750;
    private Duration connectTimeout = Duration.ofSeconds(10);
    private Duration readTimeout = Duration.ofSeconds(30);
    /** Separación mínima entre dos peticiones de red a la misma fuente (respeta sus límites). */
    private Duration minRequestInterval = Duration.ofSeconds(1);
    private String userAgent = "LigaLyticsBot/1.0";
    private Map<String, String> baseUrls = new HashMap<>();

    public String getCacheDir() {
        return cacheDir;
    }

    public void setCacheDir(String cacheDir) {
        this.cacheDir = cacheDir;
    }

    public Duration getCacheTtl() {
        return cacheTtl;
    }

    public void setCacheTtl(Duration cacheTtl) {
        this.cacheTtl = cacheTtl;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    public long getRetryBackoffMillis() {
        return retryBackoffMillis;
    }

    public void setRetryBackoffMillis(long retryBackoffMillis) {
        this.retryBackoffMillis = retryBackoffMillis;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public Duration getMinRequestInterval() {
        return minRequestInterval;
    }

    public void setMinRequestInterval(Duration minRequestInterval) {
        this.minRequestInterval = minRequestInterval;
    }

    public Duration getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(Duration readTimeout) {
        this.readTimeout = readTimeout;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public Map<String, String> getBaseUrls() {
        return baseUrls;
    }

    public void setBaseUrls(Map<String, String> baseUrls) {
        this.baseUrls = baseUrls;
    }
}
