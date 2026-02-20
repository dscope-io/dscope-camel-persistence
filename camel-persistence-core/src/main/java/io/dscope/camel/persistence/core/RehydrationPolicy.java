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
