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
package io.dscope.camel.persistence.jdbc;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.dscope.camel.persistence.core.AppendResult;
import io.dscope.camel.persistence.core.FlowStateStore;
import io.dscope.camel.persistence.core.PersistedEvent;
import io.dscope.camel.persistence.core.RehydratedState;
import io.dscope.camel.persistence.core.StateEnvelope;
import io.dscope.camel.persistence.core.exception.BackendUnavailableException;
import io.dscope.camel.persistence.core.exception.OptimisticConflictException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class JdbcFlowStateStore implements FlowStateStore {

    private static final String TABLE_SNAPSHOT = "camel_flow_snapshot";
    private static final String TABLE_EVENT = "camel_flow_event";
    private static final String TABLE_IDEMPOTENCY = "camel_flow_idempotency";
    private static final String TABLE_CONTEXT = "camel_flow_conversation_context";
    private static final String CONTEXT_EVENT_MARKER = "conversation.context";
    private static final String BUILTIN_SCHEMA_RESOURCE = "builtin";
    private static final Pattern STATEMENT_SEPARATOR = Pattern.compile(";\\s*(?:\\R|$)");

    private final ObjectMapper mapper = new ObjectMapper();
    private final String jdbcUrl;
    private final String jdbcUser;
    private final String jdbcPassword;
    private final String schemaDdlResource;

    public JdbcFlowStateStore(String jdbcUrl, String jdbcUser, String jdbcPassword) {
        this(jdbcUrl, jdbcUser, jdbcPassword, BUILTIN_SCHEMA_RESOURCE);
    }

    public JdbcFlowStateStore(String jdbcUrl, String jdbcUser, String jdbcPassword, String schemaDdlResource) {
        this.jdbcUrl = jdbcUrl;
        this.jdbcUser = jdbcUser == null ? "" : jdbcUser;
        this.jdbcPassword = jdbcPassword == null ? "" : jdbcPassword;
        this.schemaDdlResource = schemaDdlResource == null ? BUILTIN_SCHEMA_RESOURCE : schemaDdlResource.trim();
        initializeSchema();
    }

    @Override
    public RehydratedState rehydrate(String flowType, String flowId) {
        try (Connection connection = newConnection()) {
            StateEnvelope envelope = readSnapshot(connection, flowType, flowId);
            List<PersistedEvent> tail = readEvents(connection, flowType, flowId, envelope.snapshotVersion(), 100_000);
            return new RehydratedState(envelope, tail);
        } catch (Exception e) {
            throw new BackendUnavailableException("JDBC rehydrate failed for " + flowType + "/" + flowId, e);
        }
    }

    @Override
    public AppendResult appendEvents(String flowType, String flowId, long expectedVersion, List<PersistedEvent> events, String idempotencyKey) {
        List<PersistedEvent> safeEvents = events == null ? List.of() : events;
        try (Connection connection = newConnection()) {
            connection.setAutoCommit(false);
            try {
                long currentVersion = currentVersion(connection, flowType, flowId);
                if (currentVersion != expectedVersion) {
                    throw new OptimisticConflictException(
                        "Version conflict for " + flowType + "/" + flowId + ": expected " + expectedVersion + " actual " + currentVersion
                    );
                }

                if (idempotencyKey != null && !idempotencyKey.isBlank() && isDuplicate(connection, flowType, flowId, idempotencyKey)) {
                    connection.rollback();
                    return new AppendResult(currentVersion, currentVersion, true);
                }

                long next = currentVersion;
                for (PersistedEvent event : safeEvents) {
                    next++;
                    insertEvent(connection, flowType, flowId, next, event);
                }

                if (idempotencyKey != null && !idempotencyKey.isBlank()) {
                    insertIdempotency(connection, flowType, flowId, idempotencyKey, next);
                }

                connection.commit();
                return new AppendResult(currentVersion, next, false);
            } catch (RuntimeException e) {
                connection.rollback();
                throw e;
            } catch (Exception e) {
                connection.rollback();
                throw new BackendUnavailableException("JDBC append failed for " + flowType + "/" + flowId, e);
            }
        } catch (OptimisticConflictException e) {
            throw e;
        } catch (Exception e) {
            throw new BackendUnavailableException("JDBC append failed for " + flowType + "/" + flowId, e);
        }
    }

    @Override
    public void writeSnapshot(String flowType, String flowId, long version, JsonNode snapshotJson, Map<String, Object> metadata) {
        String metadataJson;
        try {
            metadataJson = mapper.writeValueAsString(metadata == null ? Map.of() : metadata);
        } catch (Exception e) {
            throw new BackendUnavailableException("Unable to serialize snapshot metadata", e);
        }

        try (Connection connection = newConnection()) {
            int updated;
            try (PreparedStatement update = connection.prepareStatement(
                "UPDATE " + TABLE_SNAPSHOT + " SET version = ?, snapshot_json = ?, metadata_json = ?, last_updated_at = ?"
                    + " WHERE flow_type = ? AND flow_id = ?"
            )) {
                update.setLong(1, version);
                update.setString(2, snapshotJson == null ? "{}" : snapshotJson.toString());
                update.setString(3, metadataJson);
                update.setString(4, Instant.now().toString());
                update.setString(5, flowType);
                update.setString(6, flowId);
                updated = update.executeUpdate();
            }

            if (updated == 0) {
                try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO " + TABLE_SNAPSHOT
                        + " (flow_type, flow_id, version, snapshot_json, metadata_json, last_updated_at)"
                        + " VALUES (?, ?, ?, ?, ?, ?)"
                )) {
                    insert.setString(1, flowType);
                    insert.setString(2, flowId);
                    insert.setLong(3, version);
                    insert.setString(4, snapshotJson == null ? "{}" : snapshotJson.toString());
                    insert.setString(5, metadataJson);
                    insert.setString(6, Instant.now().toString());
                    insert.executeUpdate();
                }
            }
        } catch (Exception e) {
            throw new BackendUnavailableException("JDBC snapshot write failed for " + flowType + "/" + flowId, e);
        }
    }

    @Override
    public List<PersistedEvent> readEvents(String flowType, String flowId, long afterVersion, int limit) {
        try (Connection connection = newConnection()) {
            return readEvents(connection, flowType, flowId, afterVersion, limit);
        } catch (Exception e) {
            throw new BackendUnavailableException("JDBC read events failed for " + flowType + "/" + flowId, e);
        }
    }

    private List<PersistedEvent> readEvents(Connection connection, String flowType, String flowId, long afterVersion, int limit) throws SQLException {
        int resolvedLimit = limit <= 0 ? 500 : limit;
        List<PersistedEvent> events = new ArrayList<>();

        try (PreparedStatement query = connection.prepareStatement(
            "SELECT sequence, event_id, event_type, payload_json, occurred_at, idempotency_key"
                + " FROM " + TABLE_EVENT
                + " WHERE flow_type = ? AND flow_id = ? AND sequence > ?"
                + " ORDER BY sequence ASC"
        )) {
            query.setString(1, flowType);
            query.setString(2, flowId);
            query.setLong(3, afterVersion);
            query.setMaxRows(resolvedLimit);
            try (ResultSet rs = query.executeQuery()) {
                while (rs.next()) {
                    long sequence = rs.getLong(1);
                    String eventId = rs.getString(2);
                    String eventType = rs.getString(3);
                    String payloadRaw = rs.getString(4);
                    String occurredAt = rs.getString(5);
                    String idempotencyKey = rs.getString(6);

                    JsonNode payload = mapper.readTree(payloadRaw == null ? "{}" : payloadRaw);
                    events.add(new PersistedEvent(eventId, flowType, flowId, sequence, eventType, payload, occurredAt, idempotencyKey));
                }
            } catch (Exception e) {
                throw new BackendUnavailableException("Failed to decode JDBC events", e);
            }
        }
        return events;
    }

    private StateEnvelope readSnapshot(Connection connection, String flowType, String flowId) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement(
            "SELECT version, snapshot_json, metadata_json, last_updated_at"
                + " FROM " + TABLE_SNAPSHOT
                + " WHERE flow_type = ? AND flow_id = ?"
        )) {
            query.setString(1, flowType);
            query.setString(2, flowId);
            try (ResultSet rs = query.executeQuery()) {
                if (rs.next()) {
                    long version = rs.getLong(1);
                    JsonNode snapshot;
                    Map<String, Object> metadata;
                    try {
                        snapshot = mapper.readTree(rs.getString(2));
                        metadata = mapper.readValue(rs.getString(3), new TypeReference<Map<String, Object>>() {});
                    } catch (Exception e) {
                        throw new BackendUnavailableException("Failed to decode snapshot", e);
                    }
                    return new StateEnvelope(
                        flowType,
                        flowId,
                        version,
                        version,
                        snapshot,
                        rs.getString(4),
                        metadata
                    );
                }
            }
        }
        return new StateEnvelope(flowType, flowId, 0L, 0L, mapper.createObjectNode(), Instant.EPOCH.toString(), Map.of());
    }

    private long currentVersion(Connection connection, String flowType, String flowId) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement(
            "SELECT COALESCE(MAX(sequence), 0) FROM " + TABLE_EVENT + " WHERE flow_type = ? AND flow_id = ?"
        )) {
            query.setString(1, flowType);
            query.setString(2, flowId);
            try (ResultSet rs = query.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    private boolean isDuplicate(Connection connection, String flowType, String flowId, String idempotencyKey) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement(
            "SELECT 1 FROM " + TABLE_IDEMPOTENCY + " WHERE flow_type = ? AND flow_id = ? AND idempotency_key = ?"
        )) {
            query.setString(1, flowType);
            query.setString(2, flowId);
            query.setString(3, idempotencyKey);
            try (ResultSet rs = query.executeQuery()) {
                return rs.next();
            }
        }
    }

    private void insertEvent(Connection connection, String flowType, String flowId, long sequence, PersistedEvent event) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement(
            "INSERT INTO " + TABLE_EVENT
                + " (flow_type, flow_id, sequence, event_id, event_type, payload_json, occurred_at, idempotency_key)"
                + " VALUES (?, ?, ?, ?, ?, ?, ?, ?)"
        )) {
            insert.setString(1, flowType);
            insert.setString(2, flowId);
            insert.setLong(3, sequence);
            insert.setString(4, event.eventId());
            insert.setString(5, event.eventType());
            insert.setString(6, event.payload() == null ? "{}" : event.payload().toString());
            insert.setString(7, event.occurredAt() == null ? Instant.now().toString() : event.occurredAt());
            insert.setString(8, event.idempotencyKey());
            insert.executeUpdate();
        }

        if (isConversationContextEvent(event.eventType())) {
            upsertConversationContext(connection, flowType, flowId, sequence, event);
        }
    }

    private void upsertConversationContext(Connection connection, String flowType, String flowId, long sequence, PersistedEvent event)
        throws SQLException {
        String now = Instant.now().toString();
        int updated;
        try (PreparedStatement update = connection.prepareStatement(
            "UPDATE " + TABLE_CONTEXT
                + " SET sequence = ?, event_id = ?, context_json = ?, occurred_at = ?, session_id = ?, last_updated_at = ?"
                + " WHERE flow_type = ? AND flow_id = ? AND context_type = ?"
        )) {
            update.setLong(1, sequence);
            update.setString(2, event.eventId());
            update.setString(3, event.payload() == null ? "{}" : event.payload().toString());
            update.setString(4, event.occurredAt() == null ? now : event.occurredAt());
            update.setString(5, event.idempotencyKey());
            update.setString(6, now);
            update.setString(7, flowType);
            update.setString(8, flowId);
            update.setString(9, event.eventType());
            updated = update.executeUpdate();
        }

        if (updated == 0) {
            try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO " + TABLE_CONTEXT
                    + " (flow_type, flow_id, context_type, sequence, event_id, context_json, occurred_at, session_id, last_updated_at)"
                    + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)"
            )) {
                insert.setString(1, flowType);
                insert.setString(2, flowId);
                insert.setString(3, event.eventType());
                insert.setLong(4, sequence);
                insert.setString(5, event.eventId());
                insert.setString(6, event.payload() == null ? "{}" : event.payload().toString());
                insert.setString(7, event.occurredAt() == null ? now : event.occurredAt());
                insert.setString(8, event.idempotencyKey());
                insert.setString(9, now);
                insert.executeUpdate();
            }
        }
    }

    private boolean isConversationContextEvent(String eventType) {
        return eventType != null && eventType.contains(CONTEXT_EVENT_MARKER);
    }

    private void insertIdempotency(Connection connection, String flowType, String flowId, String idempotencyKey, long appliedVersion)
        throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement(
            "INSERT INTO " + TABLE_IDEMPOTENCY + " (flow_type, flow_id, idempotency_key, applied_version) VALUES (?, ?, ?, ?)"
        )) {
            insert.setString(1, flowType);
            insert.setString(2, flowId);
            insert.setString(3, idempotencyKey);
            insert.setLong(4, appliedVersion);
            insert.executeUpdate();
        }
    }

    private void initializeSchema() {
        if (schemaDdlResource.isBlank()) {
            return;
        }
        if (!BUILTIN_SCHEMA_RESOURCE.equalsIgnoreCase(schemaDdlResource)) {
            initializeSchemaFromResource(schemaDdlResource);
            return;
        }
        try (Connection connection = newConnection(); Statement statement = connection.createStatement()) {
            createTableIfMissing(statement,
                "CREATE TABLE " + TABLE_SNAPSHOT + " ("
                    + "flow_type VARCHAR(128) NOT NULL, "
                    + "flow_id VARCHAR(256) NOT NULL, "
                    + "version BIGINT NOT NULL, "
                    + "snapshot_json CLOB NOT NULL, "
                    + "metadata_json CLOB NOT NULL, "
                    + "last_updated_at VARCHAR(64) NOT NULL, "
                    + "PRIMARY KEY (flow_type, flow_id))"
            );

            createTableIfMissing(statement,
                "CREATE TABLE " + TABLE_EVENT + " ("
                    + "flow_type VARCHAR(128) NOT NULL, "
                    + "flow_id VARCHAR(256) NOT NULL, "
                    + "sequence BIGINT NOT NULL, "
                    + "event_id VARCHAR(128) NOT NULL, "
                    + "event_type VARCHAR(128) NOT NULL, "
                    + "payload_json CLOB NOT NULL, "
                    + "occurred_at VARCHAR(64) NOT NULL, "
                    + "idempotency_key VARCHAR(256), "
                    + "PRIMARY KEY (flow_type, flow_id, sequence))"
            );

            createTableIfMissing(statement,
                "CREATE TABLE " + TABLE_IDEMPOTENCY + " ("
                    + "flow_type VARCHAR(128) NOT NULL, "
                    + "flow_id VARCHAR(256) NOT NULL, "
                    + "idempotency_key VARCHAR(256) NOT NULL, "
                    + "applied_version BIGINT NOT NULL, "
                    + "PRIMARY KEY (flow_type, flow_id, idempotency_key))"
            );

            createTableIfMissing(statement,
                "CREATE TABLE " + TABLE_CONTEXT + " ("
                    + "flow_type VARCHAR(128) NOT NULL, "
                    + "flow_id VARCHAR(256) NOT NULL, "
                    + "context_type VARCHAR(128) NOT NULL, "
                    + "sequence BIGINT NOT NULL, "
                    + "event_id VARCHAR(128) NOT NULL, "
                    + "context_json CLOB NOT NULL, "
                    + "occurred_at VARCHAR(64) NOT NULL, "
                    + "session_id VARCHAR(256), "
                    + "last_updated_at VARCHAR(64) NOT NULL, "
                    + "PRIMARY KEY (flow_type, flow_id, context_type))"
            );
        } catch (Exception e) {
            throw new BackendUnavailableException("Failed to initialize JDBC persistence schema", e);
        }
    }

    private void initializeSchemaFromResource(String resource) {
        String normalized = resource.startsWith("classpath:") ? resource.substring("classpath:".length()) : resource;
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        try (var stream = Thread.currentThread().getContextClassLoader().getResourceAsStream(normalized)) {
            if (stream == null) {
                throw new BackendUnavailableException("JDBC persistence schema resource not found: " + resource);
            }
            String ddl = new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            try (Connection connection = newConnection(); Statement statement = connection.createStatement()) {
                for (String sql : STATEMENT_SEPARATOR.split(ddl)) {
                    String trimmed = withoutLineComments(sql).trim();
                    if (!trimmed.isBlank()) {
                        statement.executeUpdate(trimmed);
                    }
                }
            }
        } catch (BackendUnavailableException e) {
            throw e;
        } catch (Exception e) {
            throw new BackendUnavailableException("Failed to initialize JDBC persistence schema from " + resource, e);
        }
    }

    private String withoutLineComments(String sql) {
        StringBuilder builder = new StringBuilder();
        for (String line : sql.split("\\R")) {
            String trimmed = line.trim();
            if (!trimmed.startsWith("--")) {
                builder.append(line).append(System.lineSeparator());
            }
        }
        return builder.toString();
    }

    private Connection newConnection() throws SQLException {
        if (jdbcUser.isBlank()) {
            return DriverManager.getConnection(jdbcUrl);
        }
        return DriverManager.getConnection(jdbcUrl, jdbcUser, jdbcPassword);
    }

    private void createTableIfMissing(Statement statement, String ddl) throws SQLException {
        try {
            statement.executeUpdate(ddl);
        } catch (SQLException e) {
            if (e.getSQLState() == null || !"X0Y32".equals(e.getSQLState())) {
                throw e;
            }
        }
    }
}
