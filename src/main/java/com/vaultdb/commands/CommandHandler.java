package com.vaultdb.commands;

import com.vaultdb.VaultDBEngine;
import com.vaultdb.persistence.AofWriter;
import com.vaultdb.resp.RespWriter;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class CommandHandler {
    private static final Set<String> MUTATING_COMMANDS = Set.of(
            "SET", "SETEX", "DEL", "EXPIRE", "INCR", "DECR",
            "LPUSH", "RPUSH", "LPOP", "RPOP",
            "HSET", "HDEL", "SADD", "SREM", "FLUSHALL"
    );

    private final VaultDBEngine engine;
    private final AofWriter aofWriter;
    private final boolean persistenceEnabled;

    public CommandHandler(VaultDBEngine engine, AofWriter aofWriter, boolean persistenceEnabled) {
        this.engine = engine;
        this.aofWriter = aofWriter;
        this.persistenceEnabled = persistenceEnabled;
    }

    /** @return true if the connection should remain open */
    public boolean handle(List<String> rawCommand, RespWriter writer) throws IOException {
        if (rawCommand.isEmpty()) {
            writer.writeError("empty command");
            return true;
        }

        List<String> command = normalize(rawCommand);
        String cmd = command.get(0);

        try {
            switch (cmd) {
                case "PING" -> {
                    if (command.size() > 1) {
                        writer.writeBulkString(command.get(1));
                    } else {
                        writer.writeSimpleString(engine.ping());
                    }
                }
                case "ECHO" -> writer.writeBulkString(requireArg(command, 1, cmd));
                case "QUIT" -> {
                    writer.writeSimpleString("OK");
                    return false;
                }
                case "SELECT", "AUTH" -> writer.writeSimpleString("OK");
                case "SET" -> handleSet(command, writer);
                case "SETEX" -> handleSetEx(command, writer);
                case "GET" -> writer.writeBulkString(engine.get(requireArg(command, 1, cmd)));
                case "DEL" -> writer.writeInteger(engine.del(command.subList(1, command.size()).toArray(String[]::new)));
                case "EXISTS" -> writer.writeInteger(engine.exists(command.subList(1, command.size()).toArray(String[]::new)));
                case "KEYS" -> writer.writeArray(engine.keys(requireArg(command, 1, cmd)));
                case "TTL" -> writer.writeInteger(engine.ttl(requireArg(command, 1, cmd)));
                case "EXPIRE" -> writer.writeInteger(engine.expire(requireArg(command, 1, cmd), parseLong(requireArg(command, 2, cmd))));
                case "INCR" -> writer.writeInteger(engine.incr(requireArg(command, 1, cmd)));
                case "DECR" -> writer.writeInteger(engine.decr(requireArg(command, 1, cmd)));
                case "LPUSH" -> writer.writeInteger(engine.lpush(requireArg(command, 1, cmd),
                        command.subList(2, command.size()).toArray(String[]::new)));
                case "RPUSH" -> writer.writeInteger(engine.rpush(requireArg(command, 1, cmd),
                        command.subList(2, command.size()).toArray(String[]::new)));
                case "LPOP" -> writer.writeBulkString(engine.lpop(requireArg(command, 1, cmd)));
                case "RPOP" -> writer.writeBulkString(engine.rpop(requireArg(command, 1, cmd)));
                case "LLEN" -> writer.writeInteger(engine.llen(requireArg(command, 1, cmd)));
                case "LRANGE" -> writer.writeFlatArray(engine.lrange(
                        requireArg(command, 1, cmd),
                        (int) parseLong(requireArg(command, 2, cmd)),
                        (int) parseLong(requireArg(command, 3, cmd))));
                case "HSET" -> writer.writeInteger(engine.hset(requireArg(command, 1, cmd),
                        requireArg(command, 2, cmd), requireArg(command, 3, cmd)));
                case "HGET" -> writer.writeBulkString(engine.hget(requireArg(command, 1, cmd), requireArg(command, 2, cmd)));
                case "HDEL" -> writer.writeInteger(engine.hdel(requireArg(command, 1, cmd),
                        command.subList(2, command.size()).toArray(String[]::new)));
                case "HGETALL" -> writer.writeHashArray(engine.hgetall(requireArg(command, 1, cmd)));
                case "HEXISTS" -> writer.writeInteger(engine.hexists(requireArg(command, 1, cmd),
                        requireArg(command, 2, cmd)) ? 1 : 0);
                case "SADD" -> writer.writeInteger(engine.sadd(requireArg(command, 1, cmd),
                        command.subList(2, command.size()).toArray(String[]::new)));
                case "SREM" -> writer.writeInteger(engine.srem(requireArg(command, 1, cmd),
                        command.subList(2, command.size()).toArray(String[]::new)));
                case "SMEMBERS" -> writer.writeArray(engine.smembers(requireArg(command, 1, cmd)));
                case "SISMEMBER" -> writer.writeInteger(engine.sismember(requireArg(command, 1, cmd),
                        requireArg(command, 2, cmd)) ? 1 : 0);
                case "TYPE" -> writer.writeSimpleString(engine.type(requireArg(command, 1, cmd)));
                case "FLUSHALL" -> writer.writeSimpleString(engine.flushall());
                default -> writer.writeError("unknown command '" + cmd + "'");
            }
            persistIfNeeded(command);
        } catch (IllegalArgumentException e) {
            writer.writeError(e.getMessage());
        }
        return true;
    }

    private void handleSet(List<String> command, RespWriter writer) throws IOException {
        String key = requireArg(command, 1, "SET");
        String value = requireArg(command, 2, "SET");
        Long exSeconds = null;
        if (command.size() == 5 && "EX".equalsIgnoreCase(command.get(3))) {
            exSeconds = parseLong(requireArg(command, 4, "SET"));
        } else if (command.size() != 3) {
            throw new IllegalArgumentException("wrong number of arguments for 'SET' command");
        }
        if (exSeconds != null) {
            writer.writeSimpleString(engine.setWithOptions(key, value, exSeconds));
        } else {
            writer.writeSimpleString(engine.set(key, value));
        }
    }

    private void handleSetEx(List<String> command, RespWriter writer) throws IOException {
        String key = requireArg(command, 1, "SETEX");
        long seconds = parseLong(requireArg(command, 2, "SETEX"));
        String value = requireArg(command, 3, "SETEX");
        writer.writeSimpleString(engine.setEx(key, value, seconds));
    }

    private void persistIfNeeded(List<String> command) {
        if (!persistenceEnabled || aofWriter == null) {
            return;
        }
        if (MUTATING_COMMANDS.contains(command.get(0))) {
            aofWriter.appendCommand(command);
        }
    }

    private static List<String> normalize(List<String> command) {
        if (command.isEmpty()) {
            return command;
        }
        java.util.ArrayList<String> normalized = new java.util.ArrayList<>(command);
        normalized.set(0, normalized.get(0).toUpperCase(Locale.ROOT));
        return normalized;
    }

    private static String requireArg(List<String> command, int index, String cmd) {
        if (index >= command.size()) {
            throw new IllegalArgumentException("wrong number of arguments for '" + cmd + "' command");
        }
        return command.get(index);
    }

    private static long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("value is not an integer or out of range");
        }
    }

    public void replay(List<String> rawCommand) {
        List<String> command = normalize(rawCommand);
        String cmd = command.get(0);
        switch (cmd) {
            case "SET" -> {
                String key = requireArg(command, 1, cmd);
                String value = requireArg(command, 2, cmd);
                if (command.size() == 5 && "EX".equalsIgnoreCase(command.get(3))) {
                    engine.setWithOptions(key, value, parseLong(requireArg(command, 4, cmd)));
                } else {
                    engine.set(key, value);
                }
            }
            case "SETEX" -> engine.setEx(requireArg(command, 1, cmd), requireArg(command, 3, cmd),
                    parseLong(requireArg(command, 2, cmd)));
            case "DEL" -> engine.del(command.subList(1, command.size()).toArray(String[]::new));
            case "EXPIRE" -> engine.expire(requireArg(command, 1, cmd), parseLong(requireArg(command, 2, cmd)));
            case "INCR" -> engine.incr(requireArg(command, 1, cmd));
            case "DECR" -> engine.decr(requireArg(command, 1, cmd));
            case "LPUSH" -> engine.lpush(requireArg(command, 1, cmd),
                    command.subList(2, command.size()).toArray(String[]::new));
            case "RPUSH" -> engine.rpush(requireArg(command, 1, cmd),
                    command.subList(2, command.size()).toArray(String[]::new));
            case "LPOP" -> engine.lpop(requireArg(command, 1, cmd));
            case "RPOP" -> engine.rpop(requireArg(command, 1, cmd));
            case "HSET" -> engine.hset(requireArg(command, 1, cmd),
                    requireArg(command, 2, cmd), requireArg(command, 3, cmd));
            case "HDEL" -> engine.hdel(requireArg(command, 1, cmd),
                    command.subList(2, command.size()).toArray(String[]::new));
            case "SADD" -> engine.sadd(requireArg(command, 1, cmd),
                    command.subList(2, command.size()).toArray(String[]::new));
            case "SREM" -> engine.srem(requireArg(command, 1, cmd),
                    command.subList(2, command.size()).toArray(String[]::new));
            case "FLUSHALL" -> engine.flushall();
            default -> {
            }
        }
    }
}
