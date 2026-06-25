package com.vaultdb.resp;

import java.util.List;

public final class RespValue {
    private final RespType type;
    private final String simpleString;
    private final String error;
    private final long integer;
    private final String bulkString;
    private final List<RespValue> array;

    private RespValue(RespType type, String simpleString, String error, long integer,
                      String bulkString, List<RespValue> array) {
        this.type = type;
        this.simpleString = simpleString;
        this.error = error;
        this.integer = integer;
        this.bulkString = bulkString;
        this.array = array;
    }

    public static RespValue simpleString(String value) {
        return new RespValue(RespType.SIMPLE_STRING, value, null, 0, null, null);
    }

    public static RespValue error(String message) {
        return new RespValue(RespType.ERROR, null, message, 0, null, null);
    }

    public static RespValue integer(long value) {
        return new RespValue(RespType.INTEGER, null, null, value, null, null);
    }

    public static RespValue bulkString(String value) {
        return new RespValue(RespType.BULK_STRING, null, null, 0, value, null);
    }

    public static RespValue nullBulk() {
        return new RespValue(RespType.BULK_STRING, null, null, 0, null, null);
    }

    public static RespValue array(List<RespValue> values) {
        return new RespValue(RespType.ARRAY, null, null, 0, null, List.copyOf(values));
    }

    public static RespValue nullArray() {
        return new RespValue(RespType.ARRAY, null, null, 0, null, null);
    }

    public RespType type() {
        return type;
    }

    public String bulkString() {
        return bulkString;
    }

    public List<RespValue> array() {
        return array;
    }
}
