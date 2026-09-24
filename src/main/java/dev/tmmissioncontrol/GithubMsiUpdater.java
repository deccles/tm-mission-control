package dev.tmmissioncontrol;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Dialog;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalTime;
import java.util.Base64;
import java.util.Locale;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Checks GitHub Releases for a newer MSI, same flow as RockHound's updater.
 * Dialogs appear only when an installable newer version exists.
 */
public final class GithubMsiUpdater {
    private static final String OWNER = "deccles";
    private static final String REPO = "tm-mission-control";
    private static final String MAVEN_GROUP_ID = "dev.tmmissioncontrol";
    private static final String MAVEN_ARTIFACT_ID = "tm-mission-control";
    /** Must match {@code jpackage --name}. */
    private static final String INSTALL_DIR_NAME = "TMMissionControl";
    private static final String EXE_NAME = "TMMissionControl.exe";
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(20);

    private static final AtomicReference<String> AVAILABLE = new AtomicReference<>();
    private static volatile boolean started;

    private GithubMsiUpdater() {
    }

    /** Version string for the page header, or null when nothing newer is known. */
    public static String availableVersion() {
        return AVAILABLE.get();
    }

    /** Silent startup check, then a quiet recheck at minutes 0, 20, and 40. */
    public static void start() {
        if (started) {
            return;
        }
        started = true;
        Thread startup = new Thread(GithubMsiUpdater::checkOnStartup, "update-check");
        startup.setDaemon(true);
        startup.start();

        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "update-check-periodic");
            thread.setDaemon(true);
            return thread;
        });
        int[] lastMinute = {-1};
        scheduler.scheduleAtFixedRate(() -> {
            int minute = LocalTime.now().getMinute();
            if (minute % 20 != 0 || minute == lastMinute[0]) {
                return;
            }
            lastMinute[0] = minute;
            rememberIfNewer();
        }, 60, 60, TimeUnit.SECONDS);
    }

    private static void checkOnStartup() {
        Release release;
        try {
            release = lookUp();
        } catch (Exception ignored) {
            return;
        }
        if (release == null) {
            return;
        }
        AVAILABLE.set(release.latestVersion);
        SwingUtilities.invokeLater(() -> promptAndInstall(release));
    }

    private static void rememberIfNewer() {
        try {
            Release release = lookUp();
            AVAILABLE.set(release == null ? null : release.latestVersion);
        } catch (Exception ignored) {
            // Quiet check. Leave the previous hint in place.
        }
    }

    /** Null when current, unknown (dev run), offline-equivalent, or the release has no MSI. */
    private static Release lookUp() throws Exception {
        String current = readCurrentVersion();
        if (current == null || current.isBlank() || "(unknown)".equals(current)) {
            return null;
        }
        JsonObject latest = fetchLatestReleaseJson();
        String latestVersion = readVersionFromTag(latest);
        if (latestVersion == null || latestVersion.isBlank()) {
            return null;
        }
        if (compareVersions(latestVersion, current) <= 0) {
            return null;
        }
        JsonObject msi = findMsiAsset(latest);
        if (msi == null) {
            return null;
        }
        String name = getString(msi, "name");
        String url = getString(msi, "browser_download_url");
        if (url == null || url.isBlank()) {
            return null;
        }
        return new Release(current, latestVersion, name, url);
    }

    private static void promptAndInstall(Release release) {
        int choice = JOptionPane.showConfirmDialog(null,
                "Update available.\n\n"
                        + "Current: " + release.currentVersion + "\n"
                        + "Latest: " + release.latestVersion + "\n\n"
                        + "Download and run installer now?\n\n"
                        + "MSI: " + (release.msiName == null ? "(unknown)" : release.msiName) + "\n\n"
                        + "Windows will ask for admin permission to install.",
                "Update Available",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE);
        if (choice != JOptionPane.YES_OPTION) {
            return;
        }
        downloadAndInstall(release.msiUrl, release.msiName);
    }

    private static void downloadAndInstall(String msiUrl, String msiName) {
        JDialog dialog = new JDialog((java.awt.Frame) null, "Downloading Update", Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        JLabel label = new JLabel("Downloading " + (msiName != null ? msiName : "installer") + "...");
        JProgressBar bar = new JProgressBar();
        bar.setIndeterminate(true);
        dialog.getContentPane().setLayout(new BorderLayout(10, 10));
        dialog.getContentPane().add(label, BorderLayout.NORTH);
        dialog.getContentPane().add(bar, BorderLayout.CENTER);
        dialog.setSize(460, 120);
        dialog.setLocationRelativeTo(null);

        Thread worker = new Thread(() -> {
            Path downloaded;
            try {
                downloaded = downloadMsi(msiUrl, msiName, bar, label);
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    dialog.dispose();
                    JOptionPane.showMessageDialog(null,
                            "Unable to download update:\n" + safeMessage(ex),
                            "Download Failed",
                            JOptionPane.ERROR_MESSAGE);
                });
                return;
            }
            SwingUtilities.invokeLater(() -> dialog.dispose());
            if (downloaded == null || !Files.isRegularFile(downloaded)) {
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(null,
                        "Download did not produce a valid MSI file.",
                        "Download Failed",
                        JOptionPane.ERROR_MESSAGE));
                return;
            }
            try {
                launchInstaller(downloaded);
                System.exit(0);
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(null,
                        "Downloaded installer, but couldn't start the updater:\n" + safeMessage(ex) + "\n\n"
                                + "MSI:\n" + downloaded.toAbsolutePath(),
                        "Update Launch Failed",
                        JOptionPane.ERROR_MESSAGE));
            }
        }, "update-download");
        worker.setDaemon(true);
        worker.start();
        dialog.setVisible(true);
    }

    private static void launchInstaller(Path msi) throws IOException {
        String programFiles = System.getenv("ProgramFiles");
        if (programFiles == null || programFiles.isBlank()) {
            programFiles = "C:\\Program Files";
        }
        Path exe = Path.of(programFiles, INSTALL_DIR_NAME, EXE_NAME);
        String inner =
                "$msi = \"" + escapeForPowershell(msi.toAbsolutePath().toString()) + "\"; " +
                "$exe = \"" + escapeForPowershell(exe.toAbsolutePath().toString()) + "\"; " +
                "$args = \"/i `\"$msi`\" /passive /norestart\"; " +
                "$p = Start-Process -FilePath \"msiexec.exe\" -ArgumentList $args -Wait -PassThru; " +
                "Start-Sleep -Seconds 1; " +
                "Start-Process -FilePath $exe; ";
        String innerEncoded = toPowershellEncodedCommand(inner);
        String outer =
                "Start-Process -FilePath 'powershell.exe' -Verb RunAs -WindowStyle Hidden -ArgumentList @(" +
                "'-NoProfile'," +
                "'-EncodedCommand'," +
                "'" + innerEncoded + "'" +
                ")";
        new ProcessBuilder(
                "powershell.exe",
                "-NoProfile",
                "-EncodedCommand",
                toPowershellEncodedCommand(outer))
                .start();
        try {
            Thread.sleep(1500);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    private static Path downloadMsi(String url, String msiName, JProgressBar bar, JLabel label) throws Exception {
        Path updaterDir = updaterDir();
        Files.createDirectories(updaterDir);
        String safeName = toSafeFilename(msiName);
        if (safeName == null || safeName.isBlank()) {
            safeName = "TMMissionControl-Update.msi";
        }
        if (!safeName.toLowerCase(Locale.ROOT).endsWith(".msi")) {
            safeName = safeName + ".msi";
        }
        Path outFile = updaterDir.resolve(safeName);
        if (Files.exists(outFile)) {
            outFile = updaterDir.resolve(stripExtension(safeName) + "-" + System.currentTimeMillis() + ".msi");
        }

        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .connectTimeout(HTTP_TIMEOUT)
                .build();
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMinutes(10))
                .header("User-Agent", "TM-Mission-Control-Updater")
                .GET()
                .build();
        HttpResponse<InputStream> resp = client.send(req, HttpResponse.BodyHandlers.ofInputStream());
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            throw new IOException("HTTP " + resp.statusCode() + " downloading MSI");
        }
        long len = resp.headers().firstValueAsLong("Content-Length").orElse(-1L);
        if (len > 0) {
            SwingUtilities.invokeLater(() -> {
                bar.setIndeterminate(false);
                bar.setMinimum(0);
                bar.setMaximum(1000);
            });
        }
        try (InputStream in = resp.body(); var out = Files.newOutputStream(outFile)) {
            byte[] buf = new byte[128 * 1024];
            long readTotal = 0;
            int r;
            while ((r = in.read(buf)) >= 0) {
                if (r == 0) {
                    continue;
                }
                out.write(buf, 0, r);
                readTotal += r;
                if (len > 0) {
                    long rt = readTotal;
                    SwingUtilities.invokeLater(() -> {
                        int v = (int) Math.min(1000L, (rt * 1000L) / len);
                        bar.setValue(v);
                        label.setText("Downloading... " + (v / 10) + "%");
                    });
                }
            }
        }
        return outFile;
    }

    private static Path updaterDir() {
        String localAppData = System.getenv("LOCALAPPDATA");
        if (localAppData == null || localAppData.isBlank()) {
            localAppData = System.getProperty("user.home");
        }
        return Path.of(localAppData, INSTALL_DIR_NAME, "updater");
    }

    private static JsonObject fetchLatestReleaseJson() throws Exception {
        String api = "https://api.github.com/repos/" + OWNER + "/" + REPO + "/releases/latest";
        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .connectTimeout(HTTP_TIMEOUT)
                .build();
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(api))
                .timeout(HTTP_TIMEOUT)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "TM-Mission-Control-Updater")
                .GET()
                .build();
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            throw new IOException("HTTP " + resp.statusCode() + " calling GitHub releases/latest");
        }
        return JsonParser.parseString(resp.body()).getAsJsonObject();
    }

    private static JsonObject findMsiAsset(JsonObject release) {
        JsonElement assetsEl = release.get("assets");
        if (assetsEl == null || !assetsEl.isJsonArray()) {
            return null;
        }
        JsonArray assets = assetsEl.getAsJsonArray();
        for (JsonElement el : assets) {
            if (!el.isJsonObject()) {
                continue;
            }
            JsonObject asset = el.getAsJsonObject();
            String name = getString(asset, "name");
            if (name != null && name.toLowerCase(Locale.ROOT).endsWith(".msi")) {
                return asset;
            }
        }
        return null;
    }

    private static String readVersionFromTag(JsonObject release) {
        String tag = getString(release, "tag_name");
        if (tag == null) {
            return null;
        }
        tag = tag.trim();
        if (tag.startsWith("v") || tag.startsWith("V")) {
            tag = tag.substring(1);
        }
        return tag;
    }

    private static String readCurrentVersion() {
        try {
            String version = GithubMsiUpdater.class.getPackage().getImplementationVersion();
            if (version != null && !version.isBlank()) {
                return version.trim();
            }
        } catch (Exception ignored) {
        }
        String pomPropsPath = "/META-INF/maven/" + MAVEN_GROUP_ID + "/" + MAVEN_ARTIFACT_ID + "/pom.properties";
        try (InputStream in = GithubMsiUpdater.class.getResourceAsStream(pomPropsPath)) {
            if (in == null) {
                return "(unknown)";
            }
            Properties props = new Properties();
            props.load(in);
            String version = props.getProperty("version");
            return (version != null && !version.isBlank()) ? version.trim() : "(unknown)";
        } catch (Exception ignored) {
            return "(unknown)";
        }
    }

    private static String getString(JsonObject obj, String key) {
        JsonElement el = obj.get(key);
        if (el == null || el.isJsonNull()) {
            return null;
        }
        try {
            return el.getAsString();
        } catch (Exception ex) {
            return null;
        }
    }

    private static int compareVersions(String a, String b) {
        String[] ap = a.trim().split("[^0-9]+");
        String[] bp = b.trim().split("[^0-9]+");
        int n = Math.max(ap.length, bp.length);
        for (int i = 0; i < n; i++) {
            int ai = (i < ap.length && !ap[i].isBlank()) ? safeInt(ap[i]) : 0;
            int bi = (i < bp.length && !bp[i].isBlank()) ? safeInt(bp[i]) : 0;
            if (ai != bi) {
                return Integer.compare(ai, bi);
            }
        }
        return 0;
    }

    private static int safeInt(String s) {
        try {
            return Integer.parseInt(s);
        } catch (Exception ex) {
            return 0;
        }
    }

    private static String safeMessage(Throwable t) {
        String message = t.getMessage();
        if (message == null || message.isBlank()) {
            return t.getClass().getSimpleName();
        }
        return message;
    }

    private static String toPowershellEncodedCommand(String command) {
        return Base64.getEncoder().encodeToString(command.getBytes(StandardCharsets.UTF_16LE));
    }

    private static String escapeForPowershell(String s) {
        return s.replace("'", "''");
    }

    private static String toSafeFilename(String name) {
        if (name == null) {
            return null;
        }
        return name.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
    }

    private static String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private record Release(String currentVersion, String latestVersion, String msiName, String msiUrl) {
    }
}
