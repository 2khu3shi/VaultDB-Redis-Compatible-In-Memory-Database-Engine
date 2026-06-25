package com.vaultdb.ttl;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class TtlStore {
    private final Map<String, Long> expiryTimes = new ConcurrentHashMap<>();

    public void setExpiry(String key, long expireAtMillis) {
        expiryTimes.put(key, expireAtMillis);
    }

    public void removeExpiry(String key) {
        expiryTimes.remove(key);
    }

    public Long getExpiry(String key) {
        return expiryTimes.get(key);
    }

    public boolean isExpired(String key) {
        Long expireAt = expiryTimes.get(key);
        if (expireAt == null) {
            return false;
        }
        return System.currentTimeMillis() >= expireAt;
    }

    public long ttlSeconds(String key) {
        Long expireAt = expiryTimes.get(key);
        if (expireAt == null) {
            return -1;
        }
        long remaining = expireAt - System.currentTimeMillis();
        if (remaining <= 0) {
            return -2;
        }
        return (remaining + 999) / 1000;
    }

    public Map<String, Long> snapshot() {
        return Map.copyOf(expiryTimes);
    }

    public void clear() {
        expiryTimes.clear();
    }
}
