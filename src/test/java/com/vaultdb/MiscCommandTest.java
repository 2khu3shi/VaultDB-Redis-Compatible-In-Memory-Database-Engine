package com.vaultdb;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MiscCommandTest {
    private VaultDBEngine engine;

    @BeforeEach
    void setUp() {
        engine = new VaultDBEngine();
    }

    @Test
    void keysPatternMatching() {
        engine.set("user:1", "a");
        engine.set("user:2", "b");
        engine.set("post:1", "c");
        List<String> keys = engine.keys("user:*");
        assertEquals(2, keys.size());
        assertTrue(keys.contains("user:1"));
        assertTrue(keys.contains("user:2"));
    }

    @Test
    void typeReportsDataStructure() {
        engine.set("s", "x");
        engine.lpush("l", "x");
        engine.hset("h", "f", "v");
        engine.sadd("set", "m");
        assertEquals("string", engine.type("s"));
        assertEquals("list", engine.type("l"));
        assertEquals("hash", engine.type("h"));
        assertEquals("set", engine.type("set"));
        assertEquals("none", engine.type("missing"));
    }

    @Test
    void flushallClearsDatabase() {
        engine.set("a", "1");
        engine.hset("b", "f", "v");
        assertEquals("OK", engine.flushall());
        assertNull(engine.get("a"));
        assertTrue(engine.hgetall("b").isEmpty());
    }

    @Test
    void wrongTypeReturnsErrorViaHandler() throws IOException {
        engine.lpush("listkey", "a");
        String response = TestSupport.execute(engine, "GET", "listkey");
        assertTrue(response.startsWith("-ERR"));
    }
}
