package com.vaultdb;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StringCommandTest {
    private VaultDBEngine engine;

    @BeforeEach
    void setUp() {
        engine = new VaultDBEngine();
    }

    @Test
    void pingReturnsPong() {
        assertEquals("PONG", engine.ping());
    }

    @Test
    void setAndGetString() throws IOException {
        String response = TestSupport.execute(engine, "SET", "name", "vault");
        assertTrue(response.startsWith("+OK"));
        assertEquals("vault", engine.get("name"));
    }

    @Test
    void getMissingKeyReturnsNull() {
        assertNull(engine.get("missing"));
    }

    @Test
    void delRemovesKeys() {
        engine.set("a", "1");
        engine.set("b", "2");
        assertEquals(2, engine.del("a", "b"));
        assertEquals(0, engine.exists("a", "b"));
    }

    @Test
    void incrAndDecrWork() {
        assertEquals(1, engine.incr("count"));
        assertEquals(2, engine.incr("count"));
        assertEquals(1, engine.decr("count"));
        assertEquals("1", engine.get("count"));
    }
}
