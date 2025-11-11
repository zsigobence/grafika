package main.java.world;

import main.java.entities.GameObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Egy térbeli rács az ütközésvizsgálat gyorsítására.
 * Az objektumokat cellákba szervezi, így csak a közeli objektumokat kell ellenőrizni.
 */
public class SpatialGrid {

    /**
     * Egy egyszerű rekord, ami egy rács-koordinátát reprezentál.
     * Használható HashMap kulcsként.
     */
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
            // Egyszerűbb és gyakran gyorsabb hash-kódolás
            // a nagy számok elkerülése érdekében
            return 31 * x + y;
        }
    }

    private final Map<CellCoord, List<GameObject>> grid;
    private final int cellSize;

    /**
     * Létrehoz egy új térbeli rácsot.
     * @param worldWidth A világ teljes szélessége (becsléshez)
     * @param worldHeight A világ teljes magassága (becsléshez)
     * @param cellSize Egy cella mérete pixelben (pl. 200)
     */
    public SpatialGrid(int worldWidth, int worldHeight, int cellSize) {
        this.cellSize = cellSize;
        // Kezdeti kapacitás beállítása a HashMap-nek a várható cellaszám alapján
        int initialCapacity = (worldWidth / cellSize + 1) * (worldHeight / cellSize + 1);
        this.grid = new HashMap<>(initialCapacity);
    }

    /**
     * Kiszámítja a cella X indexét egy világ-koordináta alapján.
     */
    private int getCellX(float x) {
        return (int) (x / cellSize);
    }

    /**
     * Kiszámítja a cella Y indexét egy világ-koordináta alapján.
     */
    private int getCellY(float y) {
        return (int) (y / cellSize);
    }

    /**
     * Hozzáad egy objektumot a rácshoz az aktuális pozíciója alapján.
     */
    public void insert(GameObject obj) {
        CellCoord key = new CellCoord(getCellX(obj.x), getCellY(obj.y));
        
        // computeIfAbsent: Ha a kulcs nem létezik, létrehoz egy új listát,
        // majd hozzáadja az objektumot.
        grid.computeIfAbsent(key, k -> new ArrayList<>()).add(obj);
    }

    /**
     * Visszaadja az összes objektumot, amely az adott objektum cellájában
     * VAGY a 8 szomszédos cella valamelyikében található.
     * Ez a lista tartalmazza magát az 'obj'-t is.
     */
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

    /**
     * Minden ciklus elején meg kell hívni, hogy kiürítse a rácsot.
     */
    public void clear() {
        grid.clear();
    }
}