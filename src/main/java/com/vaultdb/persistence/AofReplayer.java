package com.vaultdb.persistence;

import com.vaultdb.resp.RespParser;
import com.vaultdb.resp.RespValue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class AofReplayer {
    private AofReplayer() {
    }

    public static int replay(Path aofPath, Consumer<List<String>> commandConsumer) throws IOException {
        if (!Files.exists(aofPath) || Files.size(aofPath) == 0) {
            return 0;
        }
        int count = 0;
        try (InputStream in = Files.newInputStream(aofPath)) {
            RespParser parser = new RespParser(in);
            while (true) {
                try {
                    List<String> command = parser.readCommand();
                    if (command.isEmpty()) {
                        continue;
                    }
                    commandConsumer.accept(command);
                    count++;
                } catch (IOException e) {
                    if (e.getMessage() != null && e.getMessage().contains("Unexpected end of stream")) {
                        break;
                    }
                    throw e;
                }
            }
        }
        return count;
    }

    public static List<String> toUpperCommand(List<String> command) {
        List<String> normalized = new ArrayList<>(command.size());
        for (int i = 0; i < command.size(); i++) {
            normalized.add(i == 0 ? command.get(i).toUpperCase() : command.get(i));
        }
        return normalized;
    }
}
