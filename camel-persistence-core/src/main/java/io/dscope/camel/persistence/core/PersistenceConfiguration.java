package io.dscope.camel.persistence.core;

import java.util.Properties;

public record PersistenceConfiguration(
    boolean enabled,
    PersistenceBackend backend,
    RehydrationPolicy rehydrationPolicy,
    String redisUri,
    String redisKeyPrefix,
    String jdbcUrl,
    String jdbcUser,
    String jdbcPassword
) {
    public static final String PERSISTENCE_ENABLED = "camel.persistence.enabled";
    public static final String PERSISTENCE_BACKEND = "camel.persistence.backend";
    public static final String SNAPSHOT_EVERY_EVENTS = "camel.persistence.snapshot-every-events";
    public static final String MAX_REPLAY_EVENTS = "camel.persistence.max-replay-events";
    public static final String READ_BATCH_SIZE = "camel.persistence.read-batch-size";
    public static final String REDIS_URI = "camel.persistence.redis.uri";
    public static final String REDIS_KEY_PREFIX = "camel.persistence.redis.key-prefix";
    public static final String JDBC_URL = "camel.persistence.jdbc.url";
    public static final String JDBC_USER = "camel.persistence.jdbc.user";
    public static final String JDBC_PASSWORD = "camel.persistence.jdbc.password";

    public static PersistenceConfiguration fromProperties(Properties properties) {
        boolean enabled = Boolean.parseBoolean(properties.getProperty(PERSISTENCE_ENABLED, "false"));
        PersistenceBackend backend = PersistenceBackend.parse(properties.getProperty(PERSISTENCE_BACKEND, "redis"));
        int snapshotEvery = Integer.parseInt(properties.getProperty(SNAPSHOT_EVERY_EVENTS, "25"));
        int maxReplay = Integer.parseInt(properties.getProperty(MAX_REPLAY_EVENTS, "500"));
        int readBatch = Integer.parseInt(properties.getProperty(READ_BATCH_SIZE, "200"));
        String redisUri = properties.getProperty(REDIS_URI, "redis://localhost:6379");
        String redisKeyPrefix = properties.getProperty(REDIS_KEY_PREFIX, "camel:state");
        String jdbcUrl = properties.getProperty(JDBC_URL, "jdbc:derby:memory:camelPersistence;create=true");
        String jdbcUser = properties.getProperty(JDBC_USER, "");
        String jdbcPassword = properties.getProperty(JDBC_PASSWORD, "");
        return new PersistenceConfiguration(
            enabled,
            backend,
            new RehydrationPolicy(snapshotEvery, maxReplay, readBatch),
            redisUri,
            redisKeyPrefix,
            jdbcUrl,
            jdbcUser,
            jdbcPassword
        );
    }
}
