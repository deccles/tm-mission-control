package dev.tmcompanion;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ScoreCalculator {
    private static final Pattern VP_LINE = Pattern.compile("VP:\\s*(.+)", Pattern.CASE_INSENSITIVE);

    private ScoreCalculator() {
    }

    public static Map<String, Object> estimate(GameState state, List<PlayerState> table, PlayerState you) {
        List<PlayerState> players = table == null || table.isEmpty()
                ? new ArrayList<>(state.players.values())
                : new ArrayList<>(table);
        if (players.isEmpty() && you != null) {
            players = List.of(you);
        }
        Map<Integer, Breakdown> byId = compute(state, players);

        Map<String, Object> out = new LinkedHashMap<>();
        Map<String, Breakdown> byKey = new LinkedHashMap<>();
        Map<String, Map<String, Integer>> compact = new LinkedHashMap<>();
        for (PlayerState player : players) {
            Breakdown b = byId.get(player.id);
            byKey.put(String.valueOf(player.id), b);
            if (b != null) {
                compact.put(String.valueOf(player.id), b.toCompact());
            }
        }
        out.put("byId", byKey);
        if (you != null) {
            out.put("you", byId.getOrDefault(you.id, scorePlayer(you, cityCount(players))));
        }
        if (state.tiles.isEmpty()) {
            out.put("note", "No tiles parsed yet — city adjacency VP will appear once hexes are in the log.");
        } else {
            out.put("note", "");
        }
        out.put("history", state.chartHistory(compact));
        out.put("events", new ArrayList<>(state.scoreEvents));
        return out;
    }

    static Map<Integer, Breakdown> compute(GameState state, List<PlayerState> players) {
        Map<Integer, Breakdown> byId = new LinkedHashMap<>();
        int citiesInPlay = cityCount(players);
        for (PlayerState player : players) {
            byId.put(player.id, scorePlayer(player, citiesInPlay));
        }
        applyBoard(state, byId);
        applyAwards(players, byId);
        return byId;
    }

    static int cityCount(List<PlayerState> players) {
        int citiesInPlay = players.stream().mapToInt(p -> p.cities).sum();
        if (citiesInPlay == 0) {
            citiesInPlay = players.stream().mapToInt(p -> p.citiesOnMars).sum();
        }
        return citiesInPlay;
    }

    private static Breakdown scorePlayer(PlayerState player, int citiesInPlay) {
        Breakdown b = new Breakdown();
        b.tr = player.tr;
        b.mc = player.megaCredits;
        b.milestones = player.milestones.size() * 5;
        b.greeneries = player.greeneries;
        for (PlayedCard card : player.allCards()) {
            int vp = cardVp(card, player, citiesInPlay);
            if (vp != 0) {
                b.cards += vp;
                b.cardDetails.add((vp > 0 ? "+" : "") + vp + " " + card.name);
            }
        }
        b.retotal();
        return b;
    }

    private static void applyBoard(GameState state, Map<Integer, Breakdown> byId) {
        for (PlacedTile tile : state.tiles.values()) {
            Breakdown owner = byId.get(tile.ownerId);
            if (tile.city() && BoardLayout.onMars(tile.hex) && owner != null) {
                int greeneries = 0;
                for (int adj : BoardLayout.neighbors(tile.hex)) {
                    PlacedTile other = state.tiles.get(adj);
                    if (other != null && other.greenery()) {
                        greeneries++;
                    }
                }
                if (greeneries > 0) {
                    owner.cities += greeneries;
                }
            }
            if (tile.capital() && owner != null) {
                int oceans = 0;
                for (int adj : BoardLayout.neighbors(tile.hex)) {
                    PlacedTile other = state.tiles.get(adj);
                    if (other != null && other.ocean()) {
                        oceans++;
                    }
                }
                if (oceans != 0) {
                    owner.cards += oceans;
                    owner.cardDetails.add("+" + oceans + " Capital");
                }
            }
            if (tile.commercialDistrict() && owner != null) {
                int cities = 0;
                for (int adj : BoardLayout.neighbors(tile.hex)) {
                    PlacedTile other = state.tiles.get(adj);
                    if (other != null && other.city()) {
                        cities++;
                    }
                }
                if (cities != 0) {
                    owner.cards += cities;
                    owner.cardDetails.add("+" + cities + " Commercial District");
                }
            }
        }
        byId.values().forEach(Breakdown::retotal);
    }

    private static void applyAwards(List<PlayerState> players, Map<Integer, Breakdown> byId) {
        Set<String> funded = new LinkedHashSet<>();
        for (PlayerState player : players) {
            funded.addAll(player.awards);
        }
        for (String award : funded) {
            int[] metrics = new int[players.size()];
            int best = Integer.MIN_VALUE;
            for (int i = 0; i < players.size(); i++) {
                metrics[i] = awardMetric(award, players.get(i));
                best = Math.max(best, metrics[i]);
            }
            List<Integer> first = new ArrayList<>();
            for (int i = 0; i < players.size(); i++) {
                if (metrics[i] == best) {
                    first.add(i);
                }
            }
            for (int i : first) {
                Breakdown b = byId.get(players.get(i).id);
                b.awards += 5;
                b.details.add("+5 " + award + " (1st)");
            }
            if (first.size() > 1 || players.size() < 3) {
                continue;
            }
            int secondBest = Integer.MIN_VALUE;
            for (int i = 0; i < players.size(); i++) {
                if (metrics[i] < best) {
                    secondBest = Math.max(secondBest, metrics[i]);
                }
            }
            if (secondBest == Integer.MIN_VALUE) {
                continue;
            }
            for (int i = 0; i < players.size(); i++) {
                if (metrics[i] == secondBest) {
                    Breakdown b = byId.get(players.get(i).id);
                    b.awards += 2;
                    b.details.add("+2 " + award + " (2nd)");
                }
            }
        }
        byId.values().forEach(Breakdown::retotal);
    }

    private static int awardMetric(String award, PlayerState p) {
        String key = award.toLowerCase(Locale.ROOT).replace(" ", "");
        return switch (key) {
            case "landlord" -> p.greeneries + p.cities + p.specialTiles;
            case "banker" -> p.megaCreditProd;
            case "scientist" -> p.tags.getOrDefault("science", 0);
            case "thermalist" -> p.heat;
            case "miner" -> p.steel + p.titanium;
            case "cultivator" -> p.greeneries;
            case "magnate" -> p.greenCards.size();
            case "spacebaron" -> p.tags.getOrDefault("space", 0);
            case "excentric" -> p.allCards().stream().mapToInt(c -> c.tokens).sum();
            case "contractor" -> p.tags.getOrDefault("building", 0);
            case "celebrity" -> p.megaCredits;
            case "industrialist" -> p.steelProd + p.energyProd;
            case "benefactor" -> p.tr;
            default -> 0;
        };
    }

    static int cardVp(PlayedCard card, PlayerState owner, int citiesInPlay) {
        String extra = card.extra == null ? "" : card.extra;
        Matcher m = VP_LINE.matcher(extra);
        String expr = null;
        while (m.find()) {
            expr = m.group(1).trim();
        }
        if (expr != null) {
            int cut = expr.indexOf('\n');
            if (cut >= 0) {
                expr = expr.substring(0, cut).trim();
            }
            Integer parsed = parseExpr(expr, card, owner, citiesInPlay);
            if (parsed != null) {
                return parsed;
            }
        }
        return card.printedVp;
    }

    private static Integer parseExpr(String expr, PlayedCard card, PlayerState owner, int citiesInPlay) {
        String e = expr.toLowerCase(Locale.ROOT)
                .replace("resource", "")
                .replace("*", "")
                .replace(".", "")
                .replaceAll("\\s+", " ")
                .trim();
        if (e.contains("mc") || e.contains("?")) {
            return null;
        }
        if (e.contains("ocean") || (e.contains("city") && e.contains("/") && !e.contains("3 city") && !e.contains("/3"))) {
            return 0;
        }
        int tokens = card.tokens;
        int jovian = owner.tags.getOrDefault("jovian", 0);
        if (e.contains("science") && e.contains(":")) {
            return tokens > 0 ? 3 : 0;
        }
        Matcher frac = Pattern.compile("(\\d+)\\s*/\\s*(\\d+)\\s+(animal|microbe|science|fighter)").matcher(e);
        if (frac.find()) {
            int num = Integer.parseInt(frac.group(1));
            int den = Integer.parseInt(frac.group(2));
            return den == 0 ? 0 : num * (tokens / den);
        }
        Matcher per = Pattern.compile("(\\d+)\\s*/\\s*(animal|microbe|science|fighter|jovian)").matcher(e);
        if (per.find()) {
            int num = Integer.parseInt(per.group(1));
            String kind = per.group(2);
            int count = kind.equals("jovian") ? jovian : tokens;
            return num * count;
        }
        Matcher cities = Pattern.compile("(\\d+)\\s*/\\s*3\\s*city").matcher(e);
        if (cities.find()) {
            return Integer.parseInt(cities.group(1)) * (citiesInPlay / 3);
        }
        Matcher plain = Pattern.compile("^([+-]?\\d+)$").matcher(e);
        if (plain.matches()) {
            return Integer.parseInt(plain.group(1));
        }
        return null;
    }

    public static final class Breakdown {
        public int total;
        public int tr;
        public int mc;
        public int milestones;
        public int awards;
        public int greeneries;
        public int cities;
        public int cards;
        public List<String> details = new ArrayList<>();
        public List<String> cardDetails = new ArrayList<>();

        void retotal() {
            total = tr + milestones + awards + greeneries + cities + cards;
        }

        Map<String, Integer> toCompact() {
            Map<String, Integer> out = new LinkedHashMap<>();
            out.put("total", total);
            out.put("tr", tr);
            out.put("milestones", milestones);
            out.put("awards", awards);
            out.put("greeneries", greeneries);
            out.put("cities", cities);
            out.put("cards", cards);
            return out;
        }
    }
}
