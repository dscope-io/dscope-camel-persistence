package io.dscope.camel.persistence.core;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;

public record StateEnvelope(
    String flowType,
    String flowId,
    long version,
    long snapshotVersion,
    JsonNode snapshot,
    String lastUpdatedAt,
    Map<String, Object> metadata
) {
}
