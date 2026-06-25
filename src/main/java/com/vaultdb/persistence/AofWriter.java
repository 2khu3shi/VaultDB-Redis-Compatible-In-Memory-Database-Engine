package com.vaultdb.persistence;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

public final class AofWriter implements AutoCloseable {
    private final Path aofPath;
    private final ReentrantLock lock = new ReentrantLock();
    private BufferedWriter writer;

    public AofWriter(Path aofPath) {
        this.aofPath = aofPath;
    }

    public void open() throws IOException {
        lock.lock();
        try {
            if (writer == null) {
                writer = Files.newBufferedWriter(
                        aofPath,
                        StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.APPEND
                );
            }
        } finally {
            lock.unlock();
        }
    }

    public void appendCommand(List<String> command) {
        lock.lock();
        try {
            if (writer == null) {
                return;
            }
            writer.write("*" + command.size() + "\r\n");
            for (String arg : command) {
                byte[] bytes = arg.getBytes(StandardCharsets.UTF_8);
                writer.write("$" + bytes.length + "\r\n");
                writer.write(arg);
                writer.write("\r\n");
            }
            writer.flush();
        } catch (IOException e) {
            throw new RuntimeException("Failed to append AOF entry", e);
        } finally {
            lock.unlock();
        }
    }

    public Path path() {
        return aofPath;
    }

    @Override
    public void close() throws IOException {
        lock.lock();
        try {
            if (writer != null) {
                writer.close();
                writer = null;
            }
        } finally {
            lock.unlock();
        }
    }
}
