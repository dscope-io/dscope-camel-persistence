package io.dscope.camel.persistence.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Properties;
import org.junit.jupiter.api.Test;

class PersistenceConfigurationTest {

    @Test
    void readsDefaults() {
        PersistenceConfiguration config = PersistenceConfiguration.fromProperties(new Properties());
        assertEquals(false, config.enabled());
        assertEquals(PersistenceBackend.REDIS, config.backend());
        assertEquals(25, config.rehydrationPolicy().snapshotEveryEvents());
    }
}
