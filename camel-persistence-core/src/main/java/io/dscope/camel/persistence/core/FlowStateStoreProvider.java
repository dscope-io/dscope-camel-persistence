package io.dscope.camel.persistence.core;

public interface FlowStateStoreProvider {

    PersistenceBackend backend();

    FlowStateStore create(PersistenceConfiguration configuration);
}
