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
package io.dscope.camel.persistence.testkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.dscope.camel.persistence.core.AppendResult;
import io.dscope.camel.persistence.core.FlowStateStore;
import io.dscope.camel.persistence.core.PersistedEvent;
import io.dscope.camel.persistence.core.RehydratedState;
import io.dscope.camel.persistence.core.exception.OptimisticConflictException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Backend-neutral contract tests for FlowStateStore implementations.
 */
public abstract class FlowStateStoreContractSuite {

    private final ObjectMapper mapper = new ObjectMapper();

    protected abstract FlowStateStore createStore();

    protected String flowType() {
        return "contract.flow";
    }

    @Test
    void appendReadAndSequenceAreMonotonic() {
        FlowStateStore store = createStore();
        String flowId = flowId();

        PersistedEvent created = event(flowId, "state.created", Map.of("state", "CREATED"));
        PersistedEvent running = event(flowId, "state.running", Map.of("state", "RUNNING"));

        AppendResult append = store.appendEvents(flowType(), flowId, 0L, List.of(created, running), "cmd-1");
        assertFalse(append.duplicate());
        assertEquals(2L, append.nextVersion());

        List<PersistedEvent> events = store.readEvents(flowType(), flowId, 0L, 10);
        assertEquals(2, events.size());
        assertEquals(1L, events.get(0).sequence());
        assertEquals(2L, events.get(1).sequence());
    }

    @Test
    void idempotencyKeyPreventsDuplicateAppend() {
        FlowStateStore store = createStore();
        String flowId = flowId();

        PersistedEvent created = event(flowId, "state.created", Map.of("state", "CREATED"));
        AppendResult first = store.appendEvents(flowType(), flowId, 0L, List.of(created), "same-cmd");
        AppendResult duplicate = store.appendEvents(flowType(), flowId, first.nextVersion(), List.of(created), "same-cmd");

        assertFalse(first.duplicate());
        assertTrue(duplicate.duplicate());
        assertEquals(first.nextVersion(), duplicate.nextVersion());

        List<PersistedEvent> events = store.readEvents(flowType(), flowId, 0L, 10);
        assertEquals(1, events.size());
    }

    @Test
    void optimisticConflictIsDetected() {
        FlowStateStore store = createStore();
        String flowId = flowId();

        PersistedEvent created = event(flowId, "state.created", Map.of("state", "CREATED"));
        store.appendEvents(flowType(), flowId, 0L, List.of(created), "cmd-a");

        PersistedEvent running = event(flowId, "state.running", Map.of("state", "RUNNING"));
        assertThrows(
            OptimisticConflictException.class,
            () -> store.appendEvents(flowType(), flowId, 0L, List.of(running), "cmd-b")
        );
    }

    @Test
    void rehydrateUsesSnapshotAndTailEvents() {
        FlowStateStore store = createStore();
        String flowId = flowId();

        PersistedEvent created = event(flowId, "state.created", Map.of("state", "CREATED"));
        PersistedEvent running = event(flowId, "state.running", Map.of("state", "RUNNING"));
        store.appendEvents(flowType(), flowId, 0L, List.of(created, running), "cmd-1");

        store.writeSnapshot(
            flowType(),
            flowId,
            1L,
            mapper.valueToTree(Map.of("state", "CREATED")),
            Map.of("snapshot", true)
        );

        RehydratedState rehydrated = store.rehydrate(flowType(), flowId);
        assertEquals(1L, rehydrated.envelope().snapshotVersion());
        assertEquals("CREATED", rehydrated.envelope().snapshot().path("state").asText());
        assertEquals(1, rehydrated.tailEvents().size());
        assertEquals(2L, rehydrated.tailEvents().get(0).sequence());
    }

    protected PersistedEvent event(String flowId, String eventType, Map<String, Object> payload) {
        return new PersistedEvent(
            UUID.randomUUID().toString(),
            flowType(),
            flowId,
            0L,
            eventType,
            mapper.valueToTree(payload),
            Instant.now().toString(),
            null
        );
    }

    protected String flowId() {
        return "flow-" + UUID.randomUUID().toString().replace("-", "");
    }
}
