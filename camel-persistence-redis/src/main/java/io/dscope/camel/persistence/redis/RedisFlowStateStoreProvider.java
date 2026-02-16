package io.dscope.camel.persistence.redis;

import io.dscope.camel.persistence.core.FlowStateStore;
import io.dscope.camel.persistence.core.FlowStateStoreProvider;
import io.dscope.camel.persistence.core.PersistenceBackend;
import io.dscope.camel.persistence.core.PersistenceConfiguration;

public class RedisFlowStateStoreProvider implements FlowStateStoreProvider {

    @Override
    public PersistenceBackend backend() {
        return PersistenceBackend.REDIS;
    }

    @Override
    public FlowStateStore create(PersistenceConfiguration configuration) {
        return new RedisFlowStateStore(configuration.redisUri(), configuration.redisKeyPrefix());
    }
}
