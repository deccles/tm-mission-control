package dev.tmmissioncontrol;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class LogTailer implements Runnable {
    private final Path logFile;
    private final LogParser parser;
    private volatile boolean running = true;

    public LogTailer(Path logFile, LogParser parser) {
        this.logFile = logFile;
        this.parser = parser;
    }

    public void stop() {
        running = false;
    }

    @Override
    public void run() {
        long position = 0;
        try {
            if (Files.exists(logFile)) {
                parser.replay(logFile);
                position = Files.size(logFile);
            }
        } catch (Exception e) {
            System.err.println("Initial parse failed: " + e.getMessage());
        }
        while (running) {
            try {
                if (!Files.exists(logFile)) {
                    Thread.sleep(500);
                    continue;
                }
                long size = Files.size(logFile);
                if (size < position) {
                    parser.replay(logFile);
                    position = Files.size(logFile);
                }
                if (size > position) {
                    try (var in = Files.newInputStream(logFile);
                         var reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                        in.skipNBytes(position);
                        String line;
                        while ((line = reader.readLine()) != null) {
                            parser.consume(line);
                        }
                    }
                    position = Files.size(logFile);
                }
                Thread.sleep(250);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                System.err.println("Tail error: " + e.getMessage());
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }
}
