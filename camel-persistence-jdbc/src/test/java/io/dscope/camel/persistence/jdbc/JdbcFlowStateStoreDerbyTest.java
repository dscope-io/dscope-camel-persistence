package io.dscope.camel.persistence.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.dscope.camel.persistence.core.AppendResult;
import io.dscope.camel.persistence.core.PersistedEvent;
import io.dscope.camel.persistence.core.RehydratedState;
import io.dscope.camel.persistence.core.exception.OptimisticConflictException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JdbcFlowStateStoreDerbyTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void persistsAndRehydratesFromDerby() {
        JdbcFlowStateStore store = newStore();

        PersistedEvent created = event("task.created", Map.of("state", "CREATED"));
        PersistedEvent running = event("task.running", Map.of("state", "RUNNING"));

        AppendResult append = store.appendEvents("a2a.task", "t1", 0L, List.of(created, running), "cmd-1");
        assertFalse(append.duplicate());
        assertEquals(2L, append.nextVersion());

        store.writeSnapshot("a2a.task", "t1", 2L, mapper.valueToTree(Map.of("status", "RUNNING")), Map.of("kind", "task"));

        RehydratedState rehydrated = store.rehydrate("a2a.task", "t1");
        assertEquals(2L, rehydrated.envelope().snapshotVersion());
        assertEquals("RUNNING", rehydrated.envelope().snapshot().path("status").asText());
        assertTrue(rehydrated.tailEvents().isEmpty());

        List<PersistedEvent> allEvents = store.readEvents("a2a.task", "t1", 0L, 10);
        assertEquals(2, allEvents.size());
        assertEquals(1L, allEvents.get(0).sequence());
        assertEquals(2L, allEvents.get(1).sequence());
    }

    @Test
    void enforcesIdempotency() {
        JdbcFlowStateStore store = newStore();

        PersistedEvent created = event("task.created", Map.of("state", "CREATED"));
        AppendResult first = store.appendEvents("a2a.task", "t2", 0L, List.of(created), "same-command");
        AppendResult duplicate = store.appendEvents("a2a.task", "t2", first.nextVersion(), List.of(created), "same-command");

        assertFalse(first.duplicate());
        assertTrue(duplicate.duplicate());
        assertEquals(first.nextVersion(), duplicate.nextVersion());

        List<PersistedEvent> events = store.readEvents("a2a.task", "t2", 0L, 10);
        assertEquals(1, events.size());
    }

    @Test
    void detectsOptimisticConflict() {
        JdbcFlowStateStore store = newStore();

        PersistedEvent created = event("task.created", Map.of("state", "CREATED"));
        store.appendEvents("a2a.task", "t3", 0L, List.of(created), "cmd-a");

        PersistedEvent running = event("task.running", Map.of("state", "RUNNING"));
        assertThrows(
            OptimisticConflictException.class,
            () -> store.appendEvents("a2a.task", "t3", 0L, List.of(running), "cmd-b")
        );
    }

    private JdbcFlowStateStore newStore() {
        String dbName = "camelPersistenceTest" + UUID.randomUUID().toString().replace("-", "");
        return new JdbcFlowStateStore("jdbc:derby:memory:" + dbName + ";create=true", "", "");
    }

    private PersistedEvent event(String type, Map<String, Object> payload) {
        return new PersistedEvent(
            UUID.randomUUID().toString(),
            "a2a.task",
            "x",
            0,
            type,
            mapper.valueToTree(payload),
            Instant.now().toString(),
            null
        );
    }
}
