package io.dscope.camel.persistence.jdbc;

import io.dscope.camel.persistence.core.FlowStateStore;
import io.dscope.camel.persistence.core.FlowStateStoreProvider;
import io.dscope.camel.persistence.core.PersistenceBackend;
import io.dscope.camel.persistence.core.PersistenceConfiguration;

public class JdbcFlowStateStoreProvider implements FlowStateStoreProvider {

    @Override
    public PersistenceBackend backend() {
        return PersistenceBackend.JDBC;
    }

    @Override
    public FlowStateStore create(PersistenceConfiguration configuration) {
        return new JdbcFlowStateStore(
            configuration.jdbcUrl(),
            configuration.jdbcUser(),
            configuration.jdbcPassword()
        );
    }
}
