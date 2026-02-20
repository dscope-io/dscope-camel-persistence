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
package io.dscope.camel.persistence.redis;

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
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.params.ScanParams;
import redis.clients.jedis.resps.ScanResult;

public class RedisFlowStateStore implements FlowStateStore {

    private static final String APPEND_SCRIPT = ""
        + "local ver = tonumber(redis.call('LLEN', KEYS[2]))\n"
        + "if ver ~= tonumber(ARGV[1]) then return {'CONFLICT', tostring(ver)} end\n"
        + "if ARGV[2] ~= '' and redis.call('SISMEMBER', KEYS[3], ARGV[2]) == 1 then return {'DUPLICATE', tostring(ver)} end\n"
        + "local events = cjson.decode(ARGV[3])\n"
        + "local seq = ver\n"
        + "for i, event in ipairs(events) do\n"
        + "  seq = seq + 1\n"
        + "  event['sequence'] = seq\n"
        + "  if event['occurredAt'] == nil then event['occurredAt'] = ARGV[4] end\n"
        + "  redis.call('RPUSH', KEYS[2], cjson.encode(event))\n"
        + "end\n"
        + "if ARGV[2] ~= '' then redis.call('SADD', KEYS[3], ARGV[2]) end\n"
        + "return {'OK', tostring(seq)}";

    private final ObjectMapper mapper = new ObjectMapper();
    private final JedisPool jedisPool;
    private final String keyPrefix;

    public RedisFlowStateStore(String redisUri, String keyPrefix) {
        this.jedisPool = new JedisPool(redisUri);
        this.keyPrefix = keyPrefix == null || keyPrefix.isBlank() ? "camel:state" : keyPrefix;
    }

    @Override
    public RehydratedState rehydrate(String flowType, String flowId) {
        try (Jedis jedis = jedisPool.getResource()) {
            String snapshotRaw = jedis.get(snapshotKey(flowType, flowId));
            StateEnvelope envelope = snapshotRaw == null ? emptyEnvelope(flowType, flowId) : parseEnvelope(snapshotRaw);
            List<PersistedEvent> tail = readEvents(flowType, flowId, envelope.snapshotVersion(), 10_000);
            return new RehydratedState(envelope, tail);
        } catch (Exception e) {
            throw new BackendUnavailableException("Redis rehydrate failed for " + flowType + "/" + flowId, e);
        }
    }

    @Override
    public AppendResult appendEvents(String flowType, String flowId, long expectedVersion, List<PersistedEvent> events, String idempotencyKey) {
        List<PersistedEvent> safeEvents = events == null ? List.of() : events;
        String now = Instant.now().toString();
        try (Jedis jedis = jedisPool.getResource()) {
            List<String> keys = List.of(snapshotKey(flowType, flowId), eventsKey(flowType, flowId), idemKey(flowType, flowId));
            List<String> args = List.of(
                Long.toString(expectedVersion),
                idempotencyKey == null ? "" : idempotencyKey,
                mapper.writeValueAsString(stripSequences(safeEvents)),
                now
            );
            @SuppressWarnings("unchecked")
            List<String> result = (List<String>) jedis.eval(APPEND_SCRIPT, keys, args);
            String status = result.get(0);
            long version = Long.parseLong(result.get(1));
            if ("CONFLICT".equals(status)) {
                throw new OptimisticConflictException("Version conflict for " + flowType + "/" + flowId + ": expected " + expectedVersion + " actual " + version);
            }
            if ("DUPLICATE".equals(status)) {
                return new AppendResult(version, version, true);
            }
            return new AppendResult(expectedVersion, version, false);
        } catch (OptimisticConflictException e) {
            throw e;
        } catch (Exception e) {
            throw new BackendUnavailableException("Redis append failed for " + flowType + "/" + flowId, e);
        }
    }

    @Override
    public void writeSnapshot(String flowType, String flowId, long version, JsonNode snapshotJson, Map<String, Object> metadata) {
        StateEnvelope envelope = new StateEnvelope(flowType, flowId, version, version, snapshotJson, Instant.now().toString(), metadata == null ? Map.of() : metadata);
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.set(snapshotKey(flowType, flowId), mapper.writeValueAsString(envelope));
        } catch (Exception e) {
            throw new BackendUnavailableException("Redis snapshot write failed for " + flowType + "/" + flowId, e);
        }
    }

    @Override
    public List<PersistedEvent> readEvents(String flowType, String flowId, long afterVersion, int limit) {
        int resolvedLimit = limit <= 0 ? 500 : Math.min(limit, 10_000);
        long start = Math.max(0, afterVersion);
        long end = start + resolvedLimit - 1;
        try (Jedis jedis = jedisPool.getResource()) {
            List<String> rows = jedis.lrange(eventsKey(flowType, flowId), start, end);
            List<PersistedEvent> events = new ArrayList<>(rows.size());
            for (String row : rows) {
                events.add(mapper.readValue(row, PersistedEvent.class));
            }
            return events;
        } catch (IOException e) {
            throw new BackendUnavailableException("Redis read events failed for " + flowType + "/" + flowId, e);
        }
    }

    public List<String> listFlowIds(String flowType) {
        String pattern = keyPrefix + ":" + flowType + ":*:snapshot";
        try (Jedis jedis = jedisPool.getResource()) {
            List<String> ids = new ArrayList<>();
            String cursor = ScanParams.SCAN_POINTER_START;
            ScanParams params = new ScanParams().match(pattern).count(200);
            do {
                ScanResult<String> result = jedis.scan(cursor, params);
                cursor = result.getCursor();
                for (String key : result.getResult()) {
                    String[] parts = key.split(":");
                    if (parts.length >= 4) {
                        ids.add(parts[parts.length - 2]);
                    }
                }
            } while (!ScanParams.SCAN_POINTER_START.equals(cursor));
            return ids;
        } catch (Exception e) {
            throw new BackendUnavailableException("Redis scan failed for flowType " + flowType, e);
        }
    }

    private List<Map<String, Object>> stripSequences(List<PersistedEvent> events) {
        List<Map<String, Object>> mapped = new ArrayList<>(events.size());
        for (PersistedEvent event : events) {
            Map<String, Object> item = mapper.convertValue(event, new TypeReference<Map<String, Object>>() {});
            item.remove("sequence");
            mapped.add(item);
        }
        return mapped;
    }

    private StateEnvelope parseEnvelope(String raw) throws IOException {
        return mapper.readValue(raw, StateEnvelope.class);
    }

    private StateEnvelope emptyEnvelope(String flowType, String flowId) {
        return new StateEnvelope(flowType, flowId, 0L, 0L, mapper.createObjectNode(), Instant.EPOCH.toString(), Map.of());
    }

    private String snapshotKey(String flowType, String flowId) {
        return keyPrefix + ":" + flowType + ":" + flowId + ":snapshot";
    }

    private String eventsKey(String flowType, String flowId) {
        return keyPrefix + ":" + flowType + ":" + flowId + ":events";
    }

    private String idemKey(String flowType, String flowId) {
        return keyPrefix + ":" + flowType + ":" + flowId + ":idem";
    }
}
