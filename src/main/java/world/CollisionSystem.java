package main.java.world;

import main.java.audio.SoundManager;
import main.java.entities.*;

import java.util.Iterator;
import java.util.List;

public class CollisionSystem {

    private final GameWorld world;
    private final Player player;
    private final LevelingSystem levelingSystem;
    private final SpatialGrid grid;

    public CollisionSystem(GameWorld world, Player player, LevelingSystem levelingSystem, SpatialGrid grid) {
        this.world = world;
        this.player = player;
        this.levelingSystem = levelingSystem;
        this.grid = grid;
    }

    public void checkCollisions() {
        checkPlayerCollisions();
        checkEnemyVsBulletCollisions();
    }

    private void checkPlayerCollisions() {
        if (player.isDead()) return;

        // Potenciális ütközők lekérése a rácsból
        List<GameObject> neighbors = grid.getPotentialColliders(player);
        
        Iterator<GameObject> iter = neighbors.iterator();
        while (iter.hasNext()) {
            GameObject obj = iter.next();

            // Játékos-Ellenség ütközés
            if (obj instanceof Enemy enemy) {
                if (checkCollision(player, enemy)) {
                    handlePlayerEnemyCollision(enemy);
                    if (player.isDead()) return; 
                }
            }
            
            // Játékos-EllenségGolyó ütközés
            else if (obj instanceof Bullet bullet && bullet.owner == Bullet.Owner.ENEMY) {
                if (checkCollision(player, bullet)) {
                    player.takeDamage(1);
                    SoundManager.playOverlap("damage");
                    world.getBullets().remove(bullet); 
                    if (player.isDead()) {
                         System.out.println("Game Over!");
                         world.setGameOver(true);
                         return;
                    }
                }
            }
            
            // Játékos-XPOrb ütközés
            else if (obj instanceof XPOrb orb) {
                if (checkCollision(player, orb)) {
                    levelingSystem.onPlayerCollectedOrb(orb);
                    world.getXPOrbs().remove(orb); 
                }
            }
        }
    }

    private void handlePlayerEnemyCollision(Enemy enemy) {
        if (enemy instanceof BossEnemy) {
            player.takeDamage(5); 
            System.out.println("Player was slain by the Boss!");
            SoundManager.playOverlap("damage");
            world.setGameOver(true);
        } else {
            player.takeDamage(1); 
            levelingSystem.onEnemyKilled(enemy);
            world.getEnemies().remove(enemy); 
        }

        if (player.isDead()) {
            System.out.println("Game Over!");
            world.setGameOver(true);
        }
    }

    private void checkEnemyVsBulletCollisions() {
        Iterator<Enemy> enemyIterator = world.getEnemies().iterator();
        while (enemyIterator.hasNext()) {
            Enemy enemy = enemyIterator.next();
            
            List<GameObject> neighbors = grid.getPotentialColliders(enemy);
            
            Iterator<GameObject> neighborIter = neighbors.iterator();
            while (neighborIter.hasNext()) {
                GameObject obj = neighborIter.next();
                
                if (obj instanceof Bullet bullet && bullet.owner == Bullet.Owner.PLAYER) {
                    
                    if (checkCollision(enemy, bullet)) {

                        if (bullet instanceof LaserBullet laser) {
                            laser.onHit(enemy); 
                            
                            if (laser.pierce <= 0) {
                                world.getBullets().remove(bullet);
                            }
                            
                        } else {
                            enemy.takeDamage(player.damage); 
                            SoundManager.playOverlap("damage");
                            world.getBullets().remove(bullet); 
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
    
    private boolean checkCollision(GameObject a, GameObject b) {
        float dx = a.x - b.x;
        float dy = a.y - b.y;
        float distSq = (dx * dx + dy * dy); 
        float radiusA = a.size / 2f;
        float radiusB = b.size / 2f;
        float sumRadiiSq = (radiusA + radiusB) * (radiusA + radiusB); 
        
        return distSq < sumRadiiSq;
    }
}