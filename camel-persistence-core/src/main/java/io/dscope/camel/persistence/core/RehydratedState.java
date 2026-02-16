package io.dscope.camel.persistence.core;

import java.util.List;

public record RehydratedState(
    StateEnvelope envelope,
    List<PersistedEvent> tailEvents
) {
}
