package dev.tmmissioncontrol;

import java.awt.Desktop;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

public final class App {
    public static void main(String[] args) throws Exception {
        Path logFile = defaultLog();
        int port = 8765;
        boolean once = false;
        String host = null;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--log" -> logFile = Path.of(args[++i]);
                case "--port" -> port = Integer.parseInt(args[++i]);
                case "--host" -> host = args[++i];
                case "--once" -> once = true;
                case "--help" -> {
                    System.out.println("Usage: tm-mission-control [--log path] [--port 8765] [--host 192.168.x.x] [--once]");
                    return;
                }
                default -> {
                    System.err.println("Unknown argument: " + args[i]);
                    return;
                }
            }
        }

        CardDatabase cards = CardDatabase.load();
        GameState state = new GameState();
        LogParser parser = new LogParser(cards, state);

        if (!Files.exists(logFile)) {
            System.err.println("Player.log not found at " + logFile);
            System.err.println("Play a local match, or pass --log with the Unity log path.");
        }

        if (once) {
            if (Files.exists(logFile)) {
                parser.replay(logFile);
            }
            System.out.println(new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(state.snapshot()));
            return;
        }

        MissionControlServer.takeOver(port);

        LogTailer tailer = new LogTailer(logFile, parser);
        Thread thread = new Thread(tailer, "player-log-tailer");
        thread.setDaemon(true);
        thread.start();

        MissionControlServer.stopHotspot();
        MissionControlServer server = new MissionControlServer(state, port, host);
        URI uri = server.start();
        System.out.println("TM Mission Control");
        System.out.println("  log  " + logFile);
        System.out.println("  open " + uri);
        System.out.println("  phone " + state.url);
        if (!state.firewallOpen) {
            System.out.println("  phone blocked — approve the Windows Firewall prompt on next start");
        }
        for (String extra : state.urls) {
            if (extra != null && !extra.equals(state.url)) {
                System.out.println("  also  " + extra);
            }
        }
        System.out.println("Leave this running on a second monitor while you play.");
        System.out.println("Phone: same Wi-Fi as usual. Open " + state.url
                + " — if Chrome warns about the certificate, tap Advanced and proceed once.");

        if (Desktop.isDesktopSupported()) {
            try {
                Desktop.getDesktop().browse(uri);
            } catch (Exception ignored) {
            }
        }

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            tailer.stop();
            server.stop();
        }));
        thread.join();
    }

    static Path defaultLog() {
        String home = System.getProperty("user.home");
        return Path.of(home, "AppData", "LocalLow", "LuckyHammers", "Terraforming Mars", "Player.log");
    }
}
