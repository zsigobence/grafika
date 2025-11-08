package main.java.entities;

import main.java.audio.SoundManager;
import main.java.rendering.TextureLoader;
import main.java.world.GameWorld;

public class BossEnemy extends Enemy {

    public enum BossType { GHOST, DEMON, DRAGON }

    private final GameWorld world;
    private final BossType bossType;
    private final TextureLoader.TextureInfo textureInfo;

    private int phase = 1;
    private boolean phase2Started = false;
    private boolean phase3Started = false;
    private float flashTimer = 0f;
    private float phaseInvulnTimer = 0f;
    private float shootTimer = 0f;
    private float abilityTimer = 0f;
 // Ghost teleporthoz
 // Teleport FSM
    private boolean isTeleporting = false;
    private float teleportTimer = 0f;         // visszaszámláló a váltásig
    private float teleportDelay = 0.6f;      // ennyi ideig “eltűnik” (windup)
    private float teleportTargetX, teleportTargetY;


    public BossEnemy(float x, float y, EnemyType type, GameWorld world, BossType bossType) {
        super(x, y, 0, 0, type);
        this.world = world;
        this.bossType = bossType;

        this.size = 200f;
        this.maxHp = 200;
        this.hp = maxHp;

        TextureLoader textureLoader = TextureLoader.getInstance();
        switch (bossType) {
            case GHOST -> this.textureInfo = textureLoader.loadTexture("src/main/assets/ghost.png");
            case DEMON -> this.textureInfo = textureLoader.loadTexture("src/main/assets/demon.png");
            case DRAGON -> this.textureInfo = textureLoader.loadTexture("src/main/assets/dragon.png");
            default -> this.textureInfo = textureLoader.loadTexture("src/main/assets/ghost.png");
        }
    }

    public float getFlashProgress() {
        if (flashTimer <= 0) return 0f;
        return Math.min(1f, flashTimer / 0.5f);
    }

    @Override
    public void update(float deltaTime, Player player) {
        super.update(deltaTime, player);
        shootTimer += deltaTime;
        abilityTimer += deltaTime;
        if (flashTimer > 0f) flashTimer -= deltaTime;
        if (phaseInvulnTimer > 0f) phaseInvulnTimer -= deltaTime;

        // --- Fázisváltás ---
        if (phase == 1 && hp < maxHp * 0.6f && !phase2Started) {
            phase = 2;
            phase2Started = true;
            flashTimer = 0.5f;
            phaseInvulnTimer = 1f;
            size *= 1.1f;
            System.out.println(bossType + " entering Phase 2!");
        } else if (phase == 2 && hp < maxHp * 0.35f && !phase3Started) {
            phase = 3;
            phase3Started = true;
            flashTimer = 0.8f;
            phaseInvulnTimer = 1f;
            size *= 1.2f;
            triggerExplosion();
            System.out.println(bossType + " entering Phase 3!");
        }

        // --- Egyedi viselkedés bossType szerint ---
        switch (bossType) {
            case GHOST -> updateGhost(player, deltaTime);
            case DEMON -> updateDemon(player, deltaTime);
            case DRAGON -> updateDragon(player, deltaTime);
        }
    }

    @Override
    public void takeDamage(int amount) {
        if (phaseInvulnTimer > 0f) return;
        super.takeDamage(amount);
    }

    private void triggerExplosion() {
        for (int i = 0; i < 40; i++) {
            float angle = (float) (Math.random() * Math.PI * 2);
            float speed = 150f + (float) Math.random() * 200f;
            float vx = (float) Math.cos(angle) * speed;
            float vy = (float) Math.sin(angle) * speed;
            world.getBullets().add(new Bullet(x, y, vx, vy, Bullet.Owner.ENEMY));
        }
        SoundManager.playOverlap("laser");
    }

