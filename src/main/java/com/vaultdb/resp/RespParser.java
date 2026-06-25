package com.vaultdb.resp;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class RespParser {
    private final InputStream in;

    public RespParser(InputStream in) {
        this.in = in;
    }

    public List<String> readCommand() throws IOException {
        RespValue value = readValue();
        if (value == null || value.type() != RespType.ARRAY) {
            throw new IOException("Expected RESP array");
        }
        List<String> command = new ArrayList<>();
        for (RespValue element : value.array()) {
            if (element.type() != RespType.BULK_STRING) {
                throw new IOException("Command elements must be bulk strings");
            }
            command.add(element.bulkString());
        }
        return command;
    }

    private RespValue readValue() throws IOException {
        int prefix = in.read();
        if (prefix == -1) {
            return null;
        }
        return switch (prefix) {
            case '+' -> readSimpleString();
            case '-' -> readError();
            case ':' -> readInteger();
            case '$' -> readBulkString();
            case '*' -> readArray();
            default -> throw new IOException("Unknown RESP prefix: " + (char) prefix);
        };
    }

    private RespValue readSimpleString() throws IOException {
        return RespValue.simpleString(readLine());
    }

    private RespValue readError() throws IOException {
        return RespValue.error(readLine());
    }

    private RespValue readInteger() throws IOException {
        return RespValue.integer(Long.parseLong(readLine()));
    }

    private RespValue readBulkString() throws IOException {
        int length = Integer.parseInt(readLine());
        if (length == -1) {
            return RespValue.nullBulk();
        }
        byte[] data = readBytes(length);
        readCrLf();
        return RespValue.bulkString(new String(data, StandardCharsets.UTF_8));
    }

    private RespValue readArray() throws IOException {
        int count = Integer.parseInt(readLine());
        if (count == -1) {
            return RespValue.nullArray();
        }
        List<RespValue> elements = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            elements.add(readValue());
        }
        return RespValue.array(elements);
    }

    private String readLine() throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int previous = -1;
        int current;
        while ((current = in.read()) != -1) {
            if (previous == '\r' && current == '\n') {
                byte[] bytes = buffer.toByteArray();
                if (bytes.length > 0 && bytes[bytes.length - 1] == '\r') {
                    return new String(bytes, 0, bytes.length - 1, StandardCharsets.UTF_8);
                }
                return buffer.toString(StandardCharsets.UTF_8);
            }
            if (previous != -1) {
                buffer.write(previous);
            }
            previous = current;
        }
        throw new IOException("Unexpected end of stream while reading line");
    }

    private byte[] readBytes(int length) throws IOException {
        byte[] data = new byte[length];
        int offset = 0;
        while (offset < length) {
            int read = in.read(data, offset, length - offset);
            if (read == -1) {
                throw new IOException("Unexpected end of stream while reading bulk string");
            }
            offset += read;
        }
        return data;
    }

    private void readCrLf() throws IOException {
        int cr = in.read();
        int lf = in.read();
        if (cr != '\r' || lf != '\n') {
            throw new IOException("Expected CRLF after bulk string");
        }
    }
}
