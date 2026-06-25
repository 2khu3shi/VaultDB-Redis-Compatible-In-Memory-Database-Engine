package com.vaultdb;

import com.vaultdb.commands.CommandHandler;
import com.vaultdb.persistence.AofWriter;
import com.vaultdb.resp.RespParser;
import com.vaultdb.resp.RespWriter;
import com.vaultdb.ttl.TtlSweeper;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class TestSupport {
    private TestSupport() {
    }

    static CommandHandler newHandler(VaultDBEngine engine) {
        return new CommandHandler(engine, null, false);
    }

    static String execute(VaultDBEngine engine, String... args) throws IOException {
        CommandHandler handler = newHandler(engine);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        RespWriter writer = new RespWriter(out);
        handler.handle(List.of(args), writer);
        return out.toString(StandardCharsets.UTF_8);
    }

    static List<String> parseCommand(String resp) throws IOException {
        ByteArrayInputStream in = new ByteArrayInputStream(resp.getBytes(StandardCharsets.UTF_8));
        return new RespParser(in).readCommand();
    }

    static Path tempAof() throws IOException {
        return Files.createTempFile("vaultdb-test-", ".aof");
    }

    static void runConcurrentReads(VaultDBEngine engine, int threads, int iterations) throws InterruptedException {
        engine.set("counter", "0");
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    for (int j = 0; j < iterations; j++) {
                        engine.get("counter");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        done.await();
        pool.shutdown();
    }

    static void runTtlSweeperOnce(VaultDBEngine engine) {
        new TtlSweeper(engine).sweepOnce();
    }
}
