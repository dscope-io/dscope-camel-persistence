/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
    String jdbcPassword,
    String icpReplicaUrl,
    String icpCanisterId,
    boolean icpFetchRootKey,
    boolean icpLoadIdl,
    String icpIdlFile,
    int icpWaiterTimeout,
    int icpWaiterSleep
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
    public static final String ICP_REPLICA_URL = "camel.persistence.icp.replica-url";
    public static final String ICP_CANISTER_ID = "camel.persistence.icp.canister-id";
    public static final String ICP_FETCH_ROOT_KEY = "camel.persistence.icp.fetch-root-key";
    public static final String ICP_LOAD_IDL = "camel.persistence.icp.load-idl";
    public static final String ICP_IDL_FILE = "camel.persistence.icp.idl-file";
    public static final String ICP_WAITER_TIMEOUT = "camel.persistence.icp.waiter-timeout";
    public static final String ICP_WAITER_SLEEP = "camel.persistence.icp.waiter-sleep";

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
        String icpReplicaUrl = properties.getProperty(ICP_REPLICA_URL, "http://127.0.0.1:4943/");
        String icpCanisterId = properties.getProperty(ICP_CANISTER_ID, "");
        boolean icpFetchRootKey = Boolean.parseBoolean(properties.getProperty(ICP_FETCH_ROOT_KEY, "true"));
        boolean icpLoadIdl = Boolean.parseBoolean(properties.getProperty(ICP_LOAD_IDL, "true"));
        String icpIdlFile = properties.getProperty(ICP_IDL_FILE, "");
        int icpWaiterTimeout = Integer.parseInt(properties.getProperty(ICP_WAITER_TIMEOUT, "60"));
        int icpWaiterSleep = Integer.parseInt(properties.getProperty(ICP_WAITER_SLEEP, "5"));
        return new PersistenceConfiguration(
            enabled,
            backend,
            new RehydrationPolicy(snapshotEvery, maxReplay, readBatch),
            redisUri,
            redisKeyPrefix,
            jdbcUrl,
            jdbcUser,
            jdbcPassword,
            icpReplicaUrl,
            icpCanisterId,
            icpFetchRootKey,
            icpLoadIdl,
            icpIdlFile,
            icpWaiterTimeout,
            icpWaiterSleep
        );
    }
}
