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

import io.dscope.camel.persistence.core.FlowStateStore;
import io.dscope.camel.persistence.testkit.FlowStateStoreContractSuite;
import java.util.UUID;
import org.junit.jupiter.api.Assumptions;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

class RedisFlowStateStoreContractTest extends FlowStateStoreContractSuite {

    @Override
    protected FlowStateStore createStore() {
        String uri = System.getProperty("camel.persistence.test.redis.uri", "redis://localhost:6379");
        Assumptions.assumeTrue(isRedisReachable(uri),
            "Redis is not reachable at " + uri + ". Set -Dcamel.persistence.test.redis.uri to run Redis contract tests.");
        String keyPrefix = "camel:state:contract:" + UUID.randomUUID().toString().replace("-", "");
        return new RedisFlowStateStore(uri, keyPrefix);
    }

    @Override
    protected String flowType() {
        return "redis.contract.flow";
    }

    private boolean isRedisReachable(String uri) {
        try (JedisPool pool = new JedisPool(uri); Jedis jedis = pool.getResource()) {
            return "PONG".equalsIgnoreCase(jedis.ping());
        } catch (Exception ignored) {
            return false;
        }
    }
}
