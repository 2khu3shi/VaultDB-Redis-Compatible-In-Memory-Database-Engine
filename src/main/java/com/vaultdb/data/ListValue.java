package com.vaultdb.data;

import java.util.LinkedList;
import java.util.List;

public final class ListValue implements StoreValue {
    private final LinkedList<String> items = new LinkedList<>();

    public void pushLeft(String value) {
        items.addFirst(value);
    }

    public void pushRight(String value) {
        items.addLast(value);
    }

    public String popLeft() {
        return items.isEmpty() ? null : items.removeFirst();
    }

    public String popRight() {
        return items.isEmpty() ? null : items.removeLast();
    }

    public int length() {
        return items.size();
    }

    public List<String> range(int start, int stop) {
        int size = items.size();
        if (size == 0) {
            return List.of();
        }
        int from = normalizeIndex(start, size);
        int to = normalizeIndex(stop, size);
        if (from > to) {
            return List.of();
        }
        return List.copyOf(items.subList(from, to + 1));
    }

    private static int normalizeIndex(int index, int size) {
        if (index < 0) {
            index = size + index;
        }
        if (index < 0) {
            return 0;
        }
        if (index >= size) {
            return size - 1;
        }
        return index;
    }

    @Override
    public ValueType type() {
        return ValueType.LIST;
    }
}
