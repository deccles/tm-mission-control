package dev.tmcompanion;

import java.util.ArrayList;
import java.util.List;

public final class ActivePlay {
    public int playerId;
    public String playerLabel;
    public String cardName;
    public String colorLabel;
    public String playerColor;
    public String effect;
    public String placing;
    public List<String> remember = new ArrayList<>();
    public boolean yours;
}
