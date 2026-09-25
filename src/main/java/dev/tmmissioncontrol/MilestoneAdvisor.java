package dev.tmmissioncontrol;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Public-table milestone progress for the local player. Hand names are never used;
 * Planner stays unknown because we do not track hidden cards, only public counts we lack.
 */
public final class MilestoneAdvisor {
    private static final int COST = 8;
    private static final int MAX_CLAIMED = 3;

    private MilestoneAdvisor() {
    }

    public static Map<String, Object> snapshot(GameState state, PlayerState you, List<PlayerState> table) {
        Map<String, Object> out = new LinkedHashMap<>();
        List<Map<String, Object>> claimed = new ArrayList<>();
        Set<String> claimedKeys = new LinkedHashSet<>();
        for (PlayerState player : table) {
            for (String raw : player.milestones) {
                String name = displayName(raw);
                String key = key(name);
                if (key.isBlank() || !claimedKeys.add(key)) {
                    continue;
                }
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("name", name);
                row.put("playerId", player.id);
                row.put("playerName", player.accountName());
                row.put("color", player.color);
                row.put("yours", player.human);
                claimed.add(row);
            }
        }

        int left = Math.max(0, MAX_CLAIMED - claimed.size());
        List<Map<String, Object>> grab = new ArrayList<>();
        List<Map<String, Object>> close = new ArrayList<>();
        boolean ended = state.phase != null && state.phase.toLowerCase(Locale.ROOT).contains("endgame");
        if (you != null && left > 0 && !ended) {
            boolean canPay = canPay(you);
            for (String name : track(state)) {
                if (claimedKeys.contains(key(name))) {
                    continue;
                }
                Progress p = progress(name, you, state);
                if (p == null || !p.known) {
                    continue;
                }
                if (p.have >= p.need) {
                    grab.add(p.toMap(canPay));
                } else if (p.need - p.have == 1) {
                    close.add(p.toMap(canPay));
                }
            }
            grab.sort((a, b) -> Boolean.compare(!Boolean.TRUE.equals(a.get("canPay")),
                    !Boolean.TRUE.equals(b.get("canPay"))));
        }

        out.put("claimed", claimed);
        out.put("left", left);
        out.put("cost", COST);
        out.put("grab", grab);
        out.put("close", close);
        return out;
    }

    static List<String> track(GameState state) {
        LinkedHashSet<String> names = new LinkedHashSet<>(forBoard(state.board));
        for (String listed : state.listedMilestones) {
            names.add(displayName(listed));
        }
        return List.copyOf(names);
    }

    static List<String> forBoard(String board) {
        String b = board == null ? "" : board.toLowerCase(Locale.ROOT);
        if (b.contains("hellas")) {
            return List.of("Diversifier", "Tactician", "Polar Explorer", "Energizer", "Rim Settler");
        }
        if (b.contains("elysium")) {
            return List.of("Generalist", "Specialist", "Ecologist", "Tycoon", "Legend");
        }
        return List.of("Terraformer", "Mayor", "Gardener", "Builder", "Planner");
    }

    static Progress progress(String name, PlayerState p, GameState state) {
        return switch (key(name)) {
            case "terraformer" -> new Progress(name, p.tr, 35, "TR");
            case "mayor" -> new Progress(name, p.cities, 3, "cities");
            case "gardener" -> new Progress(name, p.greeneries, 3, "greeneries");
            case "builder" -> new Progress(name, tag(p, "building") + tag(p, "wild"), 8, "building tags");
            case "planner" -> null;
            case "diversifier" -> new Progress(name, distinctTags(p), 8, "different tags");
            case "tactician" -> new Progress(name, cardsWithReq(p), 5, "cards with requirements");
            case "polarexplorer" -> new Progress(name, southTiles(p, state), 3, "south-row tiles");
            case "energizer" -> new Progress(name, p.energyProd, 6, "energy production");
            case "rimsettler" -> new Progress(name, tag(p, "jovian") + tag(p, "wild"), 3, "Jovian tags");
            case "generalist" -> new Progress(name, productionsAtLeast(p, 1), 6, "productions at 1+");
            case "specialist" -> new Progress(name, maxProduction(p), 10, "in one production");
            case "ecologist" -> new Progress(name, tag(p, "plant", "microbe", "animal") + tag(p, "wild"), 4, "bio tags");
            case "tycoon" -> new Progress(name, projectCards(p), 15, "project cards");
            case "legend" -> new Progress(name, p.events.size(), 5, "events");
            default -> null;
        };
    }

    private static int tag(PlayerState p, String... keys) {
        int n = 0;
        for (String key : keys) {
            n += p.tags.getOrDefault(key, 0);
        }
        return n;
    }

    private static int distinctTags(PlayerState p) {
        int n = 0;
        for (Map.Entry<String, Integer> e : p.tags.entrySet()) {
            if (e.getValue() != null && e.getValue() > 0) {
                n++;
            }
        }
        return n;
    }

    private static int cardsWithReq(PlayerState p) {
        int n = 0;
        for (PlayedCard card : p.allCards()) {
            if (card.hasRequirement) {
                n++;
            }
        }
        return n;
    }

    private static int southTiles(PlayerState p, GameState state) {
        int n = 0;
        for (PlacedTile tile : state.tiles.values()) {
            if (tile.ownerId == p.id && BoardLayout.polarSouth(tile.hex)) {
                n++;
            }
        }
        return n;
    }

    private static int productionsAtLeast(PlayerState p, int min) {
        int n = 0;
        for (int v : List.of(p.megaCreditProd, p.steelProd, p.titaniumProd, p.plantProd, p.energyProd, p.heatProd)) {
            if (v >= min) {
                n++;
            }
        }
        return n;
    }

    private static int maxProduction(PlayerState p) {
        return Math.max(p.megaCreditProd, Math.max(p.steelProd, Math.max(p.titaniumProd,
                Math.max(p.plantProd, Math.max(p.energyProd, p.heatProd)))));
    }

    private static int projectCards(PlayerState p) {
        int n = 0;
        for (PlayedCard card : p.blueCards) {
            if (card.project) {
                n++;
            }
        }
        for (PlayedCard card : p.greenCards) {
            if (card.project) {
                n++;
            }
        }
        return n;
    }

    private static boolean canPay(PlayerState p) {
        int cash = p.megaCredits;
        if (p.corporation != null && p.corporation.toLowerCase(Locale.ROOT).startsWith("helion")) {
            cash += p.heat;
        }
        return cash >= COST;
    }

    static String displayName(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replace('_', ' ').trim();
    }

    static String key(String name) {
        return displayName(name).toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
    }

    static final class Progress {
        final String name;
        final int have;
        final int need;
        final String unit;
        final boolean known;

        Progress(String name, int have, int need, String unit) {
            this.name = name;
            this.have = have;
            this.need = need;
            this.unit = unit;
            this.known = true;
        }

        Map<String, Object> toMap(boolean canPay) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", name);
            row.put("have", have);
            row.put("need", need);
            row.put("unit", unit);
            row.put("detail", have + "/" + need + " " + unit);
            row.put("canPay", canPay);
            row.put("cost", COST);
            return row;
        }
    }
}
