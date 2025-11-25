package main.java.entities;

import java.util.List;

public class Player extends Character {
    public float shootCooldown = 0;
    public int damage;
    public float moveSpeed;
    public final int baseDamage, baseMaxHp;
    public final float baseMoveSpeed;
    public final float autoShootRange = 500.0f;
    private float moveX, moveY;

    public Player(float x, float y, int maxHp, int damage, float moveSpeed) {
        super(x, y, 32, maxHp);
        this.baseMaxHp = maxHp;
        this.baseDamage = damage;
        this.baseMoveSpeed = moveSpeed;
        this.damage = damage;
        this.moveSpeed = moveSpeed;
    }

    public void setMovementDirection(float dx, float dy) {
        this.moveX = dx;
        this.moveY = dy;
    }

    public void update(float deltaTime, List<Enemy> enemies) {
        if (moveX != 0 || moveY != 0) {
            float len = (float) Math.sqrt(moveX * moveX + moveY * moveY);
            x += (moveX / len) * moveSpeed * deltaTime;
            y += (moveY / len) * moveSpeed * deltaTime;
        }
        // Pozíció határolása a pályán belül
        x = Math.max(size / 2, Math.min(2000 - size / 2, x));
        y = Math.max(size / 2, Math.min(2000 - size / 2, y));

        shootCooldown -= deltaTime;
    }

    public boolean isReadyToShoot() {
        return shootCooldown <= 0;
    }

    public Enemy findNearestEnemy(List<Enemy> enemies) {
        if (enemies.isEmpty()) return null;
        Enemy nearest = null;
        float bestDistSq = Float.MAX_VALUE;
        for (Enemy e : enemies) {
            float d2 = (e.x - x) * (e.x - x) + (e.y - y) * (e.y - y);
            if (d2 < bestDistSq) {
                bestDistSq = d2;
                nearest = e;
            }
        }
        return nearest;
    }
}