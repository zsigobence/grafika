package main.java.entities;

public class VisualEffect {
    public float x, y;
    public float life = 1.0f;
    public float maxLife = 1.0f;
    public float size;

    public VisualEffect(float x, float y, float size) {
        this.x = x;
        this.y = y;
        this.size = size;
    }

    public void update(float dt) {
        life -= dt;
    }

    public boolean isDead() {
        return life <= 0;
    }

    public float getAlpha() {
        return life / maxLife;
    }
}
