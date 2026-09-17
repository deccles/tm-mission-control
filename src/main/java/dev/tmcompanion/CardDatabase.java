package dev.tmcompanion;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public final class CardDatabase {
    private final Map<String, Card> byNormalized = new LinkedHashMap<>();
    private final List<Card> all;

    private CardDatabase(List<Card> all) {
        this.all = all;
        for (Card card : all) {
            if ("Credi Cor".equals(card.name)) {
                card.name = "Credicor";
            } else if ("Eco Line".equals(card.name)) {
                card.name = "Ecoline";
            } else if ("Phobo Log".equals(card.name)) {
                card.name = "Phobolog";
            }
            byNormalized.put(normalize(card.key), card);
            byNormalized.put(normalize(card.name), card);
            if (card.number != null && !card.number.isBlank()) {
                byNormalized.put("num" + card.number.toLowerCase(Locale.ROOT), card);
            }
        }
        // Digital client spelling
        alias("Coloniser Training Camp", "Colonizer Training Camp");
        alias("Urbanised Area", "Urbanized Area");
        alias("Designed Microorganisms", "Designed Micro Organisms");
        alias("EOS Chasma National Park", "Eos Chasma National Park");
        alias("Towing A Comet", "Towing A Comet");
        alias("Import of Advanced GHG", "Import Of Advanced GHG");
        alias("Convoy From Europa", "Convoy From Europa");
        alias("Water Import From Europa", "Water Import From Europa");
        alias("Beam From A Thorium Asteroid", "Beam From A Thorium Asteroid");
        alias("CEO's Favorite Project", "CEOs Favorite Project");
    }

    private void alias(String from, String to) {
        Card card = find(to);
        if (card != null) {
            byNormalized.put(normalize(from), card);
        }
    }

    public static CardDatabase load() {
        try (var in = CardDatabase.class.getResourceAsStream("/cards.json")) {
            if (in == null) {
                throw new IllegalStateException("cards.json missing from classpath");
            }
            List<Card> cards = new Gson().fromJson(new InputStreamReader(in, StandardCharsets.UTF_8),
                    new TypeToken<List<Card>>() {}.getType());
            return new CardDatabase(cards);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load card database", e);
        }
    }

    public Card find(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        Card direct = byNormalized.get(normalize(name));
        if (direct != null) {
            return direct;
        }
        String z = normalize(name.replace("ise", "ize").replace("isation", "ization"));
        return byNormalized.get(z);
    }

    public Card findByNumber(String number) {
        if (number == null || number.isBlank()) {
            return null;
        }
        Card byKey = byNormalized.get("num" + number.toLowerCase(Locale.ROOT));
        if (byKey != null) {
            return byKey;
        }
        try {
            return byNormalized.get("num" + Integer.parseInt(number.trim()));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    public List<Card> corpsWithStartingMc(int mc) {
        List<Card> matches = new ArrayList<>();
        for (Card card : all) {
            if (!"corp".equalsIgnoreCase(card.type)) {
                continue;
            }
            if (number(card.resources.get("mc")) == mc) {
                matches.add(card);
            }
        }
        return matches;
    }

    public List<String> remember(Card card, String placingType) {
        List<String> tips = new ArrayList<>();
        if (card == null) {
            if (placingType != null) {
                tips.addAll(placementTips(placingType, null));
            }
            return tips;
        }
        String extra = card.extra == null ? "" : card.extra.toLowerCase(Locale.ROOT);
        if (placingType != null && !placingType.isBlank()) {
            tips.addAll(placementTips(placingType, card));
        } else {
            for (String place : card.place) {
                tips.addAll(placementTips(place, card));
            }
        }
        if (extra.contains("next to no other tile")) {
            tips.add("Must sit next to no other tile.");
        }
        if (extra.contains("reserved")) {
            tips.add("Goes on a reserved area (Noctis, ocean strip, volcano, or off-Mars slot).");
        }
        if (extra.contains("adjacent to at least 2 other city")) {
            tips.add("City must be adjacent to at least two other cities.");
        }
        if (extra.contains("adjacent to a city")) {
            tips.add("Must be adjacent to a city.");
        }
        if (extra.contains("adjacent to any greenery") || extra.contains("adjacent to a greenery")) {
            tips.add("Must be adjacent to a greenery.");
        }
        if (extra.contains("steel or ti placement bonus")) {
            tips.add("Place on a steel or titanium hex; you get that production.");
        }
        if (extra.contains("1 additional vp for each ocean") || extra.contains("1/ocean")) {
            tips.add("Capital: every adjacent ocean is extra VP. Maximize ocean neighbors.");
        }
        if (extra.contains("1 vp per adjacent city") || extra.contains("1/city")) {
            tips.add("Commercial District: VP for each adjacent city.");
        }
        if (extra.contains("another card")) {
            tips.add("You still have to pick another card for microbes/animals after the tile.");
        }
        if (extra.contains("remove") && extra.contains("plant")) {
            tips.add("May remove plants from an opponent after this resolves.");
        }
        if (number(card.resources.get("ocean")) >= 2) {
            tips.add("This places two oceans — take a bonus for each.");
        }
        if (card.isBlue()) {
            tips.add("Blue card: the action stays available on later turns.");
        }
        return tips.stream().distinct().toList();
    }

    public String effectSummary(Card card) {
        if (card == null) {
            return "";
        }
        List<String> bits = new ArrayList<>();
        if (card.cost != null) {
            bits.add("Cost " + card.cost + " M€");
        }
        if (!card.tags.isEmpty()) {
            bits.add("Tags: " + String.join(", ", card.tags));
        }
        if (!card.production.isEmpty()) {
            bits.add("Production: " + formatMap(card.production));
        }
        if (!card.resources.isEmpty()) {
            bits.add("Immediate: " + formatMap(card.resources));
        }
        if (card.vp != null) {
            bits.add("Printed VP: " + card.vp);
        }
        if (card.extra != null && !card.extra.isBlank()) {
            bits.add(card.extra.replace('\n', ' ').replaceAll("\\s+", " ").trim());
        }
        return String.join(" · ", bits);
    }

    private static List<String> placementTips(String kind, Card card) {
        String k = kind.toLowerCase(Locale.ROOT);
        List<String> tips = new ArrayList<>();
        if (k.contains("ocean")) {
            tips.add("Place an ocean. Collect the hex bonus (M€, cards, steel, titanium, or plants). +1 TR.");
            tips.add("Oceans pay 2 M€ to adjacent cities.");
        } else if (k.contains("greenery")) {
            tips.add("Place a greenery. Raise oxygen +1 TR. Prefer adjacent to your tiles.");
            tips.add("Each greenery next to your city is 1 VP at game end.");
        } else if (k.contains("city")) {
            tips.add("Place a city. +1 M€ production. Adjacent oceans pay 2 M€ now.");
            tips.add("Later greeneries adjacent to this city are VP.");
        } else if (k.contains("mohole")) {
            tips.add("Mohole Area goes on an ocean-reserved hex. +4 heat production.");
        } else if (k.contains("naturalpreserve") || k.contains("natural preserve")) {
            tips.add("Natural Preserve: next to no other tile. +1 M€ production.");
        } else if (k.contains("ecological")) {
            tips.add("Ecological Zone: adjacent to a greenery. Animals will score.");
        } else if (k.contains("special") || k.contains("generic") || k.contains("tile")) {
            tips.add("Place this special tile. Check adjacency / reserved-area rules on the card.");
        }
        if (card != null && "Capital".equalsIgnoreCase(card.name)) {
            tips.add("Touch as many oceans as you can — those are extra VP.");
        }
        if (card != null && "Research Outpost".equalsIgnoreCase(card.name)) {
            tips.add("City must be next to no other tile. Future cards cost 1 M€ less.");
        }
        if (card != null && "Open City".equalsIgnoreCase(card.name)) {
            tips.add("Place the city on one of your greeneries (you keep the greenery VP).");
        }
        if (card != null && "Noctis City".equalsIgnoreCase(card.name)) {
            tips.add("Goes on the reserved Noctis spot, not a normal hex.");
        }
        if (card != null && "Mangrove".equalsIgnoreCase(card.name)) {
            tips.add("Greenery on an ocean-reserved hex. Raises oxygen. Counts as your greenery.");
        }
        if (card != null && "Protected Valley".equalsIgnoreCase(card.name)) {
            tips.add("Greenery on an ocean-reserved hex, plus +2 M€ production.");
        }
        if (card != null && "Lava Flows".equalsIgnoreCase(card.name)) {
            tips.add("Must be on a volcano: Tharsis Tholus, Ascraeus, Pavonis, or Arsia Mons. +2 temperature.");
        }
        return tips;
    }

    public static String normalize(String name) {
        if (name == null) {
            return "";
        }
        String s = name.toLowerCase(Locale.ROOT)
                .replace("coloniser", "colonizer")
                .replace("urbanised", "urbanized")
                .replace("micro-organisms", "microorganisms")
                .replace("micro organisms", "microorganisms");
        return s.replaceAll("[^a-z0-9]", "");
    }

    private static String formatMap(Map<String, Object> map) {
        List<String> parts = new ArrayList<>();
        map.forEach((k, v) -> parts.add(prettyResource(k) + " " + signed(v)));
        return String.join(", ", parts);
    }

    private static String prettyResource(String key) {
        return switch (key) {
            case "mc" -> "M€";
            case "ti" -> "titanium";
            case "o2" -> "oxygen";
            case "temp" -> "temperature";
            case "tr" -> "TR";
            case "card" -> "card draw";
            default -> key;
        };
    }

    private static String signed(Object value) {
        int n = number(value);
        return n > 0 ? "+" + n : Integer.toString(n);
    }

    static int number(Object value) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        if (value instanceof String s) {
            try {
                return (int) Double.parseDouble(s);
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }

    public List<Card> all() {
        return all;
    }

    public String displayName(String raw) {
        Card card = find(raw);
        return card != null ? card.name : Objects.requireNonNullElse(raw, "?");
    }

    public String tokenType(Card card) {
        if (card == null || card.extra == null || card.extra.isBlank()) {
            return null;
        }
        String extra = card.extra.toLowerCase(Locale.ROOT);
        if (extra.contains("fighter")) {
            return "fighter";
        }
        if (extra.contains("floater")) {
            return "floater";
        }
        if (extra.contains("science resource")) {
            return "science";
        }
        if (extra.contains("animal resource") || extra.contains("animal to this card")) {
            return "animal";
        }
        if (extra.contains("microbe resource") || extra.contains("microbe to this card")
                || extra.contains("add 3 microbes")) {
            return "microbe";
        }
        return null;
    }

    static int parseCardNumber(String number) {
        if (number == null || number.isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(number.trim());
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }
}
