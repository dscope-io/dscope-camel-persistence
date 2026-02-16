package io.dscope.camel.persistence.core;

public enum PersistenceBackend {
    REDIS,
    JDBC,
    IC4J;

    public static PersistenceBackend parse(String value) {
        if (value == null || value.isBlank()) {
            return REDIS;
        }
        return PersistenceBackend.valueOf(value.trim().toUpperCase());
    }
}
