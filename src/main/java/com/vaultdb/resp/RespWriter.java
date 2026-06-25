package com.vaultdb.resp;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public final class RespWriter {
    private final OutputStream out;

    public RespWriter(OutputStream out) {
        this.out = out;
    }

    public void writeSimpleString(String value) throws IOException {
        writeRaw("+" + value + "\r\n");
    }

    public void writeError(String message) throws IOException {
        writeRaw("-ERR " + message + "\r\n");
    }

    public void writeInteger(long value) throws IOException {
        writeRaw(":" + value + "\r\n");
    }

    public void writeBulkString(String value) throws IOException {
        if (value == null) {
            writeRaw("$-1\r\n");
            return;
        }
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        writeRaw("$" + bytes.length + "\r\n");
        out.write(bytes);
        writeRaw("\r\n");
    }

    public void writeArray(Collection<String> values) throws IOException {
        writeRaw("*" + values.size() + "\r\n");
        for (String value : values) {
            writeBulkString(value);
        }
    }

    public void writeFlatArray(List<String> values) throws IOException {
        writeArray(values);
    }

    public void writeHashArray(Map<String, String> hash) throws IOException {
        writeRaw("*" + (hash.size() * 2) + "\r\n");
        for (Map.Entry<String, String> entry : hash.entrySet()) {
            writeBulkString(entry.getKey());
            writeBulkString(entry.getValue());
        }
    }

    private void writeRaw(String value) throws IOException {
        out.write(value.getBytes(StandardCharsets.UTF_8));
        out.flush();
    }
}
