package dev.tmmissioncontrol;

public final class ScoreEvent {
    public int generation;
    public int playerId;
    public String kind = "";
    public String label = "";
    public int delta;

    public ScoreEvent(int generation, int playerId, String kind, String label, int delta) {
        this.generation = generation;
        this.playerId = playerId;
        this.kind = kind == null ? "" : kind;
        this.label = label == null ? "" : label;
        this.delta = delta;
    }
}
