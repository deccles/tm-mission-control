package dev.tmmissioncontrol;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Card {
    public String key;
    public String name;
    public String number;
    public String type;
    public String color;
    public Integer cost;
    public List<String> tags = new ArrayList<>();
    public Integer vp;
    public Map<String, Object> resources = new LinkedHashMap<>();
    public Map<String, Object> production = new LinkedHashMap<>();
    public String extra = "";
    public List<String> place = new ArrayList<>();
    public Map<String, Object> req = new LinkedHashMap<>();

    public boolean hasRequirement() {
        if (req != null && !req.isEmpty()) {
            return true;
        }
        if (extra == null || extra.isBlank()) {
            return false;
        }
        String e = extra.toLowerCase();
        return e.contains("it must be") || e.contains("requires that") || e.contains("requires you");
    }

    public boolean isBlue() {
        return "blue".equalsIgnoreCase(color);
    }

    public boolean isEvent() {
        return "red".equalsIgnoreCase(color) || tags.stream().anyMatch(t -> t.equalsIgnoreCase("event"));
    }

    public String colorLabel() {
        return switch (color == null ? "" : color.toLowerCase()) {
            case "blue" -> "Active (blue)";
            case "red" -> "Event (red)";
            case "green" -> "Automated (green)";
            default -> color == null ? "" : color;
        };
    }
}
