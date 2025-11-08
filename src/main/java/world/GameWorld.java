package main.java.world;

import main.java.audio.SoundManager;
import main.java.entities.*;
import main.java.systems.Gadget;
import main.java.systems.GadgetSystem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;


public class GameWorld {
    public int worldWidth = 2000;
    public int worldHeight = 2000;

    private Player player;
    private List<Bullet> bullets = new ArrayList<>();
    private List<Enemy> enemies = new ArrayList<>();
    private List<XPOrb> xpOrbs = new ArrayList<>();
    private List<FloatingText> floatingTexts = new ArrayList<>();

    public int score = 0;
    public int xp = 0;
    public int level = 1;
    public int xpToNext = 50;
    
    public double elapsedTime = 0.0;
    private double enemySpawnTimer = 0.0;
    private float baseEnemySpawnInterval = 0.9f;
    private float enemySpawnInterval = baseEnemySpawnInterval;
    
    private double bossSpawnTimer = 0.0;
    private double bossSpawnInterval = 60.0; // új boss minden 60 mp-ben

    private List<Gadget> gadgets = new ArrayList<>();
    public List<Gadget> availableGadgets = new ArrayList<>();
    public boolean levelUpMenuActive = false;
    private GadgetSystem gadgetSystem;
    private boolean gameOver = false;


    public void init() {
        player = new Player(worldWidth / 2.0f, worldHeight / 2.0f, 10, 1, 250f);
        initGadgets();
        recomputePlayerStats();
        gadgetSystem = new GadgetSystem(this, player);
        loadSounds();
    }
    
    public void update(float deltaTime) {
        if (levelUpMenuActive) return;

        elapsedTime += deltaTime;
        player.update(deltaTime, enemies);
        if (player.isReadyToShoot()) {
            spawnPlayerBullets();
        }

        updateBullets(deltaTime);
        spawnEnemies(deltaTime);
        updateEnemies(deltaTime);
        updateXPOrbs(deltaTime);
        updateFloatingTexts(deltaTime);
        gadgetSystem.update(deltaTime);
    }
    
    // Getter metódusok, hogy a Renderer és más osztályok hozzáférjenek az adatokhoz
    public Player getPlayer() { return player; }
    public List<Bullet> getBullets() { return bullets; }
    public List<Enemy> getEnemies() { return enemies; }
    public List<XPOrb> getXPOrbs() { return xpOrbs; }
    public List<FloatingText> getFloatingTexts() { return floatingTexts; }
    public List<Gadget> getGadgets() { return gadgets; }
    public GadgetSystem getGadgetSystem() { return gadgetSystem; }
    public boolean isGameOver() { return gameOver; }

    private void updateBullets(float deltaTime) {
        bullets.removeIf(b -> {
            b.update(deltaTime);
            return b.x < -50 || b.x > worldWidth + 50 || b.y < -50 || b.y > worldHeight + 50;
        });
    }

