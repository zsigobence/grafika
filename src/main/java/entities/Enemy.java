package main.java.entities;

import static org.lwjgl.glfw.GLFW.glfwGetTime;

public class Enemy extends Character {
    public EnemyType type;
    private float vx, vy;
    private final int xpValue;

    public Enemy(float x, float y, float vx, float vy, EnemyType type) {
        super(x, y, getSize(type), getHp(type));
        this.vx = vx;
        this.vy = vy;
        this.type = type;
        this.xpValue = getXpValue(type);
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

        // Irány normalizálása és sebesség alkalmazása
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

    public int getXp() {
        return xpValue;
    }

    // Statikus segédfüggvények a típus-specifikus értékekhez
    private static float getSize(EnemyType type) {
        switch (type) {
            case FAST: return 20.0f;
            case TANK: return 40.0f;
            default: return 28.0f;
        }
    }

    private static int getHp(EnemyType type) {
        switch (type) {
            case FAST: return 1;
            case TANK: return 6;
            default: return 3;
        }
    }
    
    private static int getXpValue(EnemyType type) {
        switch (type) {
            case FAST: return 10;
            case TANK: return 20;
            default: return 15;
        }
    }

    private static float getBaseSpeed(EnemyType type) {
        switch (type) {
            case FAST: return 150.0f;
            case TANK: return 45.0f;
            default: return 80.0f;
        }
    }

    private static float getSteerForce(EnemyType type) {
        switch (type) {
            case FAST: return 0.12f;
            case TANK: return 0.05f;
            default: return 0.08f;
        }
    }
}