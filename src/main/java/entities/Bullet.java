package main.java.entities;

public class Bullet extends GameObject {
    public float vx, vy;

    public Bullet(float x, float y, float vx, float vy) {
        super(x, y, 8);
        this.vx = vx;
        this.vy = vy;
    }

    public void update(float dt) {
        x += vx * dt;
        y += vy * dt;
    }
}