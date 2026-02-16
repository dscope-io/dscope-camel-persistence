package io.dscope.camel.persistence.core;

public record RehydrationPolicy(
    int snapshotEveryEvents,
    int maxReplayEvents,
    int readBatchSize
) {
    public static final RehydrationPolicy DEFAULT = new RehydrationPolicy(25, 500, 200);

    public RehydrationPolicy {
        snapshotEveryEvents = Math.max(1, snapshotEveryEvents);
        maxReplayEvents = Math.max(10, maxReplayEvents);
        readBatchSize = Math.max(10, readBatchSize);
    }
}
