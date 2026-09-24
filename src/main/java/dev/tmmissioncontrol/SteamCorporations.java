package dev.tmmissioncontrol;

import java.util.Map;

/** Steam ``ECorporationType`` values. Corporation choice is public table info. */
public final class SteamCorporations {
    private static final Map<Integer, String> NAMES = Map.ofEntries(
            Map.entry(1, "Beginner"),
            Map.entry(2, "Inventrix"),
            Map.entry(3, "Phobolog"),
            Map.entry(4, "Helion"),
            Map.entry(5, "Teractor"),
            Map.entry(6, "Saturn Systems"),
            Map.entry(7, "United Nations Mars Initiative"),
            Map.entry(8, "Interplanetary Cinematics"),
            Map.entry(9, "Credicor"),
            Map.entry(10, "Thorgate"),
            Map.entry(11, "Mining Guild"),
            Map.entry(12, "Ecoline"),
            Map.entry(13, "Tharsis Republic"),
            Map.entry(14, "Cheung Shing MARS"),
            Map.entry(15, "Point Luna"),
            Map.entry(16, "Robinson Industries"),
            Map.entry(17, "Valley Trust"),
            Map.entry(18, "Vitor"),
            Map.entry(19, "Aphrodite"),
            Map.entry(20, "Manutech"),
            Map.entry(21, "Viron"),
            Map.entry(22, "Morning Star Inc"),
            Map.entry(23, "Celestic"),
            Map.entry(24, "Splice"),
            Map.entry(25, "Recyclon"),
            Map.entry(26, "Arcadian Communities"),
            Map.entry(29, "Pharmacy Union"),
            Map.entry(30, "Pharmacy Union"),
            Map.entry(31, "Astrodrill"),
            Map.entry(40, "Arklight"),
            Map.entry(41, "Aridor"),
            Map.entry(42, "Polyphemos"),
            Map.entry(43, "Poseidon"),
            Map.entry(44, "Stormcraft Incorporated")
    );

    private SteamCorporations() {
    }

    public static String nameFor(int steamId) {
        return NAMES.get(steamId);
    }
}
