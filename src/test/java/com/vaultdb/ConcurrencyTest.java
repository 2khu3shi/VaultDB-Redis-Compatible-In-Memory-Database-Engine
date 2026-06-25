package com.vaultdb;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ConcurrencyTest {
    private VaultDBEngine engine;

    @BeforeEach
    void setUp() {
        engine = new VaultDBEngine();
    }

    @Test
    void parallelReadsDoNotBlockEachOther() throws InterruptedException {
        engine.set("shared", "value");
        TestSupport.runConcurrentReads(engine, 50, 100);
        assertEquals("value", engine.get("shared"));
    }

    @Test
    void writesAreIsolatedFromConcurrentReaders() throws InterruptedException {
        engine.set("key", "initial");
        AtomicInteger readValues = new AtomicInteger();
        Thread writer = new Thread(() -> {
            for (int i = 0; i < 100; i++) {
                engine.set("key", "v" + i);
            }
        });
        Thread reader = new Thread(() -> {
            for (int i = 0; i < 100; i++) {
                String value = engine.get("key");
                if (value != null && value.startsWith("v")) {
                    readValues.incrementAndGet();
                }
            }
        });
        writer.start();
        reader.start();
        writer.join();
        reader.join();
        assertTrue(readValues.get() > 0);
        assertTrue(engine.get("key").startsWith("v"));
    }
}
