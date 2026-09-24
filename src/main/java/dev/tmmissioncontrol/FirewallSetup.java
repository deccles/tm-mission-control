package dev.tmmissioncontrol;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

final class FirewallSetup {
    private FirewallSetup() {
    }

    static Path javaExe() {
        Path java = Path.of(System.getProperty("java.home"), "bin", "java.exe");
        if (!Files.isRegularFile(java)) {
            java = Path.of(System.getProperty("java.home"), "bin", "java");
        }
        return java;
    }

    static void rememberJavaExe() {
        try {
            Files.writeString(LocalCert.dir().resolve("java-exe.txt"), javaExe().toString());
        } catch (Exception ignored) {
        }
    }

    static boolean javaRulePresent() {
        return rulePresent("TM Mission Control Java");
    }

    static boolean ensure() {
        rememberJavaExe();
        if (javaRulePresent()) {
            return true;
        }
        try {
            Path bat = LocalCert.dir().resolve("open-firewall.bat");
            Files.writeString(bat, script(javaExe()));
            Process elevate = new ProcessBuilder(
                    "powershell",
                    "-NoProfile",
                    "-Command",
                    "Start-Process -FilePath '" + bat.toString().replace("'", "''") + "' -Verb RunAs -Wait")
                    .redirectErrorStream(true)
                    .start();
            elevate.waitFor();
        } catch (Exception ignored) {
        }
        return javaRulePresent();
    }

    private static boolean rulePresent(String name) {
        try {
            Process show = new ProcessBuilder(
                    "netsh", "advfirewall", "firewall", "show", "rule",
                    "name=" + name)
                    .redirectErrorStream(true)
                    .start();
            String output = new String(show.getInputStream().readAllBytes());
            show.waitFor();
            return output.contains(name) && !output.toLowerCase(Locale.ROOT).contains("no rules match");
        } catch (Exception ignored) {
            return false;
        }
    }

    private static String script(Path java) {
        String exe = java.toString();
        return """
                @echo off
                netsh advfirewall firewall delete rule name="TM Companion" >nul 2>nul
                netsh advfirewall firewall delete rule name="TM Companion HTTPS" >nul 2>nul
                netsh advfirewall firewall delete rule name="TM Companion HTTPS probe" >nul 2>nul
                netsh advfirewall firewall delete rule name="TM Companion 8080" >nul 2>nul
                netsh advfirewall firewall delete rule name="TM Companion LAN" >nul 2>nul
                netsh advfirewall firewall delete rule name="TM Companion Java" >nul 2>nul
                netsh advfirewall firewall delete rule name="TM Companion Java UDP" >nul 2>nul
                netsh advfirewall firewall delete rule name="TM Companion mDNS" >nul 2>nul
                netsh advfirewall firewall delete rule name="TM Mission Control" >nul 2>nul
                netsh advfirewall firewall delete rule name="TM Mission Control HTTPS" >nul 2>nul
                netsh advfirewall firewall delete rule name="TM Mission Control HTTPS probe" >nul 2>nul
                netsh advfirewall firewall delete rule name="TM Mission Control 8080" >nul 2>nul
                netsh advfirewall firewall delete rule name="TM Mission Control LAN" >nul 2>nul
                netsh advfirewall firewall delete rule name="TM Mission Control Java" >nul 2>nul
                netsh advfirewall firewall delete rule name="TM Mission Control Java UDP" >nul 2>nul
                netsh advfirewall firewall delete rule name="TM Mission Control mDNS" >nul 2>nul
                netsh advfirewall firewall add rule name="TM Mission Control" dir=in action=allow protocol=TCP localport=443,8080,8765 profile=any enable=yes
                netsh advfirewall firewall add rule name="TM Mission Control HTTPS" dir=in action=allow protocol=TCP localport=443 profile=any enable=yes
                netsh advfirewall firewall add rule name="TM Mission Control mDNS" dir=in action=allow protocol=UDP localport=5353 profile=any enable=yes
                netsh advfirewall firewall add rule name="TM Mission Control Java" dir=in action=allow program="%s" protocol=TCP profile=any enable=yes
                netsh advfirewall firewall add rule name="TM Mission Control Java UDP" dir=in action=allow program="%s" protocol=UDP profile=any enable=yes
                """.formatted(exe, exe);
    }
}
