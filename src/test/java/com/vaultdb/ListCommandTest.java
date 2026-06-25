package com.vaultdb;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ListCommandTest {
    private VaultDBEngine engine;

    @BeforeEach
    void setUp() {
        engine = new VaultDBEngine();
    }

    @Test
    void listPushPopAndRange() {
        assertEquals(2, engine.lpush("items", "b", "a"));
        assertEquals(3, engine.rpush("items", "c"));
        assertEquals(List.of("a", "b", "c"), engine.lrange("items", 0, -1));
        assertEquals("a", engine.lpop("items"));
        assertEquals("c", engine.rpop("items"));
        assertEquals(1, engine.llen("items"));
    }

    @Test
    void emptyListAfterPopRemovesKey() {
        engine.lpush("solo", "x");
        assertEquals("x", engine.lpop("solo"));
        assertNull(engine.get("solo"));
        assertEquals(0, engine.llen("solo"));
    }
}
