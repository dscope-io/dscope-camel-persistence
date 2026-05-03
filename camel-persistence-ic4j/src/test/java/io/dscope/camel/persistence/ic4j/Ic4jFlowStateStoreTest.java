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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.dscope.camel.persistence.core.AppendResult;
import io.dscope.camel.persistence.core.PersistedEvent;
import io.dscope.camel.persistence.core.RehydratedState;
import io.dscope.camel.persistence.core.StateEnvelope;
import io.dscope.camel.persistence.core.exception.OptimisticConflictException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class Ic4jFlowStateStoreTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void appendEventsCallsCanisterAndMapsAppendResult() {
        StubClient client = new StubClient();
        client.updateResponse = Map.of("previousVersion", 1L, "nextVersion", 2L, "duplicate", false);
        Ic4jFlowStateStore store = new Ic4jFlowStateStore(client);

        AppendResult result = store.appendEvents("order", "id-1", 1L, List.of(event("order", "id-1", 0L)), "cmd-1");

        assertEquals("appendEvents", client.updateMethod);
        assertEquals(2L, result.nextVersion());
        assertEquals(false, result.duplicate());
    }

    @Test
    void rehydrateMapsCanisterState() {
        StubClient client = new StubClient();
        client.queryResponse = new RehydratedState(
            new StateEnvelope("order", "id-1", 2L, 1L, MAPPER.createObjectNode().put("state", "created"), Instant.now().toString(), Map.of()),
            List.of(event("order", "id-1", 2L))
        );
        Ic4jFlowStateStore store = new Ic4jFlowStateStore(client);

        RehydratedState state = store.rehydrate("order", "id-1");

        assertEquals("rehydrate", client.queryMethod);
        assertEquals(2L, state.envelope().version());
        assertEquals(1, state.tailEvents().size());
    }

    @Test
    void rehydrateMapsJsonStringCanisterState() {
        StubClient client = new StubClient();
        client.queryResponse = Map.of(
            "envelope", Map.of(
                "flowType", "order",
                "flowId", "id-1",
                "version", 2L,
                "snapshotVersion", 1L,
                "snapshotJson", "{\"state\":\"created\"}",
                "lastUpdatedAt", "123",
                "metadataJson", "{\"snapshot\":true}"
            ),
            "tailEvents", List.of(Map.of(
                "eventId", "evt-2",
                "flowType", "order",
                "flowId", "id-1",
                "sequence", 2L,
                "eventType", "updated",
                "payloadJson", "{\"sequence\":2}",
                "occurredAt", "123",
                "idempotencyKey", "cmd-2"
            ))
        );
        Ic4jFlowStateStore store = new Ic4jFlowStateStore(client);

        RehydratedState state = store.rehydrate("order", "id-1");

        assertEquals("created", state.envelope().snapshot().path("state").asText());
        assertEquals(true, state.envelope().metadata().get("snapshot"));
        assertEquals(2L, state.tailEvents().get(0).payload().path("sequence").asLong());
    }


    @Test
    void readEventsMapsEventList() {
        StubClient client = new StubClient();
        client.queryResponse = List.of(event("order", "id-1", 2L));
        Ic4jFlowStateStore store = new Ic4jFlowStateStore(client);

        List<PersistedEvent> events = store.readEvents("order", "id-1", 1L, 10);

        assertEquals("readEvents", client.queryMethod);
        assertEquals(1, events.size());
        assertEquals(2L, events.get(0).sequence());
    }

    @Test
    void readEventsMapsJsonStringCanisterEvents() {
        StubClient client = new StubClient();
        client.queryResponse = List.of(Map.of(
            "eventId", "evt-2",
            "flowType", "order",
            "flowId", "id-1",
            "sequence", 2L,
            "eventType", "updated",
            "payloadJson", "{\"sequence\":2}",
            "occurredAt", "123",
            "idempotencyKey", "cmd-2"
        ));
        Ic4jFlowStateStore store = new Ic4jFlowStateStore(client);

        List<PersistedEvent> events = store.readEvents("order", "id-1", 1L, 10);

        assertEquals(1, events.size());
        assertEquals(2L, events.get(0).payload().path("sequence").asLong());
    }


    @Test
    void mapsOptimisticConflictEnvelope() {
        StubClient client = new StubClient();
        client.updateResponse = Map.of("error", Map.of("code", "OPTIMISTIC_CONFLICT", "message", "expected version mismatch"));
        Ic4jFlowStateStore store = new Ic4jFlowStateStore(client);

        assertThrows(
            OptimisticConflictException.class,
            () -> store.appendEvents("order", "id-1", 1L, List.of(event("order", "id-1", 2L)), "cmd-2")
        );
    }

    private static PersistedEvent event(String flowType, String flowId, long sequence) {
        return new PersistedEvent(
            "evt-" + sequence,
            flowType,
            flowId,
            sequence,
            "updated",
            MAPPER.createObjectNode().put("sequence", sequence),
            Instant.now().toString(),
            "key-" + sequence
        );
    }

    private static final class StubClient implements Ic4jPersistenceClient {
        String updateMethod;
        String queryMethod;
        Object updateResponse;
        Object queryResponse;

        @Override
        public Object update(String method, Object request) {
            updateMethod = method;
            return updateResponse;
        }

        @Override
        public Object query(String method, Object request) {
            queryMethod = method;
            return queryResponse;
        }
    }
}