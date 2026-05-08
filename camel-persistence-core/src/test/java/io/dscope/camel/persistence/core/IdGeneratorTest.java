package io.dscope.camel.persistence.core;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class IdGeneratorTest {

    @Test
    void shouldGenerateUuidFormattedIdentifiers() {
        String id = IdGenerator.newUuid();

        Assertions.assertTrue(id.matches("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"));
    }

    @Test
    void shouldGenerateUlidFormattedIdentifiers() {
        String first = IdGenerator.newUlid();
        String second = IdGenerator.newUlid();

        Assertions.assertEquals(26, first.length());
        Assertions.assertEquals(26, second.length());
        Assertions.assertTrue(first.matches("^[0-7][0-9A-HJKMNP-TV-Z]{25}$"));
        Assertions.assertTrue(second.matches("^[0-7][0-9A-HJKMNP-TV-Z]{25}$"));
        Assertions.assertNotEquals(first, second);
    }
}