    // 👻 GHOST — gyors, teleportál, kiszámíthatatlan
 // 👻 GHOST — tényleges, jól látható teleport a pálya másik oldalára
    private void updateGhost(Player player, float dt) {
        shootTimer += dt;
        abilityTimer += dt;

        // Lövés
        if (shootTimer > 2.0f) {
            shootAtPlayer(player, 1, 0f);
            shootTimer = 0f;
        }

        // Teleport minden 4 másodpercben (fázis 2-től)
     // TELEPORT – fázis 2-től
        if (phase >= 2) {
            // ha nem épp teleportál és lejárt a képesség CD-je, indítsunk windupot
            if (!isTeleporting && abilityTimer > 4.0f) {
                // célpont: PÁLYA MÁSIK OLDALA (amit kértél)
                boolean horizontal = Math.random() < 0.5;

             // célpont: játékos körül 400–700 egységre, random irányban
                float distance = 400f + (float)Math.random() * 300f;
                double angle = Math.random() * Math.PI * 2;
                float newX = player.x + (float)Math.cos(angle) * distance;
                float newY = player.y + (float)Math.sin(angle) * distance;


                // határok közé
                teleportTargetX = Math.max(50, Math.min(world.worldWidth  - 50, newX));
                teleportTargetY = Math.max(50, Math.min(world.worldHeight - 50, newY));

                // windup: eltűnik rövid időre
                isTeleporting = true;
                teleportTimer = teleportDelay;
                flashTimer = teleportDelay;            // vizuális jelzés
                SoundManager.playOverlap("damage");    // “eltűnés” hang

                abilityTimer = 0f; // CD újraindít
            }

            // ha épp teleportál, számláljunk vissza; amikor lejár, átpakoljuk
            if (isTeleporting) {
                teleportTimer -= dt;
             // 💥 Kis energia-robbanás effekt (csak látvány)
                for (int i = 0; i < 12; i++) {
                    float angle = (float)(Math.random() * Math.PI * 2);
                    float speed = 80f + (float)Math.random() * 120f;
                    float vx = (float)Math.cos(angle) * speed;
                    float vy = (float)Math.sin(angle) * speed;
                    world.getBullets().add(new Bullet(x, y, vx, vy, Bullet.Owner.ENEMY));
                }

            }
        }

    }


    // 😈 DEMON — közelharc + körlövés
    private void updateDemon(Player player, float dt) {
        // fázis szerint változó támadások
        if (phase == 1 && shootTimer > 2.0f) {
            radialShot(8, 200f);
            shootTimer = 0f;
        } else if (phase >= 2 && shootTimer > 1.2f) {
            radialShot(16, 250f);
            shootTimer = 0f;
        }

        // közelharci mozgás
        float dx = player.x - x;
        float dy = player.y - y;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        if (dist > 150) {
            x += (dx / dist) * 120 * dt;
            y += (dy / dist) * 120 * dt;
        }

        // fázis 3: extra tűzgyűrű
        if (phase == 3 && abilityTimer > 5f) {
            triggerExplosion();
            abilityTimer = 0f;
        }
    }

    // 🐉 DRAGON — tűzgyűrű + nagy támadások
    private void updateDragon(Player player, float dt) {
        // 1. normál támadás (irányított golyók)
        if (shootTimer > 1.2f) {
            shootAtPlayer(player, 5, (float) Math.toRadians(15));
            shootTimer = 0f;
        }

        // 2. fázis 2-től teljes körlövés
        if (phase >= 2 && abilityTimer > 3f) {
            int bulletCount = (phase == 3) ? 40 : 24; // fázis 3-ban még több
            float speed = (phase == 3) ? 400f : 300f;
            radialShot(bulletCount, speed);

            SoundManager.playOverlap("laser");
            abilityTimer = 0f;
            System.out.println("🐉 Dragon unleashed fire ring (" + bulletCount + " bullets)");
        }

        // 3. lassú mozgás a játékos felé
        float dx = player.x - x;
        float dy = player.y - y;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        if (dist > 300) {
            x += (dx / dist) * 50 * dt;
            y += (dy / dist) * 50 * dt;
        }
    }


    // --- Lövési segédfüggvények ---
    private void shootAtPlayer(Player player, int count, float spread) {
        float baseAngle = (float) Math.atan2(player.y - y, player.x - x);
        for (int i = 0; i < count; i++) {
            float a = baseAngle + (i - (count - 1) / 2f) * spread;
            float vx = (float) Math.cos(a) * 400f;
            float vy = (float) Math.sin(a) * 400f;
            world.getBullets().add(new Bullet(x, y, vx, vy, Bullet.Owner.ENEMY));
        }
    }

    private void radialShot(int count, float speed) {
        for (int i = 0; i < count; i++) {
            float angle = (float) (i * Math.PI * 2 / count);
            float vx = (float) Math.cos(angle) * speed;
            float vy = (float) Math.sin(angle) * speed;
            world.getBullets().add(new Bullet(x, y, vx, vy, Bullet.Owner.ENEMY));
        }
    }

    // --- Getterek a Renderer-nek ---
    public int getPhase() { return phase; }
    public float getFlashTimer() { return flashTimer; }
    public BossType getBossType() { return bossType; }
    public TextureLoader.TextureInfo getTextureInfo() { return textureInfo; }
    public boolean isTeleporting() { 
        return isTeleporting; 
    }

}