    private void spawnEnemies(float deltaTime) {
        int minutesElapsed = (int) (elapsedTime / 60.0);
        int difficultyStages = minutesElapsed / 3;
        float spawnMultiplier = Math.max(0.25f, 1.0f - 0.15f * minutesElapsed);
        enemySpawnInterval = baseEnemySpawnInterval * spawnMultiplier;
        enemySpawnTimer += deltaTime;
        float difficultyMultiplier = 1.0f + 0.15f * difficultyStages;
        float spawnRadius = 800 * 0.8f + 200.0f; 

        if (enemySpawnTimer > enemySpawnInterval) {
            enemySpawnTimer = 0.0;
            double angle = Math.random() * Math.PI * 2.0;
            float ex = (float) (player.x + Math.cos(angle) * spawnRadius);
            float ey = (float) (player.y + Math.sin(angle) * spawnRadius);
            ex = Math.max(20, Math.min(worldWidth - 20, ex));
            ey = Math.max(20, Math.min(worldHeight - 20, ey));

            EnemyType type;
            double r = Math.random();
            if (r < 0.6) type = EnemyType.BASIC;
            else if (r < 0.85) type = EnemyType.FAST;
            else type = EnemyType.TANK;

            Enemy spawned = new Enemy(ex, ey, 0, 0, type); // Velocity is set in enemy's update
            spawned.maxHp = Math.max(1, Math.round(spawned.maxHp * difficultyMultiplier));
            spawned.hp = spawned.maxHp;
            enemies.add(spawned);
        }
        bossSpawnTimer += deltaTime;

	     // Boss spawn logika: új boss minden bossSpawnInterval idő után
	     if (bossSpawnTimer >= bossSpawnInterval) {
	         bossSpawnTimer = 0.0;
	         long activeBosses = enemies.stream().filter(e -> e instanceof BossEnemy).count();
	         if (activeBosses < 3) {
	             // Spawn pozíció (véletlenszerű körben a játékos körül)
	             double angle = Math.random() * Math.PI * 2.0;
	             float bossSpawnRadius = 400f + (float)(Math.random() * 400f);
	             float bx = (float)(player.x + Math.cos(angle) * bossSpawnRadius);
	             float by = (float)(player.y + Math.sin(angle) * bossSpawnRadius);
	
	             bx = Math.max(50, Math.min(worldWidth - 50, bx));
	             by = Math.max(50, Math.min(worldHeight - 50, by));
	
	             double roll = Math.random();
	             BossEnemy.BossType bossType;

	             if (roll < 0.33) bossType = BossEnemy.BossType.GHOST;
	             else if (roll < 0.66) bossType = BossEnemy.BossType.DEMON;
	             else bossType = BossEnemy.BossType.DRAGON;

	             BossEnemy boss = new BossEnemy(bx, by, EnemyType.TANK, this, bossType);
	
	             // Kicsit erősebbek legyenek az újabb bossok
	             int bossCount = (int)(elapsedTime / bossSpawnInterval);
	             boss.maxHp *= 1f + 0.2f * bossCount; // enyhébb növekedés
	             boss.hp = boss.maxHp;
	
	             enemies.add(boss);
	             System.out.println("Boss spawned! Total bosses so far: " + (bossCount + 1));
	         }
	     }

    }
    
    
    private void updateEnemies(float deltaTime) {
        Iterator<Enemy> enemyIterator = enemies.iterator();
        while (enemyIterator.hasNext()) {
            Enemy enemy = enemyIterator.next();
            enemy.update(deltaTime, player);

            // Enemy-Bullet collision
            Iterator<Bullet> bulletIter = bullets.iterator();
            while (bulletIter.hasNext()) {
                Bullet b = bulletIter.next();
                if (b.owner == Bullet.Owner.PLAYER && checkCollision(enemy, b)) {
                    enemy.takeDamage(player.damage);
                    SoundManager.playOverlap("damage");
                    bulletIter.remove();

                    if (enemy.isDead()) {
                        handleEnemyDeath(enemy);
                        enemyIterator.remove();
                        break;
                    }
                }
            }

            if (enemy.isDead()) continue;

            // Enemy-Player collision
            if (checkCollision(enemy, player)) {
                if (enemy instanceof BossEnemy) {
                    player.takeDamage(5);
                    System.out.println("Player was slain by the Boss!");
                    SoundManager.playOverlap("damage");
                    gameOver = true;
                } else {
                    player.takeDamage(1);
                    handleEnemyDeath(enemy);
                    enemyIterator.remove();
                }

                if (player.isDead()) {
                    System.out.println("Game Over!");
                    gameOver = true;
                }
            }
        }

        // --- Enemy bullets hitting player ---
        Iterator<Bullet> enemyBullets = bullets.iterator();
        while (enemyBullets.hasNext()) {
            Bullet b = enemyBullets.next();
            if (b.owner == Bullet.Owner.ENEMY && checkCollision(player, b)) {
                player.takeDamage(1);
                SoundManager.playOverlap("damage");
                enemyBullets.remove();

                if (player.isDead()) {
                    System.out.println("Game Over!");
                    gameOver = true;
                    break;
                }
            }
        }
    }

    
    private void handleEnemyDeath(Enemy enemy) {
        xpOrbs.add(new XPOrb(enemy.x, enemy.y, enemy.getXp()));
        score += enemy.type == EnemyType.TANK ? 30 : 10;
        
        int lsLevel = getGadgetLevel("Life Steal");
        if (lsLevel > 0) {
            float chance = lsLevel * 0.03f;
            if (Math.random() < chance) {
                player.heal(1);
                floatingTexts.add(new FloatingText(player.x, player.y - 40, "+HP", 1.0f, -40f, 0.3f, 1f, 0.3f));
            }
        }
    }
    
