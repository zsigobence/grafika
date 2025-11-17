package main.java.world;

import main.java.audio.SoundManager;
import main.java.entities.*;
import main.java.systems.Gadget;
import main.java.systems.GadgetSystem;

import java.util.ArrayList;
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
    
    private List<Gadget> gadgets = new ArrayList<>();
    public List<Gadget> availableGadgets = new ArrayList<>();
    public boolean levelUpMenuActive = false;
    private GadgetSystem gadgetSystem;
    private boolean gameOver = false;
    private boolean isPaused = false;
    public int pendingLevelUps = 0;
    
    private List<VisualEffect> visualEffects = new ArrayList<>();
    public List<VisualEffect> getVisualEffects() { return visualEffects; }

    // --- RENDSZEREK ---
    private EnemySpawner enemySpawner;
    private CollisionSystem collisionSystem;
    private LevelingSystem levelingSystem;
    private SpatialGrid spatialGrid; // <-- ÚJ: A rács referencia
    
    // Javasolt cellaméret (az ellenségek méretéhez igazítva)
    private static final int GRID_CELL_SIZE = 200;
    
    
    /**
     * Visszaállítja a játékvilágot a kezdőállapotba.
     * Nem tölti újra a hangokat, de minden mást igen.
     */
    public void reset() {
        // Listák kiürítése
        bullets.clear();
        enemies.clear();
        xpOrbs.clear();
        floatingTexts.clear();
        visualEffects.clear();
        gadgets.clear();
        availableGadgets.clear();
        
        // Statisztikák nullázása
        score = 0;
        xp = 0;
        level = 1;
        xpToNext = 50;
        elapsedTime = 0.0;
        pendingLevelUps = 0;
        
        // Állapotjelzők visszaállítása
        levelUpMenuActive = false;
        gameOver = false;
        isPaused = false;
        
        // Játékos és rendszerek újrakészítése
        player = new Player(worldWidth / 2.0f, worldHeight / 2.0f, 10, 1, 250f);
        initGadgets(); // Ez újratölti az alap gadgeteket
        recomputePlayerStats();
        
        // 1. Rács újrakészítése
        spatialGrid = new SpatialGrid(worldWidth, worldHeight, GRID_CELL_SIZE);
        
        // 2. Többi rendszer újrakészítése
        enemySpawner = new EnemySpawner(this, player);
        levelingSystem = new LevelingSystem(this, player);
        collisionSystem = new CollisionSystem(this, player, levelingSystem, spatialGrid);
        gadgetSystem = new GadgetSystem(this, player);
    }


    public void init() {
        // Először hívjuk az új reset metódust, ami beállít mindent
        reset();
        
        // A hangokat elég egyszer betölteni a játék indulásakor
        loadSounds();
    }
    
    public void update(float deltaTime) {
    	if (levelUpMenuActive || isPaused() || isGameOver()) return;

        elapsedTime += deltaTime;
        
        // 1. Játékos és Gadgetek frissítése (Input alapján)
        player.update(deltaTime, enemies);
        if (player.isReadyToShoot()) {
            spawnPlayerBullets();
        }
        gadgetSystem.update(deltaTime);

        // 2. Entitások mozgatása és logikája (ÜTKÖZÉS NÉLKÜL)
        updateBullets(deltaTime);
        updateEnemies(deltaTime);
        updateXPOrbs(deltaTime); // Ez már csak mozgatja az orbokat
        updateFloatingTexts(deltaTime);
        
        // 3. Spawner futtatása (Új entitásokat hoz létre)
        enemySpawner.update(deltaTime);
        
        // 4. ÚJ LÉPÉS: Rács feltöltése az aktuális pozíciókkal
        spatialGrid.clear();
        spatialGrid.insert(player);
        for (Enemy e : enemies) {
            spatialGrid.insert(e);
        }
        for (Bullet b : bullets) {
            spatialGrid.insert(b);
        }
        for (XPOrb o : xpOrbs) {
            spatialGrid.insert(o);
        }
        
        // 5. Ütközések ellenőrzése (Ez már a gyors, rács-alapú rendszert használja)
        collisionSystem.checkCollisions();
        
        updateVisualEffects(deltaTime);
    }
    
    private void updateVisualEffects(float dt) {
        visualEffects.removeIf(e -> {
            e.update(dt);
            return e.isDead();
        });
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
    public void setGameOver(boolean gameOver) { this.gameOver = gameOver; }
    public boolean isPaused() { return isPaused; }
    
    public void togglePause() { 
        this.isPaused = !this.isPaused; 
    }

    /**
     * Golyók frissítése (csak mozgás és pályaelhagyás).
     */
    private void updateBullets(float deltaTime) {
        bullets.removeIf(b -> {
            b.update(deltaTime);
            // Pálya elhagyás ellenőrzése (marad)
            return b.x < -50 || b.x > worldWidth + 50 || b.y < -50 || b.y > worldHeight + 50;
        });
    }

    /**
     * Ellenségek frissítése (csak a saját 'update' logikájuk, pl. mozgás).
     * Az ütközésvizsgálat átkerült a CollisionSystem-be.
     */
    private void updateEnemies(float deltaTime) {
        Iterator<Enemy> enemyIterator = enemies.iterator();
        while (enemyIterator.hasNext()) {
            Enemy enemy = enemyIterator.next();
            enemy.update(deltaTime, player);
        }
    }

    /**
     * A GadgetSystem (pl. Orbit Blade) hívja, ha megöl egy ellenséget.
     * Továbbítjuk a LevelingSystem-nek.
     */
    public void killEnemy(Enemy enemy) {
    	enemy.onDeath();
        levelingSystem.onEnemyKilled(enemy);
        enemies.remove(enemy);
    }

    /**
     * XP Orbok frissítése (csak mozgás, vonzódás és pályaelhagyás).
     * Az ütközésvizsgálat átkerült a CollisionSystem-be.
     */
    private void updateXPOrbs(float deltaTime) {
        Iterator<XPOrb> xpIter = xpOrbs.iterator();
        while (xpIter.hasNext()) {
            XPOrb orb = xpIter.next();
            orb.update(deltaTime, player);

            if (orb.x < -100 || orb.x > worldWidth + 100 || orb.y < -100 || orb.y > worldHeight + 100) {
                xpIter.remove();
            }
        }
    }
    
    private void updateFloatingTexts(float deltaTime) {
        floatingTexts.removeIf(ft -> {
            ft.update(deltaTime);
            return ft.life <= 0f;
        });
    }

    /**
     * Az InputHandler hívja. Továbbítjuk a kérést a LevelingSystem-nek.
     */
    public void selectGadget(Gadget gadget) {
        levelingSystem.selectGadget(gadget);
    }
    
    // ... (A következő metódusok VÁLTOZATLANOK maradnak a GameWorld-ben) ...
    // spawnPlayerBullets, recomputePlayerStats, getGadgetLevel, 
    // getAttackSpeedMultiplier, initGadgets, loadSounds

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
    
    public void recomputePlayerStats() {
        player.damage = player.baseDamage + 2 * getGadgetLevel("Attack Damage");
        int newMaxHp = player.baseMaxHp + 2 * getGadgetLevel("Max HP");
        if (newMaxHp != player.maxHp) {
            float percent = (float)player.hp / (float)player.maxHp;
            player.maxHp = newMaxHp;
            player.hp = Math.min(player.maxHp, Math.max(1, Math.round(player.maxHp * percent)));
        }
        player.moveSpeed = player.baseMoveSpeed * (1.0f + 0.2f * getGadgetLevel("Movement Speed"));
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
        gadgets.add(new Gadget("Attack Damage", "Increases bullet damage by 2.", 5));
        gadgets.add(new Gadget("Attack Speed", "Increases attack speed by 20%.", 5));
        gadgets.add(new Gadget("Max HP", "Increases max health by 2.", 5));
        gadgets.add(new Gadget("Movement Speed", "Increases movement speed by 20%.", 5));
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