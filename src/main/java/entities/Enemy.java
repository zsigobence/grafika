package main.java.entities;

import main.java.world.GameWorld; // <-- ÚJ IMPORT

public class Enemy extends Character {
    public EnemyType type;
    private float vx, vy;
    private final int xpValue;
    
    // A GameWorld referencia szükséges a golyók létrehozásához
    protected GameWorld world; 

    // A lövéshez szükséges új változók
    private float shootTimer = (float)(Math.random() * 2.0); // Kezdő késleltetés
    private static final float SHOOT_INTERVAL = 2.1f;
    private static final float SHOOT_RANGE = 400f; // Fix távolság
    private static final float BULLET_SPEED = 300f;

    public Enemy(float x, float y, float vx, float vy, EnemyType type, GameWorld world) { // <-- MÓDOSÍTVA
        super(x, y, getSize(type), getHp(type));
        this.vx = vx;
        this.vy = vy;
        this.type = type;
        this.xpValue = getXpValue(type);
        this.world = world; // <-- HOZZÁADVA
    }
    
    public void update(float deltaTime, Player player) {
        
        // Célpont követése (steering behavior)
        float dirX = player.x - x;
        float dirY = player.y - y;
        float dist = (float) Math.hypot(dirX, dirY);
        if (dist == 0f) dist = 1f;

        float targetDirX = dirX / dist;
        float targetDirY = dirY / dist;

        float baseSpeed = getBaseSpeed(type);
        float steerForce = getSteerForce(type);

        // --- ÚJ LOGIKA A RANGED TÍPUSHOZ ---
        if (type == EnemyType.RANGED) {
            // Ha a távolság nagyobb a lőtávnál, közeledik
            if (dist > SHOOT_RANGE) {
                // Normál mozgás
                float newVx = vx * (1.0f - steerForce) + targetDirX * baseSpeed * steerForce;
                float newVy = vy * (1.0f - steerForce) + targetDirY * baseSpeed * steerForce;
                float newSpeed = (float) Math.hypot(newVx, newVy);
                if (newSpeed > 0) {
                    vx = (newVx / newSpeed) * baseSpeed;
                    vy = (newVy / newSpeed) * baseSpeed;
                }
                x += vx * deltaTime;
                y += vy * deltaTime;
            } else {
                // Ha lőtávon belül van, megáll (nem mozog) és lő
                vx = 0;
                vy = 0;
                
                shootTimer += deltaTime;
                if (shootTimer >= SHOOT_INTERVAL) {
                    shootTimer = 0f;
                    // Golyó létrehozása a világban
                    world.getBullets().add(new Bullet(x, y, targetDirX * BULLET_SPEED, targetDirY * BULLET_SPEED, Bullet.Owner.ENEMY));
                }
            }
        } else {
            // --- EREDETI MOZGÁS (BASIC, TANK, FAST) ---
            float newVx = vx * (1.0f - steerForce) + targetDirX * baseSpeed * steerForce;
            float newVy = vy * (1.0f - steerForce) + targetDirY * baseSpeed * steerForce;
            float newSpeed = (float) Math.hypot(newVx, newVy);
            if (newSpeed > 0) {
                vx = (newVx / newSpeed) * baseSpeed;
                vy = (newVy / newSpeed) * baseSpeed;
            }
            x += vx * deltaTime;
            y += vy * deltaTime;
        }
    }
    
    public void onDeath() {
        // alapértelmezés: semmi
    }

    public int getXp() {
        return xpValue;
    }

    // Statikus segédfüggvények a típus-specifikus értékekhez
    private static float getSize(EnemyType type) {
        switch (type) {
            case FAST: return 20.0f;
            case TANK: return 40.0f;
            case RANGED: return 28.0f; // <-- ÚJ
            default: return 28.0f; // BASIC
        }
    }

    private static int getHp(EnemyType type) {
        switch (type) {
            case FAST: return 1;
            case RANGED: return 1; // <-- ÚJ (Kérés: mint a tiny/fast)
            case TANK: return 6;
            default: return 3; // BASIC
        }
    }
    
    private static int getXpValue(EnemyType type) {
        switch (type) {
            case FAST: return 10;
            case TANK: return 20;
            case RANGED: return 15; // <-- ÚJ (Kérés: 15xp)
            default: return 15; // BASIC
        }
    }

    private static float getBaseSpeed(EnemyType type) {
        switch (type) {
            case FAST: return 150.0f;
            case TANK: return 45.0f;
            case RANGED: return 45.0f; // <-- ÚJ (Kérés: mint a tank)
            default: return 80.0f; // BASIC
        }
    }

    private static float getSteerForce(EnemyType type) {
        switch (type) {
            case FAST: return 0.12f;
            case TANK: return 0.05f;
            case RANGED: return 0.05f; // <-- ÚJ (mint a tank)
            default: return 0.08f; // BASIC
        }
    }
}