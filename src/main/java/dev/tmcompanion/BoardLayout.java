package dev.tmcompanion;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Standard 9-row Mars map (Tharsis / Hellas / Elysium): 61 hexes numbered 1–61
 * row-major, plus off-Mars colony slots from 62 up (no neighbors).
 */
public final class BoardLayout {
    private static final int[] ROW_LEN = {5, 6, 7, 8, 9, 8, 7, 6, 5};
    private static final int MAX_X = 8;
    private static final int MAX_Y = 8;
    private static final Map<Integer, int[]> XY = new LinkedHashMap<>();
    private static final Map<Integer, List<Integer>> NEIGHBORS = new LinkedHashMap<>();

    static {
        int id = 1;
        for (int row = 0; row < ROW_LEN.length; row++) {
            int len = ROW_LEN[row];
            int x0 = 9 - len;
            for (int i = 0; i < len; i++) {
                XY.put(id, new int[] {x0 + i, row});
                id++;
            }
        }
        for (int hex : XY.keySet()) {
            NEIGHBORS.put(hex, computeNeighbors(hex));
        }
    }

    private BoardLayout() {
    }

    public static boolean onMars(int hex) {
        return hex >= 1 && hex <= 61;
    }

    public static List<Integer> neighbors(int hex) {
        return NEIGHBORS.getOrDefault(hex, List.of());
    }

    private static List<Integer> computeNeighbors(int hex) {
        int[] xy = XY.get(hex);
        int x = xy[0];
        int y = xy[1];
        int middle = MAX_Y / 2;
        int leftX = x - 1;
        int rightX = x + 1;
        int topLeftX = x;
        int topRightX = x;
        int bottomLeftX = x;
        int bottomRightX = x;
        if (y < middle) {
            bottomLeftX--;
            topRightX++;
        } else if (y == middle) {
            bottomRightX++;
            topRightX++;
        } else {
            bottomRightX++;
            topLeftX--;
        }
        int[][] coords = {
                {topLeftX, y - 1},
                {topRightX, y - 1},
                {rightX, y},
                {bottomRightX, y + 1},
                {bottomLeftX, y + 1},
                {leftX, y},
        };
        List<Integer> out = new ArrayList<>();
        for (int[] c : coords) {
            if (c[0] < 0 || c[0] > MAX_X || c[1] < 0 || c[1] > MAX_Y) {
                continue;
            }
            Integer other = idAt(c[0], c[1]);
            if (other != null && other != hex) {
                out.add(other);
            }
        }
        return List.copyOf(out);
    }

    private static Integer idAt(int x, int y) {
        for (Map.Entry<Integer, int[]> e : XY.entrySet()) {
            if (e.getValue()[0] == x && e.getValue()[1] == y) {
                return e.getKey();
            }
        }
        return null;
    }
}
