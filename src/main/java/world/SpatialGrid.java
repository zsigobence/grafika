package main.java.world;

import main.java.entities.GameObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Térbeli rács az ütközésvizsgálat optimalizálásához.
 * Csak a szomszédos cellákban lévő objektumokat kell ellenőrizni.
 */
public class SpatialGrid {

    // Rekord a cellakoordináták tárolására (HashMap kulcs)
    private record CellCoord(int x, int y) {
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            CellCoord cellCoord = (CellCoord) o;
            return x == cellCoord.x && y == cellCoord.y;
        }

        @Override
        public int hashCode() {
            return 31 * x + y;
        }
    }

    private final Map<CellCoord, List<GameObject>> grid;
    private final int cellSize;

    public SpatialGrid(int worldWidth, int worldHeight, int cellSize) {
        this.cellSize = cellSize;
        int initialCapacity = (worldWidth / cellSize + 1) * (worldHeight / cellSize + 1);
        this.grid = new HashMap<>(initialCapacity);
    }

    private int getCellX(float x) {
        return (int) (x / cellSize);
    }

    private int getCellY(float y) {
        return (int) (y / cellSize);
    }

    // Objektum beillesztése a megfelelő cellába
    public void insert(GameObject obj) {
        CellCoord key = new CellCoord(getCellX(obj.x), getCellY(obj.y));
        grid.computeIfAbsent(key, k -> new ArrayList<>()).add(obj);
    }

    // Potenciális ütközők lekérése (saját + szomszédos cellák)
    public List<GameObject> getPotentialColliders(GameObject obj) {
        List<GameObject> potentialColliders = new ArrayList<>();
        int centerX = getCellX(obj.x);
        int centerY = getCellY(obj.y);

        for (int x = centerX - 1; x <= centerX + 1; x++) {
            for (int y = centerY - 1; y <= centerY + 1; y++) {
                List<GameObject> cellContent = grid.get(new CellCoord(x, y));
                if (cellContent != null) {
                    potentialColliders.addAll(cellContent);
                }
            }
        }
        return potentialColliders;
    }

    public void clear() {
        grid.clear();
    }
}