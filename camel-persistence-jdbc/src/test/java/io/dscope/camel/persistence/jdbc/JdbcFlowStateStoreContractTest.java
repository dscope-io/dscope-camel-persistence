package io.dscope.camel.persistence.jdbc;

import io.dscope.camel.persistence.core.FlowStateStore;
import io.dscope.camel.persistence.testkit.FlowStateStoreContractSuite;
import java.util.UUID;

class JdbcFlowStateStoreContractTest extends FlowStateStoreContractSuite {

    @Override
    protected FlowStateStore createStore() {
        String dbName = "camelPersistenceContract" + UUID.randomUUID().toString().replace("-", "");
        return new JdbcFlowStateStore("jdbc:derby:memory:" + dbName + ";create=true", "", "");
    }

    @Override
    protected String flowType() {
        return "jdbc.contract.flow";
    }
}
