package org.faded.worldvoice.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

public class LogJsonL {
    private static final Logger LOG = LoggerFactory.getLogger(LogJsonL.class);

    private static Path ensureLogPath() {
        Path dir = Path.of("logs", "worldvoice");
        try { Files.createDirectories(dir); } catch (IOException ignored) {}
        String day = new SimpleDateFormat("yyyyMMdd").format(new Date());
        return dir.resolve("skill-history-" + day + ".jsonl");
    }

    public static void append(String jsonLine) {
        Path file = ensureLogPath();
        try {
            Files.writeString(file, jsonLine + System.lineSeparator(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            LOG.warn("Failed to write JSONL log: {}", e.toString());
        }
    }

    public static String iso8601(long epochMs) {
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
        df.setTimeZone(TimeZone.getTimeZone("UTC"));
        return df.format(new Date(epochMs));
    }
}

