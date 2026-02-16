package io.dscope.camel.persistence.core;

import io.dscope.camel.persistence.core.exception.BackendUnavailableException;
import java.util.ServiceLoader;

public final class FlowStateStoreFactory {

    private FlowStateStoreFactory() {
    }

    public static FlowStateStore create(PersistenceConfiguration configuration) {
        for (FlowStateStoreProvider provider : ServiceLoader.load(FlowStateStoreProvider.class)) {
            if (provider.backend() == configuration.backend()) {
                return provider.create(configuration);
            }
        }
        throw new BackendUnavailableException("No FlowStateStoreProvider found for backend " + configuration.backend());
    }
}
