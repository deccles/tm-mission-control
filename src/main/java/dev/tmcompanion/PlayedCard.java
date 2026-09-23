package dev.tmcompanion;

import java.util.ArrayList;
import java.util.List;

public final class PlayedCard {
    public String name;
    public String color;
    public String colorLabel;
    public List<String> tags = new ArrayList<>();
    public String extra = "";
    public int generation;
    public boolean blue;
    public boolean project = true;
    public boolean hasRequirement;
    public int number;
    public String tokenType;
    public int tokens;
    public int printedVp;
}
