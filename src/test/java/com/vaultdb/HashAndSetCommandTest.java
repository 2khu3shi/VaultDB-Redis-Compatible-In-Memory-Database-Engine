package com.vaultdb;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class HashAndSetCommandTest {
    private VaultDBEngine engine;

    @BeforeEach
    void setUp() {
        engine = new VaultDBEngine();
    }

    @Test
    void hashOperations() {
        assertEquals(1, engine.hset("user", "name", "ankit"));
        assertEquals(0, engine.hset("user", "name", "ankit"));
        assertEquals("ankit", engine.hget("user", "name"));
        assertTrue(engine.hexists("user", "name"));
        assertEquals(Map.of("name", "ankit"), engine.hgetall("user"));
        assertEquals(1, engine.hdel("user", "name"));
        assertEquals(0, engine.hgetall("user").size());
    }

    @Test
    void setOperations() {
        assertEquals(2, engine.sadd("tags", "java", "redis"));
        assertEquals(0, engine.sadd("tags", "java"));
        assertTrue(engine.sismember("tags", "redis"));
        assertEquals(Set.of("java", "redis"), engine.smembers("tags"));
        assertEquals(1, engine.srem("tags", "java"));
        assertEquals(Set.of("redis"), engine.smembers("tags"));
    }
}
