package com.vaultdb.data;

import java.util.LinkedHashMap;
import java.util.Map;

public final class HashValue implements StoreValue {
    private final LinkedHashMap<String, String> fields = new LinkedHashMap<>();

    public void set(String field, String value) {
        fields.put(field, value);
    }

    public String get(String field) {
        return fields.get(field);
    }

    public boolean delete(String field) {
        return fields.remove(field) != null;
    }

    public boolean exists(String field) {
        return fields.containsKey(field);
    }

    public Map<String, String> getAll() {
        return Map.copyOf(fields);
    }

    public int size() {
        return fields.size();
    }

    @Override
    public ValueType type() {
        return ValueType.HASH;
    }
}