    public void killEnemy(Enemy enemy) {
        handleEnemyDeath(enemy);
        enemies.remove(enemy);
    }


    private void updateXPOrbs(float deltaTime) {
        Iterator<XPOrb> xpIter = xpOrbs.iterator();
        while (xpIter.hasNext()) {
            XPOrb orb = xpIter.next();
            orb.update(deltaTime, player);

            if (orb.x < -100 || orb.x > worldWidth + 100 || orb.y < -100 || orb.y > worldHeight + 100) {
                xpIter.remove();
                continue;
            }

            if (checkCollision(orb, player)) {
                xp += orb.value;
                floatingTexts.add(new FloatingText(player.x, player.y - player.size, "+" + orb.value, 1.2f, -40.0f, 1.0f, 1.0f, 0.2f));
                SoundManager.playOverlap("xp");

                if (xp >= xpToNext) {
                    levelUp();
                }
                xpIter.remove();
            }
        }
    }
    
    private void levelUp() {
        xp -= xpToNext;
        level++;
        xpToNext = calcXpForLevel(level);
        floatingTexts.add(new FloatingText(player.x, player.y - player.size - 20, "Level Up!", 1.6f, -70.0f, 1.0f, 0.8f, 0.0f));
        levelUpMenuActive = true;
        SoundManager.play("levelup");
        generateLevelUpOptions();
    }
    
    private void updateFloatingTexts(float deltaTime) {
        floatingTexts.removeIf(ft -> {
            ft.update(deltaTime);
            return ft.life <= 0f;
        });
    }
    
    // ... (rest of the logic methods: initGadgets, recomputePlayerStats, etc.)
    // ... (This includes calcXpForLevel, getGadgetLevel, spawnPlayerBullets, checkCollision)

    public void selectGadget(Gadget gadget) {
        gadget.levelUp();
        recomputePlayerStats();
        levelUpMenuActive = false;
        floatingTexts.add(new FloatingText(player.x, player.y, gadget.name + " +1", 1.5f, -50f, 0f, 1f, 0f));
    }

    private void spawnPlayerBullets() {
        Enemy nearest = player.findNearestEnemy(enemies);
        if (nearest != null) {
            float dx = nearest.x - player.x;
            float dy = nearest.y - player.y;
            float len = (float) Math.sqrt(dx * dx + dy * dy);
            if (len > 0 && len <= player.autoShootRange) {
                float bSpeed = 700.0f;
                int count = getGadgetLevel("Multi Attack") + 1;
                float baseAngle = (float) Math.atan2(dy, dx);
                float spread = (count == 1) ? 0f : (float) Math.toRadians(12f);
    
                for (int i = 0; i < count; i++) {
                     float angle = baseAngle + (i - (count - 1) / 2.0f) * spread;
                     bullets.add(new Bullet(player.x, player.y, (float)Math.cos(angle) * bSpeed, (float)Math.sin(angle) * bSpeed));
                }
                SoundManager.playOverlap("shoot");
            }
        }
        player.shootCooldown = 0.75f * getAttackSpeedMultiplier();
    }
    
