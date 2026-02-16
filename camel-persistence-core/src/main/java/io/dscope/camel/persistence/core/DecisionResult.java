package io.dscope.camel.persistence.core;

import java.util.List;

public record DecisionResult(
    List<PersistedEvent> events,
    Object response
) {
}
