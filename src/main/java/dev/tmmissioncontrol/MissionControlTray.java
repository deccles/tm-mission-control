package dev.tmmissioncontrol;

import javax.imageio.ImageIO;
import java.awt.AWTException;
import java.awt.AlphaComposite;
import java.awt.Desktop;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.RenderingHints;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.net.URI;

/** Tray icon so the windowless app can be opened or quit. */
final class MissionControlTray {
    private static TrayIcon icon;

    private MissionControlTray() {
    }

    static void install(URI openUri) {
        if (!SystemTray.isSupported()) {
            return;
        }
        Image image = loadIcon();
        if (image == null) {
            return;
        }
        PopupMenu menu = new PopupMenu();
        MenuItem open = new MenuItem("Open");
        open.addActionListener(e -> browse(openUri));
        MenuItem quit = new MenuItem("Quit");
        quit.addActionListener(e -> quit());
        menu.add(open);
        menu.add(quit);

        TrayIcon trayIcon = new TrayIcon(image, "TM Mission Control", menu);
        trayIcon.setImageAutoSize(true);
        trayIcon.addActionListener(e -> browse(openUri));
        try {
            SystemTray.getSystemTray().add(trayIcon);
            icon = trayIcon;
        } catch (AWTException ignored) {
        }
    }

    static void remove() {
        if (icon != null && SystemTray.isSupported()) {
            SystemTray.getSystemTray().remove(icon);
            icon = null;
        }
    }

    private static void browse(URI uri) {
        if (uri == null || !Desktop.isDesktopSupported()) {
            return;
        }
        try {
            Desktop.getDesktop().browse(uri);
        } catch (Exception ignored) {
        }
    }

    private static void quit() {
        remove();
        System.exit(0);
    }

    private static Image loadIcon() {
        try (InputStream in = MissionControlTray.class.getResourceAsStream("/icon.png")) {
            if (in == null) {
                return null;
            }
            BufferedImage src = ImageIO.read(in);
            if (src == null) {
                return null;
            }
            int size = 64;
            BufferedImage scaled = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = scaled.createGraphics();
            g.setComposite(AlphaComposite.Src);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.drawImage(src, 0, 0, size, size, null);
            g.dispose();
            return scaled;
        } catch (Exception ignored) {
            return null;
        }
    }
}
