package com.ligalytics.patterns.proxy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ligalytics.config.EtlProperties;

public abstract class CachingExternalDataSourceProxy implements ExternalDataSourceService {

    private static final Logger log = LoggerFactory.getLogger(CachingExternalDataSourceProxy.class);

    private final ExternalDataSourceService delegate;
    private final Path cacheDirectory;
    private final Duration cacheTtl;
    private final int maxRetries;
    private final long retryBackoffMillis;
    private final long minIntervalMillis;
    private long lastRequestAtMillis;

    protected CachingExternalDataSourceProxy(ExternalDataSourceService delegate, EtlProperties properties) {
        this.delegate = delegate;
        this.cacheDirectory = Path.of(properties.getCacheDir());
        this.cacheTtl = properties.getCacheTtl();
        this.maxRetries = Math.max(1, properties.getMaxRetries());
        this.retryBackoffMillis = Math.max(0, properties.getRetryBackoffMillis());
        this.minIntervalMillis = properties.getMinRequestInterval() == null
                ? 0
                : Math.max(0, properties.getMinRequestInterval().toMillis());
    }

    protected abstract String cacheExtension();

    @Override
    public String fetch(String url) {
        return fetch(url, false);
    }

    @Override
    public String fetch(String url, boolean forceRefresh) {
        Path cachedFile = cacheFile(url);
        if (!forceRefresh) {
            String cached = readCache(cachedFile);
            if (cached != null) {
                log.debug("[{}] cache HIT {}", sourceName(), url);
                return cached;
            }
        }
        String content = downloadWithRetries(url);
        writeCache(cachedFile, content);
        return content;
    }

    protected String downloadWithRetries(String url) {
        ExternalDataSourceException lastFailure = null;
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                throttle();
                return delegate.fetch(url);
            } catch (ExternalDataSourceException ex) {
                lastFailure = ex;
                log.warn("[{}] intento {}/{} fallido para {}: {}", sourceName(), attempt, maxRetries, url,
                        ex.getMessage());
                if (attempt < maxRetries) {
                    sleep(retryBackoffMillis * attempt);
                }
            }
        }
        throw lastFailure;
    }

    /**
     * Control de frecuencia: garantiza una separacion minima entre peticiones
     * de red a esta fuente (las lecturas de cache no cuentan).
     */
    private synchronized void throttle() {
        long wait = lastRequestAtMillis + minIntervalMillis - System.currentTimeMillis();
        if (wait > 0) {
            sleep(wait);
        }
        lastRequestAtMillis = System.currentTimeMillis();
    }

    private void sleep(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ExternalDataSourceException("Descarga interrumpida para " + sourceName(), ex);
        }
    }

    private Path cacheFile(String url) {
        return cacheDirectory.resolve(sourceName() + "-" + sha256(url) + cacheExtension());
    }

    private String readCache(Path file) {
        if (!Files.isRegularFile(file)) {
            return null;
        }
        if (cacheTtl != null && !cacheTtl.isZero() && !cacheTtl.isNegative()) {
            try {
                Instant expiresAt = Files.getLastModifiedTime(file).toInstant().plus(cacheTtl);
                if (Instant.now().isAfter(expiresAt)) {
                    return null;
                }
            } catch (IOException ex) {
                return null;
            }
        }
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            log.warn("No se pudo leer la cache {}: {}", file, ex.getMessage());
            return null;
        }
    }

    private void writeCache(Path file, String content) {
        try {
            Files.createDirectories(file.getParent());
            Path temp = Files.createTempFile(file.getParent(), "ligalytics-", ".tmp");
            Files.writeString(temp, content, StandardCharsets.UTF_8);
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            log.warn("No se pudo escribir la cache {}: {}", file, ex.getMessage());
        }
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new ExternalDataSourceException("SHA-256 no disponible", ex);
        }
    }
}
