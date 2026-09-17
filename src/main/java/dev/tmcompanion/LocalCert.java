package dev.tmcompanion;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

final class LocalCert {
    private static final char[] PASS = "tm-companion".toCharArray();

    private LocalCert() {
    }

    static Path dir() throws java.io.IOException {
        Path dir = Path.of(System.getProperty("user.home"), "AppData", "Local", "TM Companion");
        Files.createDirectories(dir);
        return dir;
    }

    static SSLContext sslContext(List<String> hosts) throws Exception {
        Path dir = dir();
        Path store = dir.resolve("https.p12");
        Path stamp = dir.resolve("https-sans.txt");
        List<String> sans = sanList(hosts);
        String wanted = String.join(",", sans);
        if (!Files.exists(store) || !Files.exists(stamp) || !wanted.equals(Files.readString(stamp).trim())) {
            generate(store, sans);
            Files.writeString(stamp, wanted, StandardCharsets.UTF_8);
        }
        var ks = java.security.KeyStore.getInstance("PKCS12");
        try (var in = Files.newInputStream(store)) {
            ks.load(in, PASS);
        }
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(ks, PASS);
        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(kmf.getKeyManagers(), null, null);
        return ctx;
    }

    private static List<String> sanList(List<String> hosts) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        out.add("localhost");
        out.add("127.0.0.1");
        out.add("tmcompanion.local");
        for (String host : hosts) {
            if (host != null && !host.isBlank()) {
                out.add(host.trim());
            }
        }
        return new ArrayList<>(out);
    }

    private static void generate(Path store, List<String> hosts) throws Exception {
        Files.deleteIfExists(store);
        Path keytool = Path.of(System.getProperty("java.home"), "bin", "keytool.exe");
        if (!Files.isRegularFile(keytool)) {
            keytool = Path.of(System.getProperty("java.home"), "bin", "keytool");
        }
        StringBuilder ext = new StringBuilder("SAN=");
        boolean first = true;
        for (String host : hosts) {
            if (!first) {
                ext.append(',');
            }
            first = false;
            ext.append(ipv4(host) ? "ip:" : "dns:").append(host);
        }
        Process proc = new ProcessBuilder(
                keytool.toString(),
                "-genkeypair",
                "-alias", "tm-companion",
                "-keyalg", "RSA",
                "-keysize", "2048",
                "-sigalg", "SHA256withRSA",
                "-validity", "825",
                "-storetype", "PKCS12",
                "-keystore", store.toString(),
                "-storepass", "tm-companion",
                "-keypass", "tm-companion",
                "-dname", "CN=tmcompanion.local",
                "-ext", ext.toString(),
                "-noprompt")
                .redirectErrorStream(true)
                .start();
        String output = new String(proc.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (proc.waitFor() != 0) {
            throw new IllegalStateException("keytool failed: " + output);
        }
    }

    private static boolean ipv4(String host) {
        return host.matches("\\d{1,3}(\\.\\d{1,3}){3}");
    }

    static String preferPhoneHost(String fallback, List<String> ips) {
        if (ips.contains(fallback)) {
            return fallback;
        }
        return ips.isEmpty() ? fallback : ips.get(0);
    }
}
