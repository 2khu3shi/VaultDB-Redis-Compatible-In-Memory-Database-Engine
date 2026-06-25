package com.vaultdb;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class TcpIntegrationTest {
    private VaultDBServer server;
    private ExecutorService serverThread;
    private Path aofPath;
    private int port;

    @BeforeEach
    void setUp() throws Exception {
        aofPath = Files.createTempFile("vaultdb-tcp-", ".aof");
        try (ServerSocket probe = new ServerSocket(0)) {
            port = probe.getLocalPort();
        }
        server = new VaultDBServer(port, aofPath, false);
        serverThread = Executors.newSingleThreadExecutor();
        serverThread.submit(() -> {
            try {
                server.start();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        Thread.sleep(300);
    }

    @AfterEach
    void tearDown() throws Exception {
        server.close();
        serverThread.shutdownNow();
        serverThread.awaitTermination(2, TimeUnit.SECONDS);
        Files.deleteIfExists(aofPath);
    }

    @Test
    void redisCliStylePingAndSetGet() throws Exception {
        try (Socket socket = new Socket("127.0.0.1", port)) {
            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();

            sendCommand(out, "PING");
            assertEquals("+PONG\r\n", readLine(in));

            sendCommand(out, "SET", "cli", "works");
            assertEquals("+OK\r\n", readLine(in));

            sendCommand(out, "GET", "cli");
            assertEquals("$5\r\n", readLine(in));
            assertEquals("works\r\n", readLine(in));
        }
    }

    private static void sendCommand(OutputStream out, String... args) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append('*').append(args.length).append("\r\n");
        for (String arg : args) {
            sb.append('$').append(arg.getBytes(StandardCharsets.UTF_8).length).append("\r\n");
            sb.append(arg).append("\r\n");
        }
        out.write(sb.toString().getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    private static String readLine(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder();
        int previous = -1;
        int current;
        while ((current = in.read()) != -1) {
            if (previous == '\r' && current == '\n') {
                sb.setLength(sb.length() - 1);
                return sb + "\r\n";
            }
            sb.append((char) current);
            previous = current;
        }
        throw new IOException("Stream closed");
    }
}
