package com.ligalytics.patterns.proxy;

import java.io.IOException;

import org.springframework.stereotype.Component;

import com.ligalytics.config.EtlProperties;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

@Component
public class HttpExternalDataSource implements ExternalDataSourceService {

    private final OkHttpClient client;
    private final String userAgent;

    public HttpExternalDataSource(EtlProperties properties) {
        this.userAgent = properties.getUserAgent();
        this.client = new OkHttpClient.Builder()
                .connectTimeout(properties.getConnectTimeout())
                .readTimeout(properties.getReadTimeout())
                .followRedirects(true)
                .build();
    }

    @Override
    public String sourceName() {
        return "http";
    }

    @Override
    public String fetch(String url) {
        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", userAgent)
                .header("Accept", "*/*")
                .get()
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new ExternalDataSourceException("HTTP " + response.code() + " al descargar " + url);
            }
            ResponseBody body = response.body();
            if (body == null) {
                throw new ExternalDataSourceException("Respuesta vacia al descargar " + url);
            }
            return body.string();
        } catch (IOException ex) {
            throw new ExternalDataSourceException("Fallo de red al descargar " + url, ex);
        }
    }
}
