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

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import java.util.Objects;

final class RedisJdbcFlowStateStore implements FlowStateStore {

    private final FlowStateStore redisStore;
    private final FlowStateStore jdbcStore;

    RedisJdbcFlowStateStore(FlowStateStore redisStore, FlowStateStore jdbcStore) {
        this.redisStore = Objects.requireNonNull(redisStore, "redisStore");
        this.jdbcStore = Objects.requireNonNull(jdbcStore, "jdbcStore");
    }

    @Override
    public RehydratedState rehydrate(String flowType, String flowId) {
        try {
            RehydratedState cached = redisStore.rehydrate(flowType, flowId);
            if (!isLikelyCacheMiss(cached)) {
                return cached;
            }
        } catch (RuntimeException ignored) {
            // ignore and fallback to JDBC
        }

        RehydratedState fallback = jdbcStore.rehydrate(flowType, flowId);
        syncRedisFromJdbc(fallback);
        return fallback;
    }

    @Override
    public AppendResult appendEvents(String flowType, String flowId, long expectedVersion, List<PersistedEvent> events, String idempotencyKey) {
        AppendResult result = jdbcStore.appendEvents(flowType, flowId, expectedVersion, events, idempotencyKey);
        try {
            redisStore.appendEvents(flowType, flowId, expectedVersion, events, idempotencyKey);
        } catch (RuntimeException ignored) {
            try {
                syncRedisFromJdbc(jdbcStore.rehydrate(flowType, flowId));
            } catch (RuntimeException ignoredAgain) {
                // ignore cache refresh failures, JDBC already succeeded
            }
        }
        return result;
    }

    @Override
    public void writeSnapshot(String flowType, String flowId, long version, JsonNode snapshotJson, Map<String, Object> metadata) {
        jdbcStore.writeSnapshot(flowType, flowId, version, snapshotJson, metadata);
        try {
            redisStore.writeSnapshot(flowType, flowId, version, snapshotJson, metadata);
        } catch (RuntimeException ignored) {
            // ignore cache write failures, JDBC already succeeded
        }
    }

    @Override
    public List<PersistedEvent> readEvents(String flowType, String flowId, long afterVersion, int limit) {
        try {
            List<PersistedEvent> cached = redisStore.readEvents(flowType, flowId, afterVersion, limit);
            if (!cached.isEmpty()) {
                return cached;
            }
        } catch (RuntimeException ignored) {
            // ignore and fallback to JDBC
        }
        return jdbcStore.readEvents(flowType, flowId, afterVersion, limit);
    }

    private static boolean isLikelyCacheMiss(RehydratedState state) {
        return state.envelope().version() == 0 && state.tailEvents().isEmpty();
    }

    private void syncRedisFromJdbc(RehydratedState jdbcState) {
        StateEnvelope envelope = jdbcState.envelope();
        if (envelope.version() == 0 && jdbcState.tailEvents().isEmpty()) {
            return;
        }

        redisStore.writeSnapshot(
            envelope.flowType(),
            envelope.flowId(),
            envelope.snapshotVersion(),
            envelope.snapshot(),
            envelope.metadata()
        );

        if (!jdbcState.tailEvents().isEmpty()) {
            redisStore.appendEvents(
                envelope.flowType(),
                envelope.flowId(),
                envelope.snapshotVersion(),
                jdbcState.tailEvents(),
                null
            );
        }
    }
}
