package dev.tmcompanion;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class GameState {
    private final Object lock = new Object();
    public String gameId = "";
    public String board = "Tharsis";
    public int generation = 1;
    public String phase = "Waiting for a match";
    public String url = "";
    public List<String> urls = List.of();
    public boolean firewallOpen;
    public boolean live;
    public boolean prelude;
    public boolean venus;
    public boolean colonies;
    public int humanId = 1;
    public final Map<Integer, PlayerState> players = new LinkedHashMap<>();
    public final Map<Integer, PlacedTile> tiles = new LinkedHashMap<>();
    public ActivePlay activePlay;

    public PlayerState player(int id) {
        return players.computeIfAbsent(id, PlayerState::new);
    }

    public void reset() {
        synchronized (lock) {
            gameId = "";
            board = "Tharsis";
            generation = 1;
            phase = "Setup";
            live = true;
            prelude = false;
            venus = false;
            colonies = false;
            humanId = 1;
            players.clear();
            tiles.clear();
            activePlay = null;
            player(1);
            player(2);
        }
    }

    public Object snapshot() {
        synchronized (lock) {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("url", url);
            out.put("urls", urls);
            out.put("firewallOpen", firewallOpen);
            out.put("gameId", gameId);
            out.put("board", board);
            out.put("generation", generation);
            out.put("phase", phase);
            out.put("live", live);
            out.put("activePlay", activePlay);
            List<PlayerState> seated = seatedPlayers();
            PlayerState you = seated.stream().filter(p -> p.human).findFirst()
                    .orElse(players.getOrDefault(humanId, player(humanId)));
            List<PlayerState> opponents = seated.stream().filter(p -> p.id != you.id).toList();
            List<PlayerState> table = new ArrayList<>();
            table.add(you);
            for (PlayerState opponent : opponents) {
                if (!table.contains(opponent)) {
                    table.add(opponent);
                }
            }
            out.put("you", you);
            out.put("opponents", opponents);
            out.put("players", table);
            out.put("tiles", new ArrayList<>(tiles.values()));
            out.put("score", ScoreCalculator.estimate(this, table, you));
            return out;
        }
    }

    public List<PlayerState> seatedPlayers() {
        List<PlayerState> seated = players.values().stream().filter(p -> p.seated).toList();
        if (!seated.isEmpty()) {
            return seated;
        }
        return new ArrayList<>(players.values());
    }

    public Object lock() {
        return lock;
    }

    public PlayedCard cardByNumber(int number) {
        for (PlayerState player : players.values()) {
            PlayedCard card = player.cardByNumber(number);
            if (card != null) {
                return card;
            }
        }
        return null;
    }
}
