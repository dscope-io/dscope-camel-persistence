package io.dscope.camel.persistence.core;

import com.fasterxml.jackson.databind.JsonNode;

public record PersistedEvent(
    String eventId,
    String flowType,
    String flowId,
    long sequence,
    String eventType,
    JsonNode payload,
    String occurredAt,
    String idempotencyKey
) {
}
