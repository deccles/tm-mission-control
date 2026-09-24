package dev.tmmissioncontrol;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

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
        alias("Self-Replicating Bacteria", "Self-replicating robots");
        alias("Morning Star Inc.", "Morning Star Inc");
        alias("Stormcraft", "Stormcraft Incorporated");
    }

    private void alias(String from, String to) {
        Card card = find(to);
        if (card != null) {
            byNormalized.put(normalize(from), card);
        }
    }

    public static CardDatabase load() {
        try (FoundCards found = openCards()) {
            if (found == null || found.in == null) {
                throw new IllegalStateException("cards.json missing from classpath and disk");
            }
            List<Card> cards = new Gson().fromJson(new InputStreamReader(found.in, StandardCharsets.UTF_8),
                    new TypeToken<List<Card>>() {}.getType());
            if (cards == null || cards.isEmpty()) {
                throw new IllegalStateException("cards.json was empty");
            }
            System.out.println("Card catalog: " + cards.size() + " cards from " + found.source);
            return new CardDatabase(cards);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load card database", e);
        }
    }

    private static FoundCards openCards() throws Exception {
        InputStream packaged = CardDatabase.class.getResourceAsStream("/cards.json");
        if (packaged != null) {
            return new FoundCards(packaged, "classpath");
        }
        for (Path path : cardFileCandidates()) {
            if (Files.isRegularFile(path)) {
                return new FoundCards(Files.newInputStream(path), path.toAbsolutePath().toString());
            }
        }
        return null;
    }

    private static List<Path> cardFileCandidates() {
        LinkedHashSet<Path> out = new LinkedHashSet<>();
        addSearchRoots(out, Path.of(System.getProperty("user.dir", ".")).toAbsolutePath().normalize());
        try {
            URI loc = CardDatabase.class.getProtectionDomain().getCodeSource().getLocation().toURI();
            addSearchRoots(out, Path.of(loc).toAbsolutePath().normalize());
        } catch (Exception ignored) {
        }
        return new ArrayList<>(out);
    }

    private static void addSearchRoots(Set<Path> out, Path start) {
        Path dir = Files.isRegularFile(start) ? start.getParent() : start;
        for (int i = 0; i < 8 && dir != null; i++, dir = dir.getParent()) {
            out.add(dir.resolve("cards.json"));
            out.add(dir.resolve(Path.of("src", "main", "resources", "cards.json")));
            out.add(dir.resolve(Path.of("terraforming-mars", "src", "main", "resources", "cards.json")));
        }
    }

    private static final class FoundCards implements AutoCloseable {
        final InputStream in;
        final String source;

        FoundCards(InputStream in, String source) {
            this.in = in;
            this.source = source;
        }

        @Override
        public void close() throws Exception {
            if (in != null) {
                in.close();
            }
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
        return corpsWithStartingMc(mc, false, false, false);
    }

    public List<Card> corpsWithStartingMc(int mc, boolean prelude, boolean venus, boolean colonies) {
        List<Card> matches = new ArrayList<>();
        for (Card card : all) {
            if (!corpAllowed(card, prelude, venus, colonies)) {
                continue;
            }
            if (number(card.resources.get("mc")) == mc) {
                matches.add(card);
            }
        }
        return matches;
    }

    public boolean corpAllowed(Card card, boolean prelude, boolean venus, boolean colonies) {
        if (card == null || !"corp".equalsIgnoreCase(card.type)) {
            return false;
        }
        int z = zNumber(card.number);
        if (z >= 1 && z <= 12) {
            return true;
        }
        if (z >= 33) {
            return true;
        }
        if (z >= 18 && z <= 22) {
            return prelude;
        }
        if (z >= 23 && z <= 27) {
            return venus;
        }
        if (z >= 28 && z <= 32) {
            return colonies;
        }
        return prelude || venus || colonies;
    }

    public static int zNumber(String number) {
        if (number == null || number.isBlank()) {
            return -1;
        }
        String trimmed = number.trim();
        if (trimmed.length() < 2 || (trimmed.charAt(0) != 'Z' && trimmed.charAt(0) != 'z')) {
            return -1;
        }
        try {
            return Integer.parseInt(trimmed.substring(1));
        } catch (NumberFormatException ignored) {
            return -1;
        }
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

    public String cardText(Card card) {
        if (card == null || card.extra == null || card.extra.isBlank()) {
            return "";
        }
        return card.extra.replace('\n', ' ').replaceAll("\\s+", " ").trim();
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
