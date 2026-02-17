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
