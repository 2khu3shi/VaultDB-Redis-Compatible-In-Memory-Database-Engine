package com.vaultdb.data;

public final class StringValue implements StoreValue {
    private String value;

    public StringValue(String value) {
        this.value = value;
    }

    public String get() {
        return value;
    }

    public void set(String value) {
        this.value = value;
    }

    @Override
    public ValueType type() {
        return ValueType.STRING;
    }
}
