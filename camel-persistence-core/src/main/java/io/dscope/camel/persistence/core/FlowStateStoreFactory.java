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

import io.dscope.camel.persistence.core.exception.BackendUnavailableException;
import java.util.EnumMap;
import java.util.Map;
import java.util.ServiceLoader;

public final class FlowStateStoreFactory {

    private FlowStateStoreFactory() {
    }

    public static FlowStateStore create(PersistenceConfiguration configuration) {
        Map<PersistenceBackend, FlowStateStoreProvider> providers = loadProviders();
        if (configuration.backend() == PersistenceBackend.REDIS_JDBC) {
            FlowStateStoreProvider redis = providers.get(PersistenceBackend.REDIS);
            FlowStateStoreProvider jdbc = providers.get(PersistenceBackend.JDBC);
            if (redis == null || jdbc == null) {
                throw new BackendUnavailableException("Both REDIS and JDBC providers are required for backend REDIS_JDBC");
            }
            return new RedisJdbcFlowStateStore(
                redis.create(configuration),
                jdbc.create(configuration)
            );
        }

        FlowStateStoreProvider provider = providers.get(configuration.backend());
        if (provider != null) {
            return provider.create(configuration);
        }
        throw new BackendUnavailableException("No FlowStateStoreProvider found for backend " + configuration.backend());
    }

    private static Map<PersistenceBackend, FlowStateStoreProvider> loadProviders() {
        Map<PersistenceBackend, FlowStateStoreProvider> providers = new EnumMap<>(PersistenceBackend.class);
        for (FlowStateStoreProvider provider : ServiceLoader.load(FlowStateStoreProvider.class)) {
            providers.put(provider.backend(), provider);
        }
        return providers;
    }
}
