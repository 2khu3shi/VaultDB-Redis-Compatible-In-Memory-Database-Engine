package com.vaultdb;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TtlCommandTest {
    private VaultDBEngine engine;

    @BeforeEach
    void setUp() {
        engine = new VaultDBEngine();
    }

    @Test
    void expireAndTtl() throws InterruptedException {
        engine.set("temp", "value");
        assertEquals(1, engine.expire("temp", 2));
        assertTrue(engine.ttl("temp") > 0);
        Thread.sleep(2100);
        assertNull(engine.get("temp"));
        assertEquals(-2, engine.ttl("temp"));
    }

    @Test
    void setExExpiresKey() throws InterruptedException {
        engine.setEx("session", "abc", 1);
        assertEquals("abc", engine.get("session"));
        Thread.sleep(1100);
        assertNull(engine.get("session"));
    }

    @Test
    void lazyEvictionOnRead() throws InterruptedException {
        engine.setEx("lazy", "1", 1);
        Thread.sleep(1100);
        assertNull(engine.get("lazy"));
    }

    @Test
    void activeSweeperEvictsExpiredKeys() throws InterruptedException {
        engine.setEx("sweep", "data", 1);
        Thread.sleep(1100);
        TestSupport.runTtlSweeperOnce(engine);
        assertEquals(-2, engine.ttl("sweep"));
        assertNull(engine.get("sweep"));
    }
}
