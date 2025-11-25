package main.java.world;

import main.java.entities.BossEnemy;
import main.java.entities.BossType;
import main.java.entities.Enemy;
import main.java.entities.EnemyType;
import main.java.entities.Player;

public class EnemySpawner {

    private final GameWorld world;
    private final Player player;

    private double enemySpawnTimer = 0.0;
    private final float baseEnemySpawnInterval = 0.9f;
    private float enemySpawnInterval = baseEnemySpawnInterval;
    
    private double bossSpawnTimer = 0.0;
    
    private double bossSpawnInterval = 60.0; 
    
    private final double subsequentBossInterval = 120.0; 
    
    private int bossesSpawnedCount = 0;

    public EnemySpawner(GameWorld world, Player player) {
        this.world = world;
        this.player = player;
    }

    public void update(float deltaTime) {
        // Boss logika: ha van boss, nem jön más
        boolean bossPresent = world.getEnemies().stream().anyMatch(e -> e instanceof BossEnemy);
        if (bossPresent) {
            bossSpawnTimer += deltaTime;

            if (bossSpawnTimer >= bossSpawnInterval) {
                
                if (bossesSpawnedCount == 0) {
                    bossSpawnInterval = subsequentBossInterval;
                }
                
                bossSpawnTimer = 0.0; 
                long activeBosses = world.getEnemies().stream().filter(e -> e instanceof BossEnemy).count();

                if (activeBosses < 3) {
                    double angle = Math.random() * Math.PI * 2.0;
                    float bossSpawnRadius = 400f + (float)(Math.random() * 400f);
                    float bx = (float)(player.x + Math.cos(angle) * bossSpawnRadius);
                    float by = (float)(player.y + Math.sin(angle) * bossSpawnRadius);
                    bx = Math.max(50, Math.min(world.worldWidth - 50, bx));
                    by = Math.max(50, Math.min(world.worldHeight - 50, by));

                    double roll = Math.random();
                    BossType bossType;
                    if (roll < 0.33) bossType = BossType.GHOST;
                    else if (roll < 0.66) bossType = BossType.DEMON;
                    else bossType = BossType.DRAGON;

                    BossEnemy boss = new BossEnemy(bx, by, EnemyType.TANK, world, bossType);
                    
                    // Boss életerő növelése minden megjelenéskor
                    boss.maxHp *= (1.0f + ((bossesSpawnedCount + 1) * 0.2f));
                    boss.hp = boss.maxHp;
                    
                    bossesSpawnedCount++; 

                    world.getEnemies().add(boss);
                    System.out.println("Boss spawned! Total bosses so far: " + bossesSpawnedCount);

                    // Kis ellenségek törlése
                    world.getEnemies().removeIf(e -> !(e instanceof BossEnemy));
                }
            }
            return; 
        }

        // Normál ellenség spawn logika
        int minutesElapsed = (int) (world.elapsedTime / 60.0);
        int difficultyStages = minutesElapsed; 
        float spawnMultiplier = Math.max(0.25f, 1.0f - 0.25f * minutesElapsed);
        enemySpawnInterval = baseEnemySpawnInterval * spawnMultiplier;
        enemySpawnTimer += deltaTime;
        float difficultyMultiplier = 1.0f + 0.15f * difficultyStages;
        float spawnRadius = 800 * 0.8f + 200.0f;

        if (enemySpawnTimer > enemySpawnInterval) {
            enemySpawnTimer = 0.0;
            double angle = Math.random() * Math.PI * 2.0;
            float ex = (float) (player.x + Math.cos(angle) * spawnRadius);
            float ey = (float) (player.y + Math.sin(angle) * spawnRadius);
            ex = Math.max(20, Math.min(world.worldWidth - 20, ex));
            ey = Math.max(20, Math.min(world.worldHeight - 20, ey));

            EnemyType type;
            double r = Math.random();
            if (r < 0.45) type = EnemyType.BASIC;
            else if (r < 0.70) type = EnemyType.RANGED;
            else if (r < 0.90) type = EnemyType.FAST;
            else type = EnemyType.TANK;

            Enemy spawned = new Enemy(ex, ey, 0, 0, type, world); 
            
            spawned.maxHp = Math.max(1, Math.round(spawned.maxHp * difficultyMultiplier));
            spawned.hp = spawned.maxHp;
            world.getEnemies().add(spawned);
        }

        // Boss időzítő frissítése
        bossSpawnTimer += deltaTime;
        if (bossSpawnTimer >= bossSpawnInterval) {
            
            if (bossesSpawnedCount == 0) {
                bossSpawnInterval = subsequentBossInterval;
            }
            
            bossSpawnTimer = 0.0; 
            long activeBosses = world.getEnemies().stream().filter(e -> e instanceof BossEnemy).count();

            if (activeBosses < 3) {
                double angle = Math.random() * Math.PI * 2.0;
                float bossSpawnRadius = 400f + (float)(Math.random() * 400f);
                float bx = (float)(player.x + Math.cos(angle) * bossSpawnRadius);
                float by = (float)(player.y + Math.sin(angle) * bossSpawnRadius);
                bx = Math.max(50, Math.min(world.worldWidth - 50, bx));
                by = Math.max(50, Math.min(world.worldHeight - 50, by));

                double roll = Math.random();
                BossType bossType;
                if (roll < 0.33) bossType = BossType.GHOST;
                else if (roll < 0.66) bossType = BossType.DEMON;
                else bossType = BossType.DRAGON;

                BossEnemy boss = new BossEnemy(bx, by, EnemyType.TANK, world, bossType);
                
                boss.maxHp *= (1.0f + ((bossesSpawnedCount + 1) * 0.2f));
                boss.hp = boss.maxHp;
                
                bossesSpawnedCount++; 

                world.getEnemies().add(boss);
                System.out.println("Boss spawned! Total bosses so far: " + bossesSpawnedCount);

                world.getEnemies().removeIf(e -> !(e instanceof BossEnemy));
            }
        }
    }
}