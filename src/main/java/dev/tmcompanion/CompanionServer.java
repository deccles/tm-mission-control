package dev.tmcompanion;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Executors;

public final class CompanionServer {
    private final GameState state;
    private final Gson gson = new GsonBuilder().serializeNulls().create();
    private final int port;
    private final String forcedHost;
    private HttpServer server;
    private byte[] qrSvg = new byte[0];

    public CompanionServer(GameState state, int port) {
        this(state, port, null);
    }

    public CompanionServer(GameState state, int port, String forcedHost) {
        this.state = state;
        this.port = port;
        this.forcedHost = forcedHost;
    }

    public URI start() throws IOException {
        String host = (forcedHost != null && !forcedHost.isBlank()) ? forcedHost : lanHost();
        String advertised = "http://" + host + ":" + port + "/";
        state.url = advertised;
        qrSvg = QrCodes.svg(advertised).getBytes(StandardCharsets.UTF_8);
        server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);
        server.createContext("/api/state", this::state);
        server.createContext("/qr.svg", this::qr);
        server.createContext("/", this::staticFile);
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        allowInbound(port);
        return URI.create("http://127.0.0.1:" + port + "/");
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    private void state(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            return;
        }
        byte[] body = gson.toJson(state.snapshot()).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().add("Cache-Control", "no-store");
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    private void qr(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().add("Content-Type", "image/svg+xml; charset=utf-8");
        exchange.getResponseHeaders().add("Cache-Control", "no-store");
        exchange.sendResponseHeaders(200, qrSvg.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(qrSvg);
        }
    }

    private void staticFile(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        if (path.equals("/")) {
            path = "/index.html";
        }
        String resource = "/web" + path;
        try (InputStream in = CompanionServer.class.getResourceAsStream(resource)) {
            if (in == null) {
                exchange.sendResponseHeaders(404, -1);
                return;
            }
            byte[] body = in.readAllBytes();
            exchange.getResponseHeaders().add("Content-Type", contentType(path));
            exchange.getResponseHeaders().add("Cache-Control", "no-store");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        }
    }

    static String lanHost() {
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.connect(InetAddress.getByName("1.1.1.1"), 80);
            InetAddress local = socket.getLocalAddress();
            if (local instanceof Inet4Address && !local.isLoopbackAddress() && !local.isAnyLocalAddress()) {
                return local.getHostAddress();
            }
        } catch (Exception ignored) {
        }
        List<String> candidates = lanAddresses();
        return candidates.isEmpty() ? "127.0.0.1" : candidates.get(0);
    }

    static List<String> lanAddresses() {
        Set<String> out = new LinkedHashSet<>();
        try {
            for (NetworkInterface nic : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!nic.isUp() || nic.isLoopback() || nic.isVirtual() || skipNic(nic)) {
                    continue;
                }
                for (InetAddress addr : Collections.list(nic.getInetAddresses())) {
                    if (addr instanceof Inet4Address v4 && v4.isSiteLocalAddress()) {
                        out.add(v4.getHostAddress());
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return new ArrayList<>(out);
    }

    private static boolean skipNic(NetworkInterface nic) {
        String name = (nic.getDisplayName() + " " + nic.getName()).toLowerCase(Locale.ROOT);
        return name.contains("bluetooth")
                || name.contains("virtual")
                || name.contains("vethernet")
                || name.contains("hyper-v")
                || name.contains("wsl")
                || name.contains("loopback")
                || name.contains("local area connection");
    }

    static void allowInbound(int port) {
        try {
            new ProcessBuilder(
                    "netsh", "advfirewall", "firewall", "delete", "rule",
                    "name=TM Companion")
                    .redirectErrorStream(true)
                    .start()
                    .waitFor();
            new ProcessBuilder(
                    "netsh", "advfirewall", "firewall", "add", "rule",
                    "name=TM Companion",
                    "dir=in",
                    "action=allow",
                    "protocol=TCP",
                    "localport=" + port,
                    "profile=any")
                    .redirectErrorStream(true)
                    .start()
                    .waitFor();
        } catch (Exception ignored) {
        }
    }

    private static String contentType(String path) {
        if (path.endsWith(".css")) {
            return "text/css; charset=utf-8";
        }
        if (path.endsWith(".js")) {
            return "text/javascript; charset=utf-8";
        }
        if (path.endsWith(".svg")) {
            return "image/svg+xml";
        }
        return "text/html; charset=utf-8";
    }
}