    private boolean checkCollision(GameObject a, GameObject b) {
        float dx = a.x - b.x;
        float dy = a.y - b.y;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        return dist < (a.size / 2 + b.size / 2);
    }
    
    private int calcXpForLevel(int lvl) {
        double val = 100.0 * Math.pow(1.45, Math.max(0, lvl - 1));
        return Math.max(20, (int) Math.round(val));
    }
    
    private void generateLevelUpOptions() {
        availableGadgets.clear();
        List<Gadget> nonMaxGadgets = new ArrayList<>();
        for (Gadget gadget : gadgets) {
            if (gadget.level < gadget.maxLevel) {
                nonMaxGadgets.add(gadget);
            }
        }
        Collections.shuffle(nonMaxGadgets);
        int count = Math.min(3, nonMaxGadgets.size());
        for (int i = 0; i < count; i++) {
            availableGadgets.add(nonMaxGadgets.get(i));
        }
    }
    
    private void recomputePlayerStats() {
        player.damage = player.baseDamage + getGadgetLevel("Attack Damage");
        int newMaxHp = player.baseMaxHp + getGadgetLevel("Max HP");
        if (newMaxHp != player.maxHp) {
            float percent = (float)player.hp / (float)player.maxHp;
            player.maxHp = newMaxHp;
            player.hp = Math.min(player.maxHp, Math.max(1, Math.round(player.maxHp * percent)));
        }
        player.moveSpeed = player.baseMoveSpeed * (1.0f + 0.1f * getGadgetLevel("Movement Speed"));
    }

    public int getGadgetLevel(String name) {
        for (Gadget g : gadgets) if (g.name.equals(name)) return g.level;
        return 0;
    }
    
    private float getAttackSpeedMultiplier() {
        int lvl = getGadgetLevel("Attack Speed");
        return 1f / (1f + 0.2f * lvl);
    }
    
    private void initGadgets() {
        gadgets.add(new Gadget("Attack Damage", "Increases bullet damage by 1.", 5));
        gadgets.add(new Gadget("Attack Speed", "Increases attack speed by 20%.", 5));
        gadgets.add(new Gadget("Max HP", "Increases max health by 1.", 5));
        gadgets.add(new Gadget("Movement Speed", "Increases movement speed by 10%.", 5));
        gadgets.add(new Gadget("Multi Attack", "Shoots an additional projectile.", 3));
        gadgets.add(new Gadget("Life Steal", "3% chance on kill to restore 1 HP.", 3));
        gadgets.add(new Gadget("Orbit Blade", "A blade circles you, damaging enemies.", 5));
        gadgets.add(new Gadget("Laser Beam", "Fires a powerful, piercing laser.", 5));
    }
    
    private void loadSounds() {
        SoundManager.loadSound("shoot", "src/main/sounds/shoot.ogg");
        SoundManager.setVolume("shoot", 0.3f);
        SoundManager.loadSound("xp", "src/main/sounds/xp.ogg");
        SoundManager.setVolume("xp", 0.4f);
        SoundManager.loadSound("damage", "src/main/sounds/damage.ogg");
        SoundManager.setVolume("damage", 0.4f);
        SoundManager.loadSound("laser", "src/main/sounds/laser.ogg");
        SoundManager.setVolume("laser", 0.4f);
        SoundManager.loadSound("flying-blade", "src/main/sounds/flying-blade.ogg");
        SoundManager.setVolume("flying-blade", 0.4f);
        SoundManager.loadSound("levelup", "src/main/sounds/levelup.ogg");
        SoundManager.setVolume("levelup", 0.5f);
        SoundManager.loadSound("bgm", "src/main/sounds/bgm.ogg");
        SoundManager.setVolume("bgm", 1.0f);
        SoundManager.loop("bgm");
    }
}