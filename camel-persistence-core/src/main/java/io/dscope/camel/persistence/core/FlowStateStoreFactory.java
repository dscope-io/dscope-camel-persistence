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
            return createRedisBackedStore(configuration, providers, PersistenceBackend.JDBC);
        }
        if (configuration.backend() == PersistenceBackend.REDIS_IC4J) {
            return createRedisBackedStore(configuration, providers, PersistenceBackend.IC4J);
        }

        FlowStateStoreProvider provider = providers.get(configuration.backend());
        if (provider != null) {
            return provider.create(configuration);
        }
        throw new BackendUnavailableException("No FlowStateStoreProvider found for backend " + configuration.backend());
    }

    private static FlowStateStore createRedisBackedStore(
        PersistenceConfiguration configuration,
        Map<PersistenceBackend, FlowStateStoreProvider> providers,
        PersistenceBackend durableBackend
    ) {
        FlowStateStoreProvider redis = providers.get(PersistenceBackend.REDIS);
        FlowStateStoreProvider durable = providers.get(durableBackend);
        if (redis == null || durable == null) {
            throw new BackendUnavailableException(
                "Both REDIS and " + durableBackend + " providers are required for backend " + configuration.backend()
            );
        }
        return new RedisBackedFlowStateStore(
            redis.create(configuration),
            durable.create(configuration)
        );
    }

    private static Map<PersistenceBackend, FlowStateStoreProvider> loadProviders() {
        Map<PersistenceBackend, FlowStateStoreProvider> providers = new EnumMap<>(PersistenceBackend.class);
        for (FlowStateStoreProvider provider : ServiceLoader.load(FlowStateStoreProvider.class)) {
            providers.put(provider.backend(), provider);
        }
        return providers;
    }
}
