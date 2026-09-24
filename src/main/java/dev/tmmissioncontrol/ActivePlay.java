package dev.tmmissioncontrol;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ActivePlay {
    public int playerId;
    public String playerLabel;
    public String cardName;
    public String colorLabel;
    public String playerColor;
    public String effect;
    public String placing;
    public Integer cost;
    public Integer vp;
    public List<String> tags = new ArrayList<>();
    public Map<String, Object> production = new LinkedHashMap<>();
    public Map<String, Object> resources = new LinkedHashMap<>();
    public List<String> remember = new ArrayList<>();
    public List<DrawnCard> drawn = new ArrayList<>();
    public boolean yours;

    public static final class DrawnCard {
        public String name;
        public String color;
        public Integer cost;
        public Integer vp;
        public List<String> tags = new ArrayList<>();
        public String extra = "";
        public Map<String, Object> production = new LinkedHashMap<>();
        public Map<String, Object> resources = new LinkedHashMap<>();
        public Map<String, Object> req = new LinkedHashMap<>();
    }

    void fromCard(Card card) {
        if (card == null) {
            return;
        }
        cost = card.cost;
        vp = card.vp;
        tags = List.copyOf(card.tags);
        if (card.production != null) {
            production = new LinkedHashMap<>(card.production);
        }
        if (card.resources != null) {
            resources = new LinkedHashMap<>(card.resources);
        }
    }
}
