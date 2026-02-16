package io.dscope.camel.persistence.core;

public interface FlowStateMachine<T, C> {

    T initialState();

    T applyEvent(T current, PersistedEvent event);

    DecisionResult decide(C command, T current);
}
