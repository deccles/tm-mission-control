package dev.tmmissioncontrol;

import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

final class LanNames {
    static final String SHORT = "tmmissioncontrol";

    private LanNames() {
    }

    static String computerName() {
        try {
            String host = InetAddress.getLocalHost().getHostName();
            int dot = host.indexOf('.');
            return dot > 0 ? host.substring(0, dot) : host;
        } catch (Exception ignored) {
            return "";
        }
    }

    static String dnsSuffix() {
        try {
            Process proc = new ProcessBuilder("ipconfig", "/all").redirectErrorStream(true).start();
            String out = new String(proc.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            proc.waitFor();
            String wifiSuffix = "";
            String otherSuffix = "";
            String section = "";
            for (String line : out.split("\\R")) {
                String trim = line.trim();
                if (trim.endsWith(":") && (trim.contains("adapter") || trim.startsWith("Ethernet adapter")
                        || trim.startsWith("Wireless LAN adapter"))) {
                    section = trim.toLowerCase(Locale.ROOT);
                    continue;
                }
                if (trim.startsWith("Connection-specific DNS Suffix")) {
                    int colon = trim.lastIndexOf(':');
                    String suffix = colon >= 0 ? trim.substring(colon + 1).trim() : "";
                    if (suffix.isBlank()) {
                        continue;
                    }
                    if (section.contains("wi-fi") && !section.contains("virtual") && !section.contains("local area")) {
                        wifiSuffix = suffix;
                    } else if (otherSuffix.isBlank() && !section.contains("bluetooth") && !section.contains("loopback")) {
                        otherSuffix = suffix;
                    }
                }
            }
            return !wifiSuffix.isBlank() ? wifiSuffix : otherSuffix;
        } catch (Exception ignored) {
        }
        return "";
    }

    static String fqdn(String host, String suffix) {
        if (host == null || host.isBlank()) {
            return "";
        }
        if (suffix == null || suffix.isBlank()) {
            return "";
        }
        return host + "." + suffix;
    }

    static String defaultGateway() {
        try {
            Process proc = new ProcessBuilder("ipconfig").redirectErrorStream(true).start();
            String out = new String(proc.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            proc.waitFor();
            String wifiGw = "";
            String otherGw = "";
            String section = "";
            boolean inGateway = false;
            for (String line : out.split("\\R")) {
                String trim = line.trim();
                if (trim.endsWith(":") && !trim.contains("IPv") && !trim.startsWith("Autoconfiguration")) {
                    if (trim.contains("adapter") || trim.startsWith("Ethernet") || trim.startsWith("Wireless")) {
                        section = trim.toLowerCase(Locale.ROOT);
                        inGateway = false;
                    }
                    continue;
                }
                String candidate = "";
                if (trim.startsWith("Default Gateway")) {
                    inGateway = true;
                    int colon = trim.lastIndexOf(':');
                    candidate = colon >= 0 ? trim.substring(colon + 1).trim() : "";
                } else if (inGateway && trim.matches("\\d{1,3}(\\.\\d{1,3}){3}")) {
                    candidate = trim;
                } else if (inGateway && trim.contains(":") && !trim.matches("\\d{1,3}(\\.\\d{1,3}){3}")) {
                    inGateway = false;
                }
                if (candidate.isBlank() || candidate.startsWith("192.168.137.") || !candidate.matches("\\d{1,3}(\\.\\d{1,3}){3}")) {
                    continue;
                }
                if (section.contains("wi-fi") && !section.contains("virtual") && !section.contains("local area")) {
                    wifiGw = candidate;
                } else if (otherGw.isBlank()) {
                    otherGw = candidate;
                }
                inGateway = false;
            }
            return !wifiGw.isBlank() ? wifiGw : otherGw;
        } catch (Exception ignored) {
        }
        return "";
    }

    static boolean gatewayResolves(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        String gateway = defaultGateway();
        if (gateway.isBlank()) {
            return false;
        }
        try {
            Process proc = new ProcessBuilder("nslookup", name, gateway).redirectErrorStream(true).start();
            String out = new String(proc.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            proc.waitFor();
            String lower = out.toLowerCase(Locale.ROOT);
            if (lower.contains("non-existent") || lower.contains("can't find") || lower.contains("server failed")) {
                return false;
            }
            return lower.contains("name:");
        } catch (Exception ignored) {
            return false;
        }
    }

    static List<String> certHosts(List<String> ips) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        String pc = computerName();
        String suffix = dnsSuffix();
        out.add(SHORT + ".local");
        out.add(SHORT);
        if (!pc.isBlank()) {
            out.add(pc);
            out.add(pc + ".local");
        }
        String tmFqdn = fqdn(SHORT, suffix);
        String pcFqdn = fqdn(pc, suffix);
        if (!tmFqdn.isBlank()) {
            out.add(tmFqdn);
        }
        if (!pcFqdn.isBlank()) {
            out.add(pcFqdn);
        }
        out.addAll(ips);
        return new ArrayList<>(out);
    }

    static String phoneHost(String forcedHost) {
        if (forcedHost != null && !forcedHost.isBlank()) {
            return forcedHost.trim();
        }
        String pc = computerName();
        String suffix = dnsSuffix();
        String tmFqdn = fqdn(SHORT, suffix);
        if (!tmFqdn.isBlank() && gatewayResolves(tmFqdn)) {
            return tmFqdn;
        }
        if (!pc.isBlank() && gatewayResolves(pc)) {
            return pc;
        }
        String pcFqdn = fqdn(pc, suffix);
        if (!pcFqdn.isBlank() && gatewayResolves(pcFqdn)) {
            return pcFqdn;
        }
        if (!pcFqdn.isBlank()) {
            return pcFqdn;
        }
        if (!tmFqdn.isBlank()) {
            return tmFqdn;
        }
        return pc.isBlank() ? SHORT : pc;
    }

    static List<String> phoneHosts(String forcedHost) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        out.add(phoneHost(forcedHost));
        String pc = computerName();
        String suffix = dnsSuffix();
        String tmFqdn = fqdn(SHORT, suffix);
        String pcFqdn = fqdn(pc, suffix);
        if (!tmFqdn.isBlank() && gatewayResolves(tmFqdn)) {
            out.add(tmFqdn);
        }
        if (!pc.isBlank()) {
            out.add(pc);
        }
        if (!pcFqdn.isBlank()) {
            out.add(pcFqdn);
        }
        return new ArrayList<>(out);
    }
}
