package dev.tmcompanion;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class PlayerState {
    public final int id;
    public String name;
    public boolean human;
    public boolean seated;
    public String color;
    public String corporation = "Unknown";
    public String corpRules = "";
    public int startingMc = -1;
    public int tr = 20;

    public int megaCredits;
    public int steel;
    public int titanium;
    public int plants;
    public int energy;
    public int heat;

    public int megaCreditProd = 1;
    public int steelProd = 1;
    public int titaniumProd = 1;
    public int plantProd = 1;
    public int energyProd = 1;
    public int heatProd = 1;

    public int citiesOnMars;
    public int cities;
    public int greeneries;
    public int oceans;
    public int specialTiles;

    public final Map<String, Integer> tags = new LinkedHashMap<>();
    public final List<PlayedCard> blueCards = new ArrayList<>();
    public final List<PlayedCard> greenCards = new ArrayList<>();
    public final List<PlayedCard> events = new ArrayList<>();
    public final List<String> milestones = new ArrayList<>();
    public final List<String> awards = new ArrayList<>();

    public PlayerState(int id) {
        this.id = id;
        this.name = "Player " + id;
        this.color = PlayerColors.colorFor(id);
        for (String tag : List.of(
                "building", "space", "science", "power", "earth", "jovian",
                "plant", "microbe", "animal", "city", "event")) {
            tags.put(tag, 0);
        }
    }

    public String displayName() {
        if (corporation != null && !corporation.isBlank() && !"Unknown".equals(corporation)) {
            return corporation;
        }
        if (human) {
            return "You";
        }
        return name == null || name.isBlank() ? "Player " + id : name;
    }

    public static String colorFor(int playerId) {
        return PlayerColors.colorFor(playerId);
    }

    public String label() {
        if (human) {
            return name + " (you)";
        }
        return name;
    }

    public void addTag(String tag) {
        if (tag == null || tag.isBlank()) {
            return;
        }
        String key = tag.toLowerCase();
        tags.merge(key, 1, Integer::sum);
    }

    public void addCard(PlayedCard card) {
        if (card.blue) {
            blueCards.add(card);
        } else if ("red".equalsIgnoreCase(card.color)) {
            events.add(card);
        } else {
            greenCards.add(card);
        }
        for (String tag : card.tags) {
            addTag(tag);
        }
    }

    public PlayedCard cardByNumber(int number) {
        if (number <= 0) {
            return null;
        }
        for (PlayedCard card : blueCards) {
            if (card.number == number) {
                return card;
            }
        }
        for (PlayedCard card : greenCards) {
            if (card.number == number) {
                return card;
            }
        }
        for (PlayedCard card : events) {
            if (card.number == number) {
                return card;
            }
        }
        return null;
    }

    public List<PlayedCard> allCards() {
        List<PlayedCard> all = new ArrayList<>();
        all.addAll(blueCards);
        all.addAll(greenCards);
        all.addAll(events);
        return all;
    }
}
