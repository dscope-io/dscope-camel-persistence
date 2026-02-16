package io.dscope.camel.persistence.core;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;

public interface FlowStateStore {

    RehydratedState rehydrate(String flowType, String flowId);

    AppendResult appendEvents(
        String flowType,
        String flowId,
        long expectedVersion,
        List<PersistedEvent> events,
        String idempotencyKey
    );

    void writeSnapshot(String flowType, String flowId, long version, JsonNode snapshotJson, Map<String, Object> metadata);

    List<PersistedEvent> readEvents(String flowType, String flowId, long afterVersion, int limit);
}
