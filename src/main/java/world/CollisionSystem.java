package main.java.world;

import main.java.audio.SoundManager;
import main.java.entities.*;

import java.util.Iterator;
import java.util.List;

public class CollisionSystem {

    private final GameWorld world;
    private final Player player;
    private final LevelingSystem levelingSystem;
    private final SpatialGrid grid; // <-- ÚJ: A rács referencia

    /**
     * Konstruktor frissítve a SpatialGrid fogadására.
     */
    public CollisionSystem(GameWorld world, Player player, LevelingSystem levelingSystem, SpatialGrid grid) {
        this.world = world;
        this.player = player;
        this.levelingSystem = levelingSystem;
        this.grid = grid;
    }

    /**
     * A GameWorld update ciklusából hívódik.
     * Most két fő részre bontva a tisztaság kedvéért.
     */
    public void checkCollisions() {
        // A játékos ütközéseit külön kezeljük, mivel ő a "központ"
        checkPlayerCollisions();
        
        // Az ellenségek golyókkal való ütközéseit itt kezeljük
        checkEnemyVsBulletCollisions();
    }

    /**
     * Ellenőrzi a JÁTÉKOS ütközéseit a közeli (grid alapú) entitásokkal.
     */
    private void checkPlayerCollisions() {
        if (player.isDead()) return;

        // Lekérjük a Játékos 9 cellás környezetében lévő összes objektumot
        List<GameObject> neighbors = grid.getPotentialColliders(player);
        
        // Iterátor használata, hogy eltávolíthassuk az orbs-okat/golyókat
        Iterator<GameObject> iter = neighbors.iterator();
        while (iter.hasNext()) {
            GameObject obj = iter.next();

            // 1. Játékos-Ellenség ütközés
            if (obj instanceof Enemy enemy) {
                // (Kihagyjuk a "saját magával" való ütközést, bár a Player nem Enemy)
                if (checkCollision(player, enemy)) {
                    handlePlayerEnemyCollision(enemy);
                    if (player.isDead()) return; // Játékos meghalt, vége a ciklusnak
                }
            }
            
            // 2. Játékos-EllenségGolyó ütközés
            else if (obj instanceof Bullet bullet && bullet.owner == Bullet.Owner.ENEMY) {
                if (checkCollision(player, bullet)) {
                    player.takeDamage(1);
                    SoundManager.playOverlap("damage");
                    world.getBullets().remove(bullet); // Eltávolítjuk a golyót a fő listából
                    if (player.isDead()) {
                         System.out.println("Game Over!");
                         world.setGameOver(true);
                         return;
                    }
                }
            }
            
            // 3. Játékos-XPOrb ütközés
            else if (obj instanceof XPOrb orb) {
                if (checkCollision(player, orb)) {
                    levelingSystem.onPlayerCollectedOrb(orb);
                    world.getXPOrbs().remove(orb); // Eltávolítjuk az orbot a fő listából
                }
            }
        }
    }

    /**
     * Kezeli a Játékos és Ellenség ütközését.
     */
    private void handlePlayerEnemyCollision(Enemy enemy) {
        if (enemy instanceof BossEnemy) {
            player.takeDamage(5); // Boss nagyot sebez
            System.out.println("Player was slain by the Boss!");
            SoundManager.playOverlap("damage");
            world.setGameOver(true);
        } else {
            player.takeDamage(1); // Normál enemy keveset sebez és meghal
            levelingSystem.onEnemyKilled(enemy);
            world.getEnemies().remove(enemy); // Eltávolítjuk az ellenséget
        }

        if (player.isDead()) {
            System.out.println("Game Over!");
            world.setGameOver(true);
        }
    }

    /**
     * Ellenőrzi az ELLENSÉGEK ütközéseit a JÁTÉKOS GOLYÓIVAL.
     */
    private void checkEnemyVsBulletCollisions() {
        Iterator<Enemy> enemyIterator = world.getEnemies().iterator();
        while (enemyIterator.hasNext()) {
            Enemy enemy = enemyIterator.next();
            
            List<GameObject> neighbors = grid.getPotentialColliders(enemy);
            
            Iterator<GameObject> neighborIter = neighbors.iterator();
            while (neighborIter.hasNext()) {
                GameObject obj = neighborIter.next();
                
                // Csak a játékos golyóit nézzük
                if (obj instanceof Bullet bullet && bullet.owner == Bullet.Owner.PLAYER) {
                    
                    if (checkCollision(enemy, bullet)) {

                        if (bullet instanceof LaserBullet laser) {
                            // 1. Lézer speciális logikája
                            laser.onHit(enemy); // Ez csökkenti a 'pierce' értékét
                            
                            if (laser.pierce <= 0) {
                                // Ha elfogyott az áthatolás, töröljük a lézert
                                world.getBullets().remove(bullet);
                            }
                            
                        } else {
                            // 2. Normál golyó logikája
                            enemy.takeDamage(player.damage); // Normál golyó a játékos sebzését használja
                            SoundManager.playOverlap("damage");
                            world.getBullets().remove(bullet); // Normál golyó azonnal törlődik
                        }

                        if (enemy.isDead()) {
                            levelingSystem.onEnemyKilled(enemy);
                            enemyIterator.remove(); 
                            break; 
                        }

                        if (!world.getBullets().contains(bullet)) {
                            break; 
                        }
                    }
                }
            }
        }
    }
    
    /**
     * AZ XP ORBOK JÁTÉKOSSAL VALÓ ÜTKÖZÉSE ÁTKERÜLT a checkPlayerCollisions-be,
     * így az itteni külön checkXPOrbCollisions metódus TÖRÖLHETŐ.
     */

    /**
     * Segédfüggvény az ütközésvizsgálathoz (kör alapú).
     */
    private boolean checkCollision(GameObject a, GameObject b) {
        float dx = a.x - b.x;
        float dy = a.y - b.y;
        float distSq = (dx * dx + dy * dy); // Négyzetes távolság
        float radiusA = a.size / 2f;
        float radiusB = b.size / 2f;
        float sumRadiiSq = (radiusA + radiusB) * (radiusA + radiusB); // Négyzetes sugárösszeg
        
        // Gyorsabb, mint a Math.sqrt() használata
        return distSq < sumRadiiSq;
    }
}