package main.java.world;

import main.java.entities.BossEnemy;
import main.java.entities.Enemy;
import main.java.entities.EnemyType;
import main.java.entities.Player;

public class EnemySpawner {

    private final GameWorld world;
    private final Player player;

    private double enemySpawnTimer = 0.0;
    private final float baseEnemySpawnInterval = 0.9f;
    private float enemySpawnInterval = baseEnemySpawnInterval;
    
    // --- MÓDOSÍTOTT BOSS VÁLTOZÓK ---
    private double bossSpawnTimer = 0.0;
    
    // Az első boss 60 másodperc (1 perc) után jön
    private double bossSpawnInterval = 60.0; 
    
    // A többi boss 120 másodperc után jön
    private final double subsequentBossInterval = 120.0; 
    
    // Számláló a HP növeléséhez
    private int bossesSpawnedCount = 0;
    // --- MÓDOSÍTÁS VÉGE ---

    public EnemySpawner(GameWorld world, Player player) {
        this.world = world;
        this.player = player;
    }

    public void update(float deltaTime) {
        // 🔒 Ne spawnoljon normál enemy, ha van legalább 1 boss a pályán
        boolean bossPresent = world.getEnemies().stream().anyMatch(e -> e instanceof BossEnemy);
        if (bossPresent) {
            // Ha boss aktív, csak a boss timer frissüljön
            bossSpawnTimer += deltaTime;

            // Ha még kevesebb, mint 3 boss van, idővel jöhet új
            // --- MÓDOSÍTOTT BOSS LOGIKA (ha már van boss) ---
            if (bossSpawnTimer >= bossSpawnInterval) {
                
                // Ha ez volt az első boss (count=0), állítsuk át az időközt 120mp-re
                if (bossesSpawnedCount == 0) {
                    bossSpawnInterval = subsequentBossInterval;
                }
                
                bossSpawnTimer = 0.0; // Időzítő nullázása
                long activeBosses = world.getEnemies().stream().filter(e -> e instanceof BossEnemy).count();

                if (activeBosses < 3) {
                    double angle = Math.random() * Math.PI * 2.0;
                    float bossSpawnRadius = 400f + (float)(Math.random() * 400f);
                    float bx = (float)(player.x + Math.cos(angle) * bossSpawnRadius);
                    float by = (float)(player.y + Math.sin(angle) * bossSpawnRadius);
                    bx = Math.max(50, Math.min(world.worldWidth - 50, bx));
                    by = Math.max(50, Math.min(world.worldHeight - 50, by));

                    double roll = Math.random();
                    BossEnemy.BossType bossType;
                    if (roll < 0.33) bossType = BossEnemy.BossType.GHOST;
                    else if (roll < 0.66) bossType = BossEnemy.BossType.DEMON;
                    else bossType = BossEnemy.BossType.DRAGON;

                    BossEnemy boss = new BossEnemy(bx, by, EnemyType.TANK, world, bossType);
                    
                    // HP NÖVELÉS:
                    // 1. boss (count=0): 1.0 + (1 * 0.2) = 1.2 (+20%)
                    // 2. boss (count=1): 1.0 + (2 * 0.2) = 1.4 (+40% az alaphoz képest)
                    // 3. boss (count=2): 1.0 + (3 * 0.2) = 1.6 (+60% az alaphoz képest)
                    boss.maxHp *= (1.0f + ((bossesSpawnedCount + 1) * 0.2f));
                    boss.hp = boss.maxHp;
                    
                    bossesSpawnedCount++; // Növeljük a számlálót

                    world.getEnemies().add(boss);
                    System.out.println("Boss spawned! Total bosses so far: " + bossesSpawnedCount);

                    // 🧹 Minden kis ellenség eltűnik, csak a boss marad
                    world.getEnemies().removeIf(e -> !(e instanceof BossEnemy));
                }
            }
            // --- MÓDOSÍTÁS VÉGE ---
            return; // ⛔ Kilépünk, nem spawnolunk normál enemy-t
        }

        // --- NORMÁL ENEMY SPAWN (ha nincs boss) ---
        // (Ez a rész változatlan maradt)
        int minutesElapsed = (int) (world.elapsedTime / 60.0);
        int difficultyStages = minutesElapsed; // Percenként nehezedik (ahogy kérted)
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

        // --- BOSS TIMER FRISSÍTÉS (ha épp nincs boss) ---
        // --- MÓDOSÍTOTT BOSS LOGIKA (ha még nincs boss) ---
        bossSpawnTimer += deltaTime;
        if (bossSpawnTimer >= bossSpawnInterval) {
            
            // Ha ez volt az első boss (count=0), állítsuk át az időközt 120mp-re
            if (bossesSpawnedCount == 0) {
                bossSpawnInterval = subsequentBossInterval;
            }
            
            bossSpawnTimer = 0.0; // Időzítő nullázása
            long activeBosses = world.getEnemies().stream().filter(e -> e instanceof BossEnemy).count();

            if (activeBosses < 3) {
                double angle = Math.random() * Math.PI * 2.0;
                float bossSpawnRadius = 400f + (float)(Math.random() * 400f);
                float bx = (float)(player.x + Math.cos(angle) * bossSpawnRadius);
                float by = (float)(player.y + Math.sin(angle) * bossSpawnRadius);
                bx = Math.max(50, Math.min(world.worldWidth - 50, bx));
                by = Math.max(50, Math.min(world.worldHeight - 50, by));

                double roll = Math.random();
                BossEnemy.BossType bossType;
                if (roll < 0.33) bossType = BossEnemy.BossType.GHOST;
                else if (roll < 0.66) bossType = BossEnemy.BossType.DEMON;
                else bossType = BossEnemy.BossType.DRAGON;

                BossEnemy boss = new BossEnemy(bx, by, EnemyType.TANK, world, bossType);
                
                // HP NÖVELÉS:
                boss.maxHp *= (1.0f + ((bossesSpawnedCount + 1) * 0.2f));
                boss.hp = boss.maxHp;
                
                bossesSpawnedCount++; // Növeljük a számlálót

                world.getEnemies().add(boss);
                System.out.println("Boss spawned! Total bosses so far: " + bossesSpawnedCount);

                // 🧹 Minden kis ellenség eltűnik, csak a boss marad
                world.getEnemies().removeIf(e -> !(e instanceof BossEnemy));
            }
        }
        // --- MÓDOSÍTÁS VÉGE ---
    }
}