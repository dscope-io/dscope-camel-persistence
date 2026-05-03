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

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

class PersistenceConfigurationTest {

    @Test
    void readsDefaults() {
        PersistenceConfiguration config = PersistenceConfiguration.fromProperties(new Properties());
        assertEquals(false, config.enabled());
        assertEquals(PersistenceBackend.REDIS, config.backend());
        assertEquals(25, config.rehydrationPolicy().snapshotEveryEvents());
        assertEquals("http://127.0.0.1:4943/", config.icpReplicaUrl());
        assertEquals(true, config.icpFetchRootKey());
        assertEquals(true, config.icpLoadIdl());
    }

    @Test
    void parsesCompositeBackendValue() {
        Properties properties = new Properties();
        properties.setProperty(PersistenceConfiguration.PERSISTENCE_BACKEND, "redis_jdbc");

        PersistenceConfiguration config = PersistenceConfiguration.fromProperties(properties);
        assertEquals(PersistenceBackend.REDIS_JDBC, config.backend());
    }

    @Test
    void parsesRedisIc4jCompositeBackendValue() {
        Properties properties = new Properties();
        properties.setProperty(PersistenceConfiguration.PERSISTENCE_BACKEND, "redis-ic4j");

        PersistenceConfiguration config = PersistenceConfiguration.fromProperties(properties);
        assertEquals(PersistenceBackend.REDIS_IC4J, config.backend());
    }

    @Test
    void readsIcpProperties() {
        Properties properties = new Properties();
        properties.setProperty(PersistenceConfiguration.ICP_REPLICA_URL, "http://localhost:4943/");
        properties.setProperty(PersistenceConfiguration.ICP_CANISTER_ID, "aaaaa-aa");
        properties.setProperty(PersistenceConfiguration.ICP_FETCH_ROOT_KEY, "false");
        properties.setProperty(PersistenceConfiguration.ICP_LOAD_IDL, "false");
        properties.setProperty(PersistenceConfiguration.ICP_IDL_FILE, "src/test/resources/persistence.did");
        properties.setProperty(PersistenceConfiguration.ICP_WAITER_TIMEOUT, "30");
        properties.setProperty(PersistenceConfiguration.ICP_WAITER_SLEEP, "2");

        PersistenceConfiguration config = PersistenceConfiguration.fromProperties(properties);

        assertEquals("http://localhost:4943/", config.icpReplicaUrl());
        assertEquals("aaaaa-aa", config.icpCanisterId());
        assertEquals(false, config.icpFetchRootKey());
        assertEquals(false, config.icpLoadIdl());
        assertEquals("src/test/resources/persistence.did", config.icpIdlFile());
        assertEquals(30, config.icpWaiterTimeout());
        assertEquals(2, config.icpWaiterSleep());
    }
}
