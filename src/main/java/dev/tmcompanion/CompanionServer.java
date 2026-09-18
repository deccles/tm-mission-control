package dev.tmcompanion;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsParameters;
import com.sun.net.httpserver.HttpsServer;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceInfo;

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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Executors;

import javax.net.ssl.SSLParameters;

public final class CompanionServer {
    private final GameState state;
    private final Gson gson = new GsonBuilder().serializeNulls().create();
    private final int port;
    private final String forcedHost;
    private HttpServer server;
    private HttpServer lanHttp;
    private final List<HttpsServer> httpsServers = new ArrayList<>();
    private final List<JmDNS> mdns = new ArrayList<>();
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
        rememberJavaExe();
        boolean firewallOpen = FirewallSetup.ensure();
        List<String> ips = lanAddresses();
        List<String> wifi = wifiAddresses();
        List<String> certHosts = LanNames.certHosts(ips);
        boolean https443 = bindHttps(443, certHosts);
        bindHttps(8080, certHosts);
        int phonePort = https443 ? 443 : 8080;
        List<String> urls = new ArrayList<>();
        for (String host : LanNames.phoneHosts(forcedHost)) {
            addUrl(urls, httpsUrl(host, phonePort));
            if (phonePort != 443 && https443) {
                addUrl(urls, httpsUrl(host, 443));
            }
        }
        addUrl(urls, httpUrl("127.0.0.1", port));
        state.url = urls.get(0);
        state.urls = List.copyOf(urls);
        allowInboundNamed("TM Companion HTTPS", 443);
        allowInboundNamed("TM Companion 8080", 8080);
        allowInboundUdp("TM Companion mDNS", 5353);
        allowJavaProgram();
        boolean desktopRule = allowInbound(port);
        state.firewallOpen = firewallOpen || FirewallSetup.javaRulePresent() || desktopRule;
        qrSvg = QrCodes.svg(state.url).getBytes(StandardCharsets.UTF_8);
        server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);
        mount(server);
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        advertiseMdns(phonePort, wifi.isEmpty() ? ips : wifi);
        return URI.create("http://127.0.0.1:" + port + "/");
    }

    private void mount(HttpServer http) {
        http.createContext("/api/state", this::state);
        http.createContext("/qr.svg", this::qr);
        http.createContext("/", this::staticFile);
    }

    private static String httpsUrl(String host, int phonePort) {
        return phonePort == 443 ? "https://" + host + "/" : "https://" + host + ":" + phonePort + "/";
    }

    private static String httpUrl(String host, int phonePort) {
        return phonePort == 80 ? "http://" + host + "/" : "http://" + host + ":" + phonePort + "/";
    }

    private static void addUrl(List<String> urls, String url) {
        if (!urls.contains(url)) {
            urls.add(url);
        }
    }

    private boolean bindHttps(int httpsPort, List<String> certHosts) {
        try {
            HttpsServer https = HttpsServer.create(new InetSocketAddress("0.0.0.0", httpsPort), 0);
            https.setHttpsConfigurator(new HttpsConfigurator(LocalCert.sslContext(certHosts)) {
                @Override
                public void configure(HttpsParameters params) {
                    SSLParameters sp = getSSLContext().getDefaultSSLParameters();
                    sp.setProtocols(new String[] {"TLSv1.3", "TLSv1.2"});
                    params.setSSLParameters(sp);
                }
            });
            mount(https);
            https.setExecutor(Executors.newCachedThreadPool());
            https.start();
            httpsServers.add(https);
            return true;
        } catch (Exception ex) {
            System.err.println("HTTPS :" + httpsPort + " not bound: " + ex.getMessage());
            return false;
        }
    }

    private void advertiseMdns(int phonePort, List<String> ips) {
        for (String ip : ips) {
            if (ip.startsWith("192.168.137.")) {
                continue;
            }
            try {
                JmDNS dns = JmDNS.create(InetAddress.getByName(ip), LanNames.SHORT);
                dns.registerService(ServiceInfo.create("_https._tcp.local.", "TM Companion", phonePort, "path=/"));
                dns.registerService(ServiceInfo.create("_http._tcp.local.", "TM Companion", phonePort, "path=/"));
                mdns.add(dns);
            } catch (Exception ex) {
                System.err.println("mDNS not advertised on " + ip + ": " + ex.getMessage());
            }
        }
    }

    public void stop() {
        for (JmDNS dns : mdns) {
            try {
                dns.unregisterAllServices();
                dns.close();
            } catch (Exception ignored) {
            }
        }
        mdns.clear();
        for (HttpsServer https : httpsServers) {
            https.stop(0);
        }
        httpsServers.clear();
        if (lanHttp != null) {
            lanHttp.stop(0);
        }
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
        List<String> wifi = new ArrayList<>();
        List<String> wired = new ArrayList<>();
        collectLanAddresses(wifi, wired);
        List<String> out = new ArrayList<>(wifi);
        out.addAll(wired);
        return out;
    }

    static List<String> wifiAddresses() {
        List<String> wifi = new ArrayList<>();
        collectLanAddresses(wifi, new ArrayList<>());
        return wifi;
    }

    private static void collectLanAddresses(List<String> wifi, List<String> wired) {
        Set<String> seen = new LinkedHashSet<>();
        try {
            for (NetworkInterface nic : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!nic.isUp() || nic.isLoopback() || nic.isVirtual() || skipNic(nic)) {
                    continue;
                }
                boolean wireless = isWifi(nic);
                for (InetAddress addr : Collections.list(nic.getInetAddresses())) {
                    if (addr instanceof Inet4Address v4 && v4.isSiteLocalAddress()) {
                        String ip = v4.getHostAddress();
                        if (ip.startsWith("192.168.137.") || !seen.add(ip)) {
                            continue;
                        }
                        (wireless ? wifi : wired).add(ip);
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    private static boolean isWifi(NetworkInterface nic) {
        String name = (nic.getDisplayName() + " " + nic.getName()).toLowerCase(Locale.ROOT);
        return name.contains("wi-fi")
                || name.contains("wifi")
                || name.contains("wlan")
                || name.contains("wireless");
    }

    static void stopHotspot() {
        try {
            Process proc = new ProcessBuilder(
                    "powershell",
                    "-NoProfile",
                    "-Command",
                    "$p=[Windows.Networking.Connectivity.NetworkInformation,Windows.Networking.Connectivity,ContentType=WindowsRuntime]::GetInternetConnectionProfile();"
                            + "$m=[Windows.Networking.NetworkOperators.NetworkOperatorTetheringManager,Windows.Networking.NetworkOperators,ContentType=WindowsRuntime]::CreateFromConnectionProfile($p);"
                            + "if($m.TetheringOperationalState.ToString() -eq 'On'){$null=$m.StopTetheringAsync()}")
                    .redirectErrorStream(true)
                    .start();
            proc.waitFor();
        } catch (Exception ignored) {
        }
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

    private static void rememberJavaExe() {
        FirewallSetup.rememberJavaExe();
    }

    private static boolean allowJavaProgram() {
        Path java = FirewallSetup.javaExe();
        boolean tcp = allowProgram("TM Companion Java", java, "TCP");
        allowProgram("TM Companion Java UDP", java, "UDP");
        return tcp;
    }

    static boolean allowInbound(int port) {
        return allowInboundNamed("TM Companion", port);
    }

    static boolean allowInboundNamed(String name, int port) {
        return allowInboundNamed(name, "TCP", port);
    }

    static boolean allowInboundUdp(String name, int port) {
        return allowInboundNamed(name, "UDP", port);
    }

    private static boolean allowProgram(String name, Path program, String protocol) {
        if (firewallRulePresent(name)) {
            return true;
        }
        try {
            Process add = new ProcessBuilder(
                    "netsh", "advfirewall", "firewall", "add", "rule",
                    "name=" + name,
                    "dir=in",
                    "action=allow",
                    "program=" + program.toString(),
                    "protocol=" + protocol,
                    "profile=any",
                    "enable=yes")
                    .redirectErrorStream(true)
                    .start();
            add.waitFor();
        } catch (Exception ignored) {
        }
        return firewallRulePresent(name);
    }

    private static boolean allowInboundNamed(String name, String protocol, int port) {
        if (firewallRulePresent(name)) {
            return true;
        }
        try {
            Process add = new ProcessBuilder(
                    "netsh", "advfirewall", "firewall", "add", "rule",
                    "name=" + name,
                    "dir=in",
                    "action=allow",
                    "protocol=" + protocol,
                    "localport=" + Integer.toString(port),
                    "profile=any",
                    "enable=yes")
                    .redirectErrorStream(true)
                    .start();
            add.waitFor();
        } catch (Exception ignored) {
        }
        return firewallRulePresent(name);
    }

    private static boolean firewallRulePresent(String name) {
        try {
            Process show = new ProcessBuilder(
                    "netsh", "advfirewall", "firewall", "show", "rule",
                    "name=" + name)
                    .redirectErrorStream(true)
                    .start();
            String output = new String(show.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            show.waitFor();
            return output.contains(name) && !output.toLowerCase(Locale.ROOT).contains("no rules match");
        } catch (Exception ignored) {
            return false;
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
