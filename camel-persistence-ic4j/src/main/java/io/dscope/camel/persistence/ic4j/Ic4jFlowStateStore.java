/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.dscope.camel.persistence.ic4j;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.dscope.camel.persistence.core.AppendResult;
import io.dscope.camel.persistence.core.FlowStateStore;
import io.dscope.camel.persistence.core.PersistedEvent;
import io.dscope.camel.persistence.core.RehydratedState;
import io.dscope.camel.persistence.core.StateEnvelope;
import io.dscope.camel.persistence.core.exception.BackendUnavailableException;
import io.dscope.camel.persistence.core.exception.OptimisticConflictException;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public class Ic4jFlowStateStore implements FlowStateStore {

    private static final String OPTIMISTIC_CONFLICT = "OPTIMISTIC_CONFLICT";

    private final Ic4jPersistenceClient client;
    private final ObjectMapper mapper;

    Ic4jFlowStateStore(Ic4jPersistenceClient client) {
        this(client, new ObjectMapper());
    }

    Ic4jFlowStateStore(Ic4jPersistenceClient client, ObjectMapper mapper) {
        this.client = client;
        this.mapper = mapper;
    }

    @Override
    public RehydratedState rehydrate(String flowType, String flowId) {
        Object response = callQuery("rehydrate", Map.of("flowType", flowType, "flowId", flowId));
        if (response == null) {
            return emptyState(flowType, flowId);
        }
        JsonNode node = mapper.valueToTree(response);
        if (node.path("envelope").has("snapshotJson")) {
            return fromIcpState(convert(response, IcpRehydratedState.class));
        }
        return convert(response, RehydratedState.class);
    }

    @Override
    public AppendResult appendEvents(String flowType, String flowId, long expectedVersion, List<PersistedEvent> events, String idempotencyKey) {
        Object response = callUpdate(
            "appendEvents",
            new AppendEventsRequest(flowType, flowId, expectedVersion, toIcpEvents(events, idempotencyKey), idempotencyKey == null ? "" : idempotencyKey)
        );
        return convert(response, AppendResult.class);
    }

    @Override
    public void writeSnapshot(String flowType, String flowId, long version, JsonNode snapshotJson, Map<String, Object> metadata) {
        callUpdate(
            "writeSnapshot",
            new WriteSnapshotRequest(flowType, flowId, version, writeJson(snapshotJson), writeJson(metadata))
        );
    }

    @Override
    public List<PersistedEvent> readEvents(String flowType, String flowId, long afterVersion, int limit) {
        Object response = callQuery(
            "readEvents",
            Map.of("flowType", flowType, "flowId", flowId, "afterVersion", afterVersion, "limit", limit)
        );
        JsonNode node = mapper.valueToTree(response);
        if (node.isArray() && !node.isEmpty() && node.get(0).has("payloadJson")) {
            List<IcpEventRecord> icpEvents = mapper.convertValue(
                response,
                mapper.getTypeFactory().constructCollectionType(List.class, IcpEventRecord.class)
            );
            return icpEvents.stream().map(this::fromIcpEvent).toList();
        }
        return mapper.convertValue(response, mapper.getTypeFactory().constructCollectionType(List.class, PersistedEvent.class));
    }

    private Object callUpdate(String method, Object request) {
        return unwrap(client.update(method, request));
    }

    private Object callQuery(String method, Object request) {
        return unwrap(client.query(method, request));
    }

    private Object unwrap(Object response) {
        JsonNode node = mapper.valueToTree(response);
        if (node == null || node.isNull()) {
            return null;
        }
        JsonNode error = node.path("error");
        if (error.isObject()) {
            String code = error.path("code").asText();
            String message = error.path("message").asText("IC4J persistence call failed");
            if (OPTIMISTIC_CONFLICT.equals(code)) {
                throw new OptimisticConflictException(message);
            }
            throw new BackendUnavailableException(message);
        }
        JsonNode result = node.path("result");
        if (!result.isMissingNode()) {
            return result;
        }
        return response;
    }

    private <T> T convert(Object response, Class<T> type) {
        return mapper.convertValue(response, type);
    }

    private List<IcpEventRecord> toIcpEvents(List<PersistedEvent> events, String idempotencyKey) {
        String normalizedKey = idempotencyKey == null ? "" : idempotencyKey;
        return events.stream()
            .map(event -> new IcpEventRecord(
                event.eventId(),
                event.flowType(),
                event.flowId(),
                event.sequence(),
                event.eventType(),
                writeJson(event.payload()),
                event.occurredAt(),
                event.idempotencyKey() == null ? normalizedKey : event.idempotencyKey()
            ))
            .toList();
    }

    private PersistedEvent fromIcpEvent(IcpEventRecord event) {
        return new PersistedEvent(
            event.eventId(),
            event.flowType(),
            event.flowId(),
            event.sequence(),
            event.eventType(),
            readJson(event.payloadJson()),
            event.occurredAt(),
            event.idempotencyKey().isBlank() ? null : event.idempotencyKey()
        );
    }

    private RehydratedState fromIcpState(IcpRehydratedState state) {
        IcpStateEnvelope envelope = state.envelope();
        StateEnvelope coreEnvelope = new StateEnvelope(
            envelope.flowType(),
            envelope.flowId(),
            envelope.version(),
            envelope.snapshotVersion(),
            readJson(envelope.snapshotJson()),
            envelope.lastUpdatedAt(),
            readMetadata(envelope.metadataJson())
        );
        return new RehydratedState(coreEnvelope, state.tailEvents().stream().map(this::fromIcpEvent).toList());
    }

    private String writeJson(Object value) {
        try {
            return mapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (Exception ex) {
            throw new BackendUnavailableException("Unable to serialize IC4J persistence JSON", ex);
        }
    }

    private JsonNode readJson(String json) {
        try {
            return mapper.readTree(json == null || json.isBlank() ? "{}" : json);
        } catch (Exception ex) {
            throw new BackendUnavailableException("Unable to deserialize IC4J persistence JSON", ex);
        }
    }

    private Map<String, Object> readMetadata(String json) {
        return mapper.convertValue(
            readJson(json),
            mapper.getTypeFactory().constructMapType(Map.class, String.class, Object.class)
        );
    }

    private RehydratedState emptyState(String flowType, String flowId) {
        ObjectNode snapshot = mapper.createObjectNode();
        StateEnvelope envelope = new StateEnvelope(flowType, flowId, 0L, 0L, snapshot, Instant.EPOCH.toString(), Map.of());
        return new RehydratedState(envelope, List.of());
    }

    record AppendEventsRequest(String flowType, String flowId, long expectedVersion, List<IcpEventRecord> events, String idempotencyKey) {
    }

    record WriteSnapshotRequest(String flowType, String flowId, long version, String snapshotJson, String metadataJson) {
    }

    record IcpEventRecord(
        String eventId,
        String flowType,
        String flowId,
        long sequence,
        String eventType,
        String payloadJson,
        String occurredAt,
        String idempotencyKey
    ) {
    }

    record IcpStateEnvelope(
        String flowType,
        String flowId,
        long version,
        long snapshotVersion,
        String snapshotJson,
        String lastUpdatedAt,
        String metadataJson
    ) {
    }

    record IcpRehydratedState(IcpStateEnvelope envelope, List<IcpEventRecord> tailEvents) {
    }
}