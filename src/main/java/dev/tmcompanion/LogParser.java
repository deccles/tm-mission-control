package dev.tmcompanion;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LogParser {
    private static final Pattern PLAYING_CARD = Pattern.compile(
            "Playing(?: action)? \\((\\d+(?:\\.\\d+)*)\\) CardPlayerAction Card: (.+)$");
    private static final Pattern CONFIRM_PLAY = Pattern.compile(
            "Do you want to play: (.+)\\??$");
    private static final Pattern PLAYER_PLAYS_ID = Pattern.compile(
            "\\[PlayerAction] Player (\\d+) plays the card (\\d+)");
    private static final Pattern PLAYER_PLAY = Pattern.compile(
            "\\[PlayerAction] Player (\\d+) Play for player");
    private static final Pattern TRAY = Pattern.compile("Setting tray to player (\\d+)");
    private static final Pattern CURRENT_PLAYER = Pattern.compile("CurrentPlayerLocalId: (\\d+)");
    private static final Pattern PLACE_TILE = Pattern.compile(
            "PlaceTile => Type: \\(([^)]+)\\).*Place tile (\\S+) on id (-?\\d+)");
    private static final Pattern RESOURCE = Pattern.compile(
            "\\[PlayerResources] Set (MegaCredit|Steel|Titanium|Plant|Energy|Heat) (quantity|production) from (-?\\d+) to (-?\\d+)");
    private static final Pattern TR = Pattern.compile(
            "\\[BoardDatas] Set terraforming rating for player (\\d+), from (-?\\d+) to (-?\\d+)");
    private static final Pattern GENERATION = Pattern.compile("Increment Generation to (\\d+)");
    private static final Pattern PHASE = Pattern.compile("GameSM enters (\\w+)");
    private static final Pattern GAME_ID = Pattern.compile("^GameID: (.+)$");
    private static final Pattern BOARD = Pattern.compile("^BoardType: (.+)$");
    private static final Pattern PLAYER_HEADER = Pattern.compile("^Player (\\d+)$");
    private static final Pattern AGENT = Pattern.compile("^AgentType: (.+)$");
    private static final Pattern PLAYER_NAME = Pattern.compile("^Name: (.+)$");
    private static final Pattern CORP_ID = Pattern.compile("^\\tCorporation : (\\d+)$");
    private static final Pattern MILESTONE = Pattern.compile(
            "\\[PlayerAction] Playing(?: action)? \\(\\d+\\) Milestone Action: (.+)$");
    private static final Pattern AWARD = Pattern.compile(
            "\\[PlayerAction] Playing(?: action)? \\(\\d+\\) Award Action: (.+)$");
    private static final Pattern STANDARD = Pattern.compile(
            "Playing(?: action)? \\((\\d+(?:\\.\\d+)*)\\) Standard Project: (.+)$");
    private static final Pattern BLUE_ACTION = Pattern.compile(
            "Playing(?: action)? \\((\\d+(?:\\.\\d+)*)\\) Blue Card Action : (.+)$");
    private static final Pattern CORP_ACTION = Pattern.compile(
            "Playing(?: action)? \\((\\d+(?:\\.\\d+)*)\\) Corporation action : (.+)$");
    private static final Pattern PLACE_TILE_ACTION = Pattern.compile(
            "Playing action \\(([\\d.]+)\\) Place(Ocean|City|Greenery|Generic)TilePlayerAction");
    private static final Pattern CARD_TOKEN = Pattern.compile(
            "\\[PlayerResources] (Adding|Removing) (-?\\d+) (\\w+) (?:to|from) (\\d+)");
    private static final Pattern CORP_CITY = Pattern.compile(
            "\\[PlayerAction] (?:Adding|Playing) action \\((\\d+)\\) PlaceCityTilePlayerAction");
    private static final Pattern ASKING_PLAYER = Pattern.compile(
            "asking for input for player (\\d+)");
    private static final Pattern TAB_PLAYER = Pattern.compile("Tab_Player_(\\d+)");

    private final CardDatabase cards;
    private final GameState state;

    private int currentPlayer = 1;
    private int headerPlayer = 0;
    private boolean inHeader;
    private int pendingCorpMc = -1;
    private int pendingCorpPlayer = 0;
    private List<Card> pendingCorps = List.of();
    private String lastCardKey = "";

    public LogParser(CardDatabase cards, GameState state) {
        this.cards = cards;
        this.state = state;
    }

    public void replay(Path logFile) throws Exception {
        int startLine = lastGameLine(logFile);
        try (var reader = Files.newBufferedReader(logFile, StandardCharsets.UTF_8)) {
            int lineNo = 0;
            String line;
            while ((line = reader.readLine()) != null) {
                lineNo++;
                if (lineNo >= startLine) {
                    consume(line);
                }
            }
        }
    }

    static int lastGameLine(Path logFile) throws Exception {
        int lineNo = 0;
        int last = 1;
        try (var reader = Files.newBufferedReader(logFile, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                lineNo++;
                if (line.contains("Created new Game")) {
                    last = lineNo;
                }
            }
        }
        return last;
    }

    public void consume(String line) {
        if (line == null || line.isBlank()) {
            return;
        }
        synchronized (state.lock()) {
            if (line.contains("Created new Game")) {
                state.reset();
                currentPlayer = 1;
                headerPlayer = 0;
                inHeader = true;
                pendingCorpMc = -1;
                pendingCorps = List.of();
                pendingCorpPlayer = 0;
                lastCardKey = "";
                return;
            }
            parseHeader(line);
            Matcher m;

            m = TRAY.matcher(line);
            if (m.find()) {
                currentPlayer = Integer.parseInt(m.group(1));
            }
            m = CURRENT_PLAYER.matcher(line);
            if (m.find()) {
                currentPlayer = Integer.parseInt(m.group(1));
            }
            m = PLAYER_PLAY.matcher(line);
            if (m.find()) {
                currentPlayer = Integer.parseInt(m.group(1));
            }
            m = PLAYER_PLAYS_ID.matcher(line);
            if (m.find()) {
                currentPlayer = Integer.parseInt(m.group(1));
            }
            m = TAB_PLAYER.matcher(line);
            if (m.find() && productionPhase()) {
                currentPlayer = Integer.parseInt(m.group(1));
            }

            m = GAME_ID.matcher(line.trim());
            if (m.matches()) {
                state.gameId = m.group(1).trim();
            }
            m = BOARD.matcher(line.trim());
            if (m.matches()) {
                state.board = m.group(1).trim();
            }
            m = GENERATION.matcher(line);
            if (m.find()) {
                state.generation = Integer.parseInt(m.group(1));
            }
            m = PHASE.matcher(line);
            if (m.find()) {
                state.phase = humanPhase(m.group(1));
                if ("Actions".equals(state.phase)
                        && (corpTaken("Tharsis Republic") || state.generation > 1)) {
                    resolveLeftoverCorps();
                }
            }

            m = TR.matcher(line);
            if (m.find()) {
                PlayerState p = state.player(Integer.parseInt(m.group(1)));
                p.tr = Integer.parseInt(m.group(3));
            }

            m = CORP_ID.matcher(line);
            if (m.matches()) {
                pendingCorpPlayer = currentPlayer;
                pendingCorpMc = -1;
                pendingCorps = List.of();
            }

            m = RESOURCE.matcher(line);
            if (m.find()) {
                applyResource(m.group(1), m.group(2), Integer.parseInt(m.group(3)), Integer.parseInt(m.group(4)));
            }

            m = CARD_TOKEN.matcher(line);
            if (m.find()) {
                int amount = Integer.parseInt(m.group(2));
                if ("Removing".equals(m.group(1))) {
                    amount = -Math.abs(amount);
                }
                addCardTokens(Integer.parseInt(m.group(4)), m.group(3), amount);
            }

            m = CONFIRM_PLAY.matcher(line);
            if (m.find()) {
                setActive(currentPlayer, m.group(1).trim(), null, true);
            }

            m = PLAYING_CARD.matcher(line);
            if (m.find()) {
                playCard(currentPlayer, m.group(2).trim());
            }

            m = STANDARD.matcher(line);
            if (m.find()) {
                setActive(currentPlayer, "Standard Project: " + m.group(2).trim(), m.group(2).trim(), false);
            }

            m = BLUE_ACTION.matcher(line);
            if (m.find() && !line.contains("Adding action")) {
                setActive(currentPlayer, "Using " + m.group(2).trim(), null, false);
            }
            m = CORP_ACTION.matcher(line);
            if (m.find() && !line.contains("Adding action")) {
                setActive(currentPlayer, "Using " + m.group(2).trim(), null, false);
            }
            m = PLACE_TILE_ACTION.matcher(line);
            if (m.find()) {
                pinPlacement(m.group(2));
            }

            m = ASKING_PLAYER.matcher(line);
            if (m.find()) {
                currentPlayer = Integer.parseInt(m.group(1));
            }
            m = CORP_CITY.matcher(line);
            if (m.find() && Integer.parseInt(m.group(1)) != 5 && state.generation <= 1) {
                assignTharsisIfPending();
            }

            m = PLACE_TILE.matcher(line);
            if (m.find()) {
                onPlace(m.group(1), Integer.parseInt(m.group(3)));
            }

            m = MILESTONE.matcher(line);
            if (m.find() && !line.contains("Playing action")) {
                state.player(currentPlayer).milestones.add(m.group(1).trim());
            }
            m = AWARD.matcher(line);
            if (m.find() && !line.contains("Playing action")) {
                state.player(currentPlayer).awards.add(m.group(1).trim());
            }

            if (line.contains("ShowOpponentCardPage") && state.activePlay != null) {
                state.activePlay.yours = false;
            }
        }
    }

    private void parseHeader(String line) {
        String trimmed = line.trim();
        if (trimmed.equals("----- Game Info -----")) {
            inHeader = true;
            return;
        }
        if (trimmed.equals("---------------------")) {
            inHeader = false;
            headerPlayer = 0;
            return;
        }
        if (!inHeader) {
            return;
        }
        Matcher m = PLAYER_HEADER.matcher(trimmed);
        if (m.matches()) {
            headerPlayer = Integer.parseInt(m.group(1));
            PlayerState seated = state.player(headerPlayer);
            seated.seated = true;
            seated.color = PlayerState.colorFor(headerPlayer);
            return;
        }
        if (headerPlayer == 0) {
            return;
        }
        m = PLAYER_NAME.matcher(trimmed);
        if (m.matches()) {
            String name = m.group(1).trim();
            if (name.matches("\\d+ Player")) {
                state.player(headerPlayer).name = "Player " + headerPlayer;
            } else {
                state.player(headerPlayer).name = name;
            }
            return;
        }
        if (trimmed.startsWith("AI - ")) {
            state.player(headerPlayer).name = trimmed;
            return;
        }
        m = AGENT.matcher(trimmed);
        if (m.matches()) {
            boolean human = m.group(1).equalsIgnoreCase("Human");
            state.player(headerPlayer).human = human;
            if (human) {
                state.humanId = headerPlayer;
                if (state.player(headerPlayer).name.startsWith("Player ")) {
                    state.player(headerPlayer).name = "You";
                }
            }
        }
    }

    private void applyResource(String kind, String qtyOrProd, int from, int to) {
        boolean production = "production".equals(qtyOrProd);
        PlayerState target;
        if (pendingCorpPlayer > 0 && !production && "MegaCredit".equals(kind) && from == 0
                && "Unknown".equals(state.player(pendingCorpPlayer).corporation)) {
            target = state.player(pendingCorpPlayer);
        } else {
            target = findOwner(kind, production, from);
            if (target == null) {
                target = state.player(currentPlayer);
            }
        }
        setField(target, kind, production, to);
        if (!production && "MegaCredit".equals(kind) && from == 0 && "Unknown".equals(target.corporation)) {
            identifyCorpByMc(target, to);
        }
        if (!production && pendingCorps.size() > 1 && target.id == pendingCorpPlayer) {
            refineCorpByResource(target, kind, to);
        }
        if (production && pendingCorps.size() > 1 && "Unknown".equals(target.corporation)) {
            refineCorpByProduction(target, kind, to);
        }
    }

    private void identifyCorpByMc(PlayerState player, int mc) {
        List<Card> matches = new ArrayList<>();
        for (Card corp : cards.corpsWithStartingMc(mc)) {
            if (!corpTaken(corp.name)) {
                matches.add(corp);
            }
        }
        pendingCorpPlayer = player.id;
        pendingCorpMc = mc;
        pendingCorps = matches;
        player.startingMc = mc;
        if (matches.size() == 1) {
            assignCorp(player, matches.get(0));
        }
    }

    private void refineCorpByResource(PlayerState player, String kind, int to) {
        String key = switch (kind) {
            case "Steel" -> "steel";
            case "Titanium" -> "ti";
            case "Plant" -> "plant";
            default -> "";
        };
        if (key.isEmpty()) {
            return;
        }
        for (Card corp : pendingCorps) {
            if (CardDatabase.number(corp.resources.get(key)) == to) {
                assignCorp(player, corp);
                return;
            }
        }
    }

    private void refineCorpByProduction(PlayerState player, String kind, int to) {
        String key = switch (kind) {
            case "Heat" -> "heat";
            case "Energy" -> "energy";
            case "Steel" -> "steel";
            case "Titanium" -> "ti";
            case "Plant" -> "plant";
            case "MegaCredit" -> "mc";
            default -> "";
        };
        if (key.isEmpty()) {
            return;
        }
        for (Card corp : pendingCorps) {
            int extra = CardDatabase.number(corp.production.get(key));
            if (extra > 0 && to == 1 + extra) {
                assignCorp(player, corp);
                return;
            }
        }
    }

    private boolean corpTaken(String name) {
        for (PlayerState p : state.players.values()) {
            if (name.equals(p.corporation)) {
                return true;
            }
        }
        return false;
    }

    private void assignTharsisIfPending() {
        Card tharsis = cards.find("Tharsis Republic");
        if (tharsis == null || corpTaken(tharsis.name)) {
            resolveLeftoverCorps();
            return;
        }
        PlayerState player = state.player(currentPlayer);
        if (!"Unknown".equals(player.corporation)) {
            return;
        }
        if (player.startingMc != 40 && player.startingMc != -1) {
            return;
        }
        assignCorp(player, tharsis);
        resolveLeftoverCorps();
    }

    private void resolveLeftoverCorps() {
        if (!corpTaken("Tharsis Republic") && state.generation < 2) {
            return;
        }
        Card unmi = cards.find("United Nations Mars Initiative");
        if (unmi == null) {
            return;
        }
        for (PlayerState p : state.players.values()) {
            if ("Unknown".equals(p.corporation) && p.startingMc == 40) {
                assignCorp(p, unmi);
            }
        }
    }

    private void assignCorp(PlayerState player, Card corp) {
        pendingCorps = List.of();
        pendingCorpMc = -1;
        if (!"Unknown".equals(player.corporation)) {
            return;
        }
        player.corporation = corp.name;
        for (String tag : corp.tags) {
            player.addTag(tag);
        }
    }

    private PlayerState findOwner(String kind, boolean production, int from) {
        List<PlayerState> matches = new ArrayList<>();
        for (PlayerState p : state.players.values()) {
            if (getField(p, kind, production) == from) {
                matches.add(p);
            }
        }
        if (matches.size() == 1) {
            return matches.get(0);
        }
        PlayerState current = state.players.get(currentPlayer);
        if (current != null && getField(current, kind, production) == from) {
            return current;
        }
        if (productionPhase() && current != null) {
            return current;
        }
        return matches.isEmpty() ? current : matches.get(0);
    }

    private boolean productionPhase() {
        String phase = state.phase;
        return "Production".equals(phase) || "FinalPlantConversion".equals(phase);
    }

    private static int getField(PlayerState p, String kind, boolean production) {
        return switch (kind) {
            case "MegaCredit" -> production ? p.megaCreditProd : p.megaCredits;
            case "Steel" -> production ? p.steelProd : p.steel;
            case "Titanium" -> production ? p.titaniumProd : p.titanium;
            case "Plant" -> production ? p.plantProd : p.plants;
            case "Energy" -> production ? p.energyProd : p.energy;
            case "Heat" -> production ? p.heatProd : p.heat;
            default -> 0;
        };
    }

    private static void setField(PlayerState p, String kind, boolean production, int to) {
        switch (kind) {
            case "MegaCredit" -> {
                if (production) p.megaCreditProd = to;
                else p.megaCredits = to;
            }
            case "Steel" -> {
                if (production) p.steelProd = to;
                else p.steel = to;
            }
            case "Titanium" -> {
                if (production) p.titaniumProd = to;
                else p.titanium = to;
            }
            case "Plant" -> {
                if (production) p.plantProd = to;
                else p.plants = to;
            }
            case "Energy" -> {
                if (production) p.energyProd = to;
                else p.energy = to;
            }
            case "Heat" -> {
                if (production) p.heatProd = to;
                else p.heat = to;
            }
            default -> {
            }
        }
    }

    private void playCard(int playerId, String rawName) {
        String key = playerId + "|" + CardDatabase.normalize(rawName);
        if (key.equals(lastCardKey)) {
            return;
        }
        lastCardKey = key;
        Card card = cards.find(rawName);
        String name = cards.displayName(rawName);
        PlayerState player = state.player(playerId);
        PlayedCard played = new PlayedCard();
        played.name = name;
        played.generation = state.generation;
        if (card != null) {
            played.color = card.color;
            played.colorLabel = card.colorLabel();
            played.tags = List.copyOf(card.tags);
            played.extra = card.extra;
            played.blue = card.isBlue();
            played.number = CardDatabase.parseCardNumber(card.number);
            played.tokenType = cards.tokenType(card);
            played.printedVp = card.vp == null ? 0 : card.vp;
        } else {
            played.color = "green";
            played.colorLabel = "";
            played.blue = false;
        }
        player.addCard(played);
        setActive(playerId, name, card != null && !card.place.isEmpty() ? card.place.get(0) : null, false);
        currentPlayer = playerId;
    }

    private void addCardTokens(int cardNumber, String rawType, int amount) {
        PlayedCard card = state.cardByNumber(cardNumber);
        if (card == null) {
            return;
        }
        String type = switch (rawType.toLowerCase()) {
            case "microbe", "microbes" -> "microbe";
            case "animal", "animals" -> "animal";
            case "science" -> "science";
            case "fighter", "fighters" -> "fighter";
            case "floater", "floaters" -> "floater";
            default -> rawType.toLowerCase();
        };
        if (card.tokenType == null || card.tokenType.isBlank()) {
            card.tokenType = type;
        }
        card.tokens = Math.max(0, card.tokens + amount);
    }

    private void setActive(int playerId, String name, String placing, boolean preview) {
        PlayerState player = state.player(playerId);
        String lookup = name.startsWith("Using ") ? name.substring(6) : name.replace("Standard Project: ", "");
        Card card = cards.find(lookup);
        ActivePlay play = new ActivePlay();
        play.playerId = playerId;
        play.playerLabel = player.label();
        play.cardName = name.startsWith("Standard Project:") ? name : cards.displayName(lookup);
        play.yours = player.human;
        play.colorLabel = name.startsWith("Using ")
                ? "Action"
                : (card != null ? card.colorLabel() : (preview ? "Confirming" : ""));
        play.playerColor = player.color;
        play.effect = card != null ? cards.effectSummary(card) : (preview ? "Waiting for confirm." : "");
        play.placing = placing;
        play.remember.addAll(cards.remember(card, placing));
        if (preview && play.remember.isEmpty()) {
            play.remember.add("Confirming this card. Effects stay on this screen if a tile placement follows.");
        }
        state.activePlay = play;
        state.live = true;
    }

    private void pinPlacement(String kind) {
        if (state.activePlay == null) {
            setActive(currentPlayer, "Placing " + kind, kind, false);
            return;
        }
        state.activePlay.placing = kind;
        String lookup = state.activePlay.cardName == null ? "" : state.activePlay.cardName
                .replace("Using ", "")
                .replace("Standard Project: ", "")
                .replace("Placing ", "");
        Card card = cards.find(lookup);
        state.activePlay.remember = cards.remember(card, kind);
        if (card != null && (state.activePlay.effect == null || state.activePlay.effect.isBlank())) {
            state.activePlay.effect = cards.effectSummary(card);
        }
    }

    private void onPlace(String type, int hex) {
        PlayerState player = state.player(currentPlayer);
        String kind = type.toLowerCase().replace(" ", "").replace("_", "");
        String cardName = state.activePlay == null ? "" : state.activePlay.cardName;
        if (kind.contains("city") || "capital".equals(kind)) {
            player.cities++;
            if (!offMarsCity(cardName) && BoardLayout.onMars(hex)) {
                player.citiesOnMars++;
            }
        } else if (kind.contains("greenery")) {
            player.greeneries++;
        } else if (kind.contains("ocean")) {
            player.oceans++;
        } else {
            player.specialTiles++;
        }
        if (hex > 0) {
            placeOnBoard(hex, kind, cardName, player.id);
        }
        if ((kind.contains("city") || "capital".equals(kind)) && state.generation <= 1
                && !offMarsCity(cardName)) {
            assignTharsisIfPending();
        }
        if (state.activePlay == null) {
            setActive(currentPlayer, "Placing " + type, type, false);
        } else {
            state.activePlay.placing = type;
            Card card = cards.find(state.activePlay.cardName);
            state.activePlay.remember = cards.remember(card, type);
            if (card != null) {
                state.activePlay.effect = cards.effectSummary(card);
            }
        }
    }

    private void placeOnBoard(int hex, String kind, String cardName, int playerId) {
        PlacedTile tile = new PlacedTile();
        tile.hex = hex;
        String card = cardName == null ? "" : cardName.toLowerCase();
        if (card.contains("capital") || "capital".equals(kind)) {
            tile.type = "capital";
            tile.ownerId = playerId;
        } else if (card.contains("commercial district") || "commercialdistrict".equals(kind)) {
            tile.type = "commercialdistrict";
            tile.ownerId = playerId;
        } else if (kind.contains("city")) {
            tile.type = "city";
            tile.ownerId = playerId;
        } else if (kind.contains("greenery")) {
            tile.type = "greenery";
            tile.ownerId = playerId;
        } else if (kind.contains("ocean")) {
            tile.type = "ocean";
            tile.ownerId = 0;
        } else {
            tile.type = kind;
            tile.ownerId = playerId;
        }
        state.tiles.put(hex, tile);
    }

    private static boolean offMarsCity(String cardName) {
        if (cardName == null) {
            return false;
        }
        String n = cardName.toLowerCase();
        return n.contains("phobos") || n.contains("ganymede") || n.contains("luna") || n.contains("stanford");
    }

    private static String humanPhase(String raw) {
        return switch (raw) {
            case "ActionPhase" -> "Actions";
            case "ProductionPhase" -> "Production";
            case "ResearchPhase" -> "Research";
            case "PlayerOrderPhase" -> "Player order";
            case "PlayerSetup", "InitializationPhase" -> "Setup";
            case "SaveGameState" -> "Saving";
            default -> raw.replace("Phase", "").replace("State", "");
        };
    }
}
