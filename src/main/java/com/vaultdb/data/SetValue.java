package com.vaultdb.data;

import java.util.LinkedHashSet;
import java.util.Set;

public final class SetValue implements StoreValue {
    private final LinkedHashSet<String> members = new LinkedHashSet<>();

    public int add(String member) {
        return members.add(member) ? 1 : 0;
    }

    public int remove(String member) {
        return members.remove(member) ? 1 : 0;
    }

    public boolean isMember(String member) {
        return members.contains(member);
    }

    public Set<String> members() {
        return Set.copyOf(members);
    }

    public int size() {
        return members.size();
    }

    @Override
    public ValueType type() {
        return ValueType.SET;
    }
}
