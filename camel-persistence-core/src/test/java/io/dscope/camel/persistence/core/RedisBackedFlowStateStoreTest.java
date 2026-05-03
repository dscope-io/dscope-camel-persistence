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
package io.dscope.camel.persistence.core;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class RedisBackedFlowStateStoreTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void rehydrateFallsBackToDurableStoreAndWarmsRedisOnCacheMiss() {
        StubStore redis = new StubStore();
        StubStore durable = new StubStore();

        redis.rehydrateResult = emptyState("order", "id-1");
        durable.rehydrateResult = stateWithTail("order", "id-1", 3L, 1L);

        RedisBackedFlowStateStore store = new RedisBackedFlowStateStore(redis, durable);
        RehydratedState resolved = store.rehydrate("order", "id-1");

        assertSame(durable.rehydrateResult, resolved);
        assertEquals(1, redis.writeSnapshotCalls);
        assertEquals(1, redis.appendCalls);
    }

    @Test
    void appendWritesDurableStoreFirstThenRedis() {
        StubStore redis = new StubStore();
        StubStore durable = new StubStore();
        durable.appendResult = new AppendResult(2L, 3L, false);

        RedisBackedFlowStateStore store = new RedisBackedFlowStateStore(redis, durable);
        AppendResult result = store.appendEvents("order", "id-1", 2L, List.of(sampleEvent("order", "id-1", 3L)), "k1");

        assertEquals(1, durable.appendCalls);
        assertEquals(1, redis.appendCalls);
        assertEquals(3L, result.nextVersion());
    }

    @Test
    void readEventsFallsBackToDurableStoreWhenRedisIsEmpty() {
        StubStore redis = new StubStore();
        StubStore durable = new StubStore();

        redis.readEventsResult = List.of();
        durable.readEventsResult = List.of(sampleEvent("order", "id-1", 5L));

        RedisBackedFlowStateStore store = new RedisBackedFlowStateStore(redis, durable);
        List<PersistedEvent> events = store.readEvents("order", "id-1", 4L, 100);

        assertEquals(1, redis.readEventsCalls);
        assertEquals(1, durable.readEventsCalls);
        assertEquals(1, events.size());
        assertEquals(5L, events.get(0).sequence());
    }

    private static RehydratedState emptyState(String flowType, String flowId) {
        StateEnvelope envelope = new StateEnvelope(flowType, flowId, 0L, 0L, MAPPER.createObjectNode(), Instant.EPOCH.toString(), Map.of());
        return new RehydratedState(envelope, List.of());
    }

    private static RehydratedState stateWithTail(String flowType, String flowId, long version, long snapshotVersion) {
        StateEnvelope envelope = new StateEnvelope(flowType, flowId, version, snapshotVersion, MAPPER.createObjectNode(), Instant.now().toString(), Map.of());
        List<PersistedEvent> tail = List.of(sampleEvent(flowType, flowId, snapshotVersion + 1));
        return new RehydratedState(envelope, tail);
    }

    private static PersistedEvent sampleEvent(String flowType, String flowId, long sequence) {
        return new PersistedEvent(
            "evt-" + sequence,
            flowType,
            flowId,
            sequence,
            "updated",
            MAPPER.createObjectNode().put("v", sequence),
            Instant.now().toString(),
            "k-" + sequence
        );
    }

    private static final class StubStore implements FlowStateStore {
        RehydratedState rehydrateResult = emptyState("flow", "id");
        AppendResult appendResult = new AppendResult(0L, 0L, false);
        List<PersistedEvent> readEventsResult = new ArrayList<>();

        int writeSnapshotCalls;
        int appendCalls;
        int readEventsCalls;

        @Override
        public RehydratedState rehydrate(String flowType, String flowId) {
            return rehydrateResult;
        }

        @Override
        public AppendResult appendEvents(String flowType, String flowId, long expectedVersion, List<PersistedEvent> events, String idempotencyKey) {
            appendCalls++;
            return appendResult;
        }

        @Override
        public void writeSnapshot(String flowType, String flowId, long version, JsonNode snapshotJson, Map<String, Object> metadata) {
            writeSnapshotCalls++;
        }

        @Override
        public List<PersistedEvent> readEvents(String flowType, String flowId, long afterVersion, int limit) {
            readEventsCalls++;
            return readEventsResult;
        }
    }
}