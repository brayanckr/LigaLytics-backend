package com.ligalytics.patterns.proxy;

public class ExternalDataSourceException extends RuntimeException {

    public ExternalDataSourceException(String message) {
        super(message);
    }

    public ExternalDataSourceException(String message, Throwable cause) {
        super(message, cause);
    }
}
