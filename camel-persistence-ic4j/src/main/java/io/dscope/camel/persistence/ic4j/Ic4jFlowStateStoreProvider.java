package io.dscope.camel.persistence.ic4j;

import io.dscope.camel.persistence.core.FlowStateStore;
import io.dscope.camel.persistence.core.FlowStateStoreProvider;
import io.dscope.camel.persistence.core.PersistenceBackend;
import io.dscope.camel.persistence.core.PersistenceConfiguration;
import io.dscope.camel.persistence.core.exception.BackendUnavailableException;

public class Ic4jFlowStateStoreProvider implements FlowStateStoreProvider {

    @Override
    public PersistenceBackend backend() {
        return PersistenceBackend.IC4J;
    }

    @Override
    public FlowStateStore create(PersistenceConfiguration configuration) {
        throw new BackendUnavailableException("IC4J backend scaffolded in Phase 1, implementation scheduled for Phase 3");
    }
}
