package dev.tmmissioncontrol;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Seat colors from the Steam client's {@code PlayerColorPreference.GetPlayerColorOrder}.
 * Enum name {@code Grey} is the purple tab in the HUD.
 */
public final class PlayerColors {
    static final List<String> POOL = List.of("blue", "green", "purple", "red", "yellow");

    private PlayerColors() {
    }

    public static String colorFor(int playerId) {
        String[] order = order(Math.max(playerId, 5), 1, preferredFromDisk());
        if (playerId >= 1 && playerId <= order.length) {
            return order[playerId - 1];
        }
        return "black";
    }

    public static void apply(GameState state) {
        List<PlayerState> seated = state.seatedPlayers();
        int playerCount = seated.size();
        if (playerCount == 0) {
            return;
        }
        int humanId = state.humanId;
        String[] colors = order(playerCount, humanId, preferredFromDisk());
        for (PlayerState player : seated) {
            if (player.id >= 1 && player.id <= colors.length && colors[player.id - 1] != null) {
                player.color = colors[player.id - 1];
            }
        }
    }

    static String[] order(int playerCount, int humanId, String preferred) {
        String[] assigned = new String[playerCount];
        int preferredIndex = preferred == null ? -1 : POOL.indexOf(preferred);
        int next = 0;
        if (humanId > 0 && humanId <= playerCount) {
            if (preferredIndex >= 0) {
                assigned[humanId - 1] = POOL.get(preferredIndex);
            } else {
                assigned[humanId - 1] = POOL.get(next);
                next++;
            }
        }
        for (int i = 1; i <= playerCount; i++) {
            if (next >= POOL.size()) {
                break;
            }
            if (i == humanId) {
                continue;
            }
            if (next == preferredIndex) {
                next++;
            }
            if (next >= POOL.size()) {
                break;
            }
            assigned[i - 1] = POOL.get(next);
            next++;
        }
        return assigned;
    }

    static String preferredFromDisk() {
        Path file = Path.of(System.getProperty("user.home"),
                "AppData", "LocalLow", "LuckyHammers", "Terraforming Mars", "Saves", "GameData", "GameData");
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            JsonObject data = new Gson().fromJson(json, JsonObject.class);
            if (data == null) {
                return null;
            }
            if (data.has("RandomColor") && data.get("RandomColor").getAsBoolean()) {
                return null;
            }
            JsonElement pref = data.get("ColorOrderPreference");
            if (pref == null || pref.isJsonNull() || !pref.isJsonArray() || pref.getAsJsonArray().isEmpty()) {
                return null;
            }
            JsonArray order = pref.getAsJsonArray();
            return fromGameColor(order.get(0));
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String fromGameColor(JsonElement value) {
        if (value == null || value.isJsonNull()) {
            return null;
        }
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) {
            return switch (value.getAsInt()) {
                case 0 -> "green";
                case 1 -> "purple";
                case 2 -> "yellow";
                case 3 -> "red";
                case 4 -> "blue";
                default -> null;
            };
        }
        String name = value.getAsString().trim().toLowerCase();
        return switch (name) {
            case "green" -> "green";
            case "grey", "gray", "purple" -> "purple";
            case "yellow" -> "yellow";
            case "red" -> "red";
            case "blue" -> "blue";
            default -> null;
        };
    }
}
