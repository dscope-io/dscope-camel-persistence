package io.dscope.camel.persistence.core;

import com.fasterxml.jackson.databind.JsonNode;

public interface FlowStateSerializer<T> {

    JsonNode serialize(T state);

    T deserialize(JsonNode json);
}
