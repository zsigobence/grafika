package main.java.entities;

public class Bullet extends GameObject {
    public float vx, vy;

    public enum Owner { PLAYER, ENEMY }
    public Owner owner;

    public Bullet(float x, float y, float vx, float vy) {
        this(x, y, vx, vy, Owner.PLAYER);
    }

    public Bullet(float x, float y, float vx, float vy, Owner owner) {
        super(x, y, 8);
        this.vx = vx;
        this.vy = vy;
        this.owner = owner;
    }

    public void update(float dt) {
        x += vx * dt;
        y += vy * dt;
    }
}
