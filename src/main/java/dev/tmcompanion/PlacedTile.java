package dev.tmcompanion;

public final class PlacedTile {
    public int hex;
    public String type = "";
    public int ownerId;

    public boolean city() {
        return "city".equals(type) || "capital".equals(type);
    }

    public boolean greenery() {
        return "greenery".equals(type);
    }

    public boolean ocean() {
        return "ocean".equals(type);
    }

    public boolean capital() {
        return "capital".equals(type);
    }

    public boolean commercialDistrict() {
        return "commercialdistrict".equals(type);
    }
}
