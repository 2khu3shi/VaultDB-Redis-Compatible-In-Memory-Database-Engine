package com.vaultdb;

import com.vaultdb.data.HashValue;
import com.vaultdb.data.ListValue;
import com.vaultdb.data.SetValue;
import com.vaultdb.data.StoreValue;
import com.vaultdb.data.StringValue;
import com.vaultdb.ttl.TtlStore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public final class VaultDBEngine {
    private final Map<String, StoreValue> store = new HashMap<>();
    private final TtlStore ttlStore = new TtlStore();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    public TtlStore getTtlStore() {
        return ttlStore;
    }

    public void runRead(Runnable action) {
        lock.readLock().lock();
        try {
            action.run();
        } finally {
            lock.readLock().unlock();
        }
    }

    public <T> T runRead(java.util.function.Supplier<T> action) {
        lock.readLock().lock();
        try {
            return action.get();
        } finally {
            lock.readLock().unlock();
        }
    }

    public void runWrite(Runnable action) {
        lock.writeLock().lock();
        try {
            action.run();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public <T> T runWrite(java.util.function.Supplier<T> action) {
        lock.writeLock().lock();
        try {
            return action.get();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public boolean evictIfExpired(String key) {
        return runWrite(() -> lazyEvict(key));
    }

    private boolean lazyEvict(String key) {
        if (ttlStore.isExpired(key)) {
            store.remove(key);
            ttlStore.removeExpiry(key);
            return true;
        }
        return false;
    }

    /** Lazy TTL: evict under write lock before a read when the key has expired. */
    private void maybeEvictBeforeRead(String key) {
        if (ttlStore.isExpired(key)) {
            evictIfExpired(key);
        }
    }

    public String ping() {
        return "PONG";
    }

    public String get(String key) {
        maybeEvictBeforeRead(key);
        return runRead(() -> {
            StoreValue value = store.get(key);
            if (value instanceof StringValue stringValue) {
                return stringValue.get();
            }
            return null;
        });
    }

    public String set(String key, String value) {
        return runWrite(() -> {
            store.put(key, new StringValue(value));
            ttlStore.removeExpiry(key);
            return "OK";
        });
    }

    public String setEx(String key, String value, long seconds) {
        return runWrite(() -> {
            store.put(key, new StringValue(value));
            ttlStore.setExpiry(key, System.currentTimeMillis() + seconds * 1000L);
            return "OK";
        });
    }

    public String setWithOptions(String key, String value, Long exSeconds) {
        return runWrite(() -> {
            store.put(key, new StringValue(value));
            if (exSeconds != null) {
                ttlStore.setExpiry(key, System.currentTimeMillis() + exSeconds * 1000L);
            } else {
                ttlStore.removeExpiry(key);
            }
            return "OK";
        });
    }

    public long del(String... keys) {
        return runWrite(() -> {
            long removed = 0;
            for (String key : keys) {
                lazyEvict(key);
                if (store.remove(key) != null) {
                    ttlStore.removeExpiry(key);
                    removed++;
                }
            }
            return removed;
        });
    }

    public long exists(String... keys) {
        for (String key : keys) {
            maybeEvictBeforeRead(key);
        }
        return runRead(() -> {
            long count = 0;
            for (String key : keys) {
                if (store.containsKey(key)) {
                    count++;
                }
            }
            return count;
        });
    }

    public List<String> keys(String pattern) {
        return runRead(() -> {
            List<String> matches = new ArrayList<>();
            for (String key : store.keySet()) {
                if (ttlStore.isExpired(key)) {
                    continue;
                }
                if (matchPattern(key, pattern)) {
                    matches.add(key);
                }
            }
            return matches;
        });
    }

    public long ttl(String key) {
        maybeEvictBeforeRead(key);
        return runRead(() -> {
            if (!store.containsKey(key)) {
                return -2L;
            }
            return ttlStore.ttlSeconds(key);
        });
    }

    public long expire(String key, long seconds) {
        return runWrite(() -> {
            lazyEvict(key);
            if (!store.containsKey(key)) {
                return 0;
            }
            ttlStore.setExpiry(key, System.currentTimeMillis() + seconds * 1000L);
            return 1;
        });
    }

    public long incr(String key) {
        return runWrite(() -> {
            lazyEvict(key);
            StoreValue current = store.get(key);
            long value;
            if (current == null) {
                value = 1;
            } else if (current instanceof StringValue stringValue) {
                try {
                    value = Long.parseLong(stringValue.get()) + 1;
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("value is not an integer");
                }
            } else {
                throw new IllegalArgumentException("WRONGTYPE Operation against a key holding the wrong kind of value");
            }
            store.put(key, new StringValue(Long.toString(value)));
            return value;
        });
    }

    public long decr(String key) {
        return runWrite(() -> {
            lazyEvict(key);
            StoreValue current = store.get(key);
            long value;
            if (current == null) {
                value = -1;
            } else if (current instanceof StringValue stringValue) {
                try {
                    value = Long.parseLong(stringValue.get()) - 1;
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("value is not an integer");
                }
            } else {
                throw new IllegalArgumentException("WRONGTYPE Operation against a key holding the wrong kind of value");
            }
            store.put(key, new StringValue(Long.toString(value)));
            return value;
        });
    }

    public long lpush(String key, String... values) {
        return runWrite(() -> {
            lazyEvict(key);
            ListValue list = getOrCreateList(key);
            for (String value : values) {
                list.pushLeft(value);
            }
            return list.length();
        });
    }

    public long rpush(String key, String... values) {
        return runWrite(() -> {
            lazyEvict(key);
            ListValue list = getOrCreateList(key);
            for (String value : values) {
                list.pushRight(value);
            }
            return list.length();
        });
    }

    public String lpop(String key) {
        return runWrite(() -> {
            lazyEvict(key);
            StoreValue value = store.get(key);
            if (value == null) {
                return null;
            }
            if (!(value instanceof ListValue list)) {
                throw new IllegalArgumentException("WRONGTYPE Operation against a key holding the wrong kind of value");
            }
            String popped = list.popLeft();
            if (list.length() == 0) {
                store.remove(key);
                ttlStore.removeExpiry(key);
            }
            return popped;
        });
    }

    public String rpop(String key) {
        return runWrite(() -> {
            lazyEvict(key);
            StoreValue value = store.get(key);
            if (value == null) {
                return null;
            }
            if (!(value instanceof ListValue list)) {
                throw new IllegalArgumentException("WRONGTYPE Operation against a key holding the wrong kind of value");
            }
            String popped = list.popRight();
            if (list.length() == 0) {
                store.remove(key);
                ttlStore.removeExpiry(key);
            }
            return popped;
        });
    }

    public long llen(String key) {
        maybeEvictBeforeRead(key);
        return runRead(() -> {
            StoreValue value = store.get(key);
            if (value == null) {
                return 0;
            }
            if (!(value instanceof ListValue list)) {
                throw new IllegalArgumentException("WRONGTYPE Operation against a key holding the wrong kind of value");
            }
            return list.length();
        });
    }

    public List<String> lrange(String key, int start, int stop) {
        maybeEvictBeforeRead(key);
        return runRead(() -> {
            StoreValue value = store.get(key);
            if (value == null) {
                return List.of();
            }
            if (!(value instanceof ListValue list)) {
                throw new IllegalArgumentException("WRONGTYPE Operation against a key holding the wrong kind of value");
            }
            return list.range(start, stop);
        });
    }

    public long hset(String key, String field, String value) {
        return runWrite(() -> {
            lazyEvict(key);
            HashValue hash = getOrCreateHash(key);
            boolean isNew = hash.get(field) == null;
            hash.set(field, value);
            return isNew ? 1 : 0;
        });
    }

    public String hget(String key, String field) {
        maybeEvictBeforeRead(key);
        return runRead(() -> {
            StoreValue value = store.get(key);
            if (value == null) {
                return null;
            }
            if (!(value instanceof HashValue hash)) {
                throw new IllegalArgumentException("WRONGTYPE Operation against a key holding the wrong kind of value");
            }
            return hash.get(field);
        });
    }

    public long hdel(String key, String... fields) {
        return runWrite(() -> {
            lazyEvict(key);
            StoreValue value = store.get(key);
            if (!(value instanceof HashValue hash)) {
                return 0L;
            }
            long removed = 0;
            for (String field : fields) {
                if (hash.delete(field)) {
                    removed++;
                }
            }
            if (hash.size() == 0) {
                store.remove(key);
                ttlStore.removeExpiry(key);
            }
            return removed;
        });
    }

    public Map<String, String> hgetall(String key) {
        maybeEvictBeforeRead(key);
        return runRead(() -> {
            StoreValue value = store.get(key);
            if (value == null) {
                return Map.of();
            }
            if (!(value instanceof HashValue hash)) {
                throw new IllegalArgumentException("WRONGTYPE Operation against a key holding the wrong kind of value");
            }
            return hash.getAll();
        });
    }

    public boolean hexists(String key, String field) {
        maybeEvictBeforeRead(key);
        return runRead(() -> {
            StoreValue value = store.get(key);
            if (!(value instanceof HashValue hash)) {
                return false;
            }
            return hash.exists(field);
        });
    }

    public long sadd(String key, String... members) {
        return runWrite(() -> {
            lazyEvict(key);
            SetValue set = getOrCreateSet(key);
            long added = 0;
            for (String member : members) {
                added += set.add(member);
            }
            return added;
        });
    }

    public long srem(String key, String... members) {
        return runWrite(() -> {
            lazyEvict(key);
            StoreValue value = store.get(key);
            if (!(value instanceof SetValue set)) {
                return 0L;
            }
            long removed = 0;
            for (String member : members) {
                removed += set.remove(member);
            }
            if (set.size() == 0) {
                store.remove(key);
                ttlStore.removeExpiry(key);
            }
            return removed;
        });
    }

    public java.util.Set<String> smembers(String key) {
        maybeEvictBeforeRead(key);
        return runRead(() -> {
            StoreValue value = store.get(key);
            if (value == null) {
                return java.util.Set.of();
            }
            if (!(value instanceof SetValue set)) {
                throw new IllegalArgumentException("WRONGTYPE Operation against a key holding the wrong kind of value");
            }
            return set.members();
        });
    }

    public boolean sismember(String key, String member) {
        maybeEvictBeforeRead(key);
        return runRead(() -> {
            StoreValue value = store.get(key);
            if (!(value instanceof SetValue set)) {
                return false;
            }
            return set.isMember(member);
        });
    }

    public String flushall() {
        return runWrite(() -> {
            store.clear();
            ttlStore.clear();
            return "OK";
        });
    }

    public String type(String key) {
        maybeEvictBeforeRead(key);
        return runRead(() -> {
            StoreValue value = store.get(key);
            if (value == null) {
                return "none";
            }
            return switch (value.type()) {
                case STRING -> "string";
                case LIST -> "list";
                case HASH -> "hash";
                case SET -> "set";
            };
        });
    }

    private ListValue getOrCreateList(String key) {
        StoreValue existing = store.get(key);
        if (existing == null) {
            ListValue list = new ListValue();
            store.put(key, list);
            return list;
        }
        if (existing instanceof ListValue list) {
            return list;
        }
        throw new IllegalArgumentException("WRONGTYPE Operation against a key holding the wrong kind of value");
    }

    private HashValue getOrCreateHash(String key) {
        StoreValue existing = store.get(key);
        if (existing == null) {
            HashValue hash = new HashValue();
            store.put(key, hash);
            return hash;
        }
        if (existing instanceof HashValue hash) {
            return hash;
        }
        throw new IllegalArgumentException("WRONGTYPE Operation against a key holding the wrong kind of value");
    }

    private SetValue getOrCreateSet(String key) {
        StoreValue existing = store.get(key);
        if (existing == null) {
            SetValue set = new SetValue();
            store.put(key, set);
            return set;
        }
        if (existing instanceof SetValue set) {
            return set;
        }
        throw new IllegalArgumentException("WRONGTYPE Operation against a key holding the wrong kind of value");
    }

    private static boolean matchPattern(String key, String pattern) {
        if ("*".equals(pattern)) {
            return true;
        }
        if (pattern.contains("*")) {
            String regex = pattern.replace(".", "\\.").replace("*", ".*");
            return key.matches(regex);
        }
        return key.equals(pattern);
    }
}
