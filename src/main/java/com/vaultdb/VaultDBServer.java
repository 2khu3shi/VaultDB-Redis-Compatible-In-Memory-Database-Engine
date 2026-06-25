package com.vaultdb;

import com.vaultdb.commands.CommandHandler;
import com.vaultdb.persistence.AofReplayer;
import com.vaultdb.persistence.AofWriter;
import com.vaultdb.resp.RespParser;
import com.vaultdb.resp.RespWriter;
import com.vaultdb.ttl.TtlSweeper;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class VaultDBServer implements AutoCloseable {
    public static final int DEFAULT_PORT = 6379;
    public static final int THREAD_POOL_SIZE = 200;

    private final VaultDBEngine engine;
    private final CommandHandler commandHandler;
    private final AofWriter aofWriter;
    private final TtlSweeper ttlSweeper;
    private final Thread sweeperThread;
    private final int port;
    private ServerSocket serverSocket;
    private ExecutorService executor;

    public VaultDBServer(int port, Path aofPath, boolean enablePersistence) throws IOException {
        this.port = port;
        this.engine = new VaultDBEngine();
        this.aofWriter = enablePersistence ? new AofWriter(aofPath) : null;
        if (aofWriter != null) {
            aofWriter.open();
            CommandHandler replayHandler = new CommandHandler(engine, null, false);
            AofReplayer.replay(aofPath, replayHandler::replay);
        }
        this.commandHandler = new CommandHandler(engine, aofWriter, enablePersistence);
        this.ttlSweeper = new TtlSweeper(engine);
        this.sweeperThread = new Thread(ttlSweeper, "vaultdb-ttl-sweeper");
        this.sweeperThread.setDaemon(true);
    }

    public VaultDBEngine engine() {
        return engine;
    }

    public CommandHandler commandHandler() {
        return commandHandler;
    }

    public int port() {
        return port;
    }

    public void start() throws IOException {
        serverSocket = new ServerSocket(port);
        executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        sweeperThread.start();
        System.out.println("VaultDB listening on port " + port + " with " + THREAD_POOL_SIZE + " worker threads");
        while (!serverSocket.isClosed()) {
            try {
                Socket client = serverSocket.accept();
                executor.submit(() -> handleClient(client));
            } catch (IOException e) {
                if (!serverSocket.isClosed()) {
                    System.err.println("Accept failed: " + e.getMessage());
                }
            }
        }
    }

    private void handleClient(Socket client) {
        try (client;
             InputStream in = client.getInputStream();
             OutputStream out = client.getOutputStream()) {
            RespParser parser = new RespParser(in);
            RespWriter writer = new RespWriter(out);
            while (!client.isClosed()) {
                List<String> command = parser.readCommand();
                if (command.isEmpty()) {
                    continue;
                }
                if (!commandHandler.handle(command, writer)) {
                    break;
                }
            }
        } catch (IOException e) {
            // Client disconnected
        }
    }

    @Override
    public void close() throws IOException {
        ttlSweeper.stop();
        sweeperThread.interrupt();
        if (serverSocket != null && !serverSocket.isClosed()) {
            serverSocket.close();
        }
        if (executor != null) {
            executor.shutdown();
            try {
                executor.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if (aofWriter != null) {
            aofWriter.close();
        }
    }

    public static void main(String[] args) throws IOException {
        int port = DEFAULT_PORT;
        Path aofPath = Path.of("appendonly.aof");
        boolean persistence = true;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--port" -> port = Integer.parseInt(args[++i]);
                case "--aof" -> aofPath = Path.of(args[++i]);
                case "--no-persistence" -> persistence = false;
                default -> throw new IllegalArgumentException("Unknown argument: " + args[i]);
            }
        }

        VaultDBServer server = new VaultDBServer(port, aofPath, persistence);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                server.close();
            } catch (IOException ignored) {
            }
        }));
        server.start();
    }
}
