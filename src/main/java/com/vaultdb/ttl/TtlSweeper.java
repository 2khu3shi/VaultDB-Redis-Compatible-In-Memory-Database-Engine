package com.vaultdb.ttl;

import com.vaultdb.VaultDBEngine;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class TtlSweeper implements Runnable {
    private static final long SWEEP_INTERVAL_MS = 100;

    private final VaultDBEngine engine;
    private volatile boolean running = true;

    public TtlSweeper(VaultDBEngine engine) {
        this.engine = engine;
    }

    public void stop() {
        running = false;
    }

    @Override
    public void run() {
        while (running) {
            try {
                Thread.sleep(SWEEP_INTERVAL_MS);
                sweepExpiredKeys();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private void sweepExpiredKeys() {
        Map<String, Long> snapshot = engine.getTtlStore().snapshot();
        long now = System.currentTimeMillis();
        List<String> expired = new ArrayList<>();
        for (Map.Entry<String, Long> entry : snapshot.entrySet()) {
            if (entry.getValue() <= now) {
                expired.add(entry.getKey());
            }
        }
        for (String key : expired) {
            engine.evictIfExpired(key);
        }
    }

    public void sweepOnce() {
        sweepExpiredKeys();
    }
}
