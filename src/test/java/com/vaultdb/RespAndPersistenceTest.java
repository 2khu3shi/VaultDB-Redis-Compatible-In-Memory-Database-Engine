package com.vaultdb;

import com.vaultdb.persistence.AofReplayer;
import com.vaultdb.persistence.AofWriter;
import com.vaultdb.resp.RespParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RespAndPersistenceTest {
    @Test
    void respParserReadsArrayOfBulkStrings() throws IOException {
        String raw = "*3\r\n$3\r\nSET\r\n$3\r\nkey\r\n$5\r\nvalue\r\n";
        List<String> command = TestSupport.parseCommand(raw);
        assertEquals(List.of("SET", "key", "value"), command);
    }

    @Test
    void aofPersistsAndReplaysCommands() throws IOException {
        java.nio.file.Path aof = TestSupport.tempAof();
        VaultDBEngine writerEngine = new VaultDBEngine();
        try (AofWriter aofWriter = new AofWriter(aof)) {
            aofWriter.open();
            var handler = new com.vaultdb.commands.CommandHandler(writerEngine, aofWriter, true);
            var out = new java.io.ByteArrayOutputStream();
            var respWriter = new com.vaultdb.resp.RespWriter(out);
            handler.handle(List.of("SET", "persist", "yes"), respWriter);
            handler.handle(List.of("INCR", "counter"), respWriter);
        }

        VaultDBEngine replayEngine = new VaultDBEngine();
        var replayHandler = new com.vaultdb.commands.CommandHandler(replayEngine, null, false);
        int replayed = AofReplayer.replay(aof, replayHandler::replay);
        assertEquals(2, replayed);
        assertEquals("yes", replayEngine.get("persist"));
        assertEquals("1", replayEngine.get("counter"));
        Files.deleteIfExists(aof);
    }
}
