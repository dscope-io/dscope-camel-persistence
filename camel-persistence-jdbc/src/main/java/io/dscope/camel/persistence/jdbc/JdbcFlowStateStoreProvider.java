package io.dscope.camel.persistence.jdbc;

import io.dscope.camel.persistence.core.FlowStateStore;
import io.dscope.camel.persistence.core.FlowStateStoreProvider;
import io.dscope.camel.persistence.core.PersistenceBackend;
import io.dscope.camel.persistence.core.PersistenceConfiguration;
import io.dscope.camel.persistence.core.exception.BackendUnavailableException;

public class JdbcFlowStateStoreProvider implements FlowStateStoreProvider {

    @Override
    public PersistenceBackend backend() {
        return PersistenceBackend.JDBC;
    }

    @Override
    public FlowStateStore create(PersistenceConfiguration configuration) {
        throw new BackendUnavailableException("JDBC backend scaffolded in Phase 1, implementation scheduled for Phase 2");
    }
}
