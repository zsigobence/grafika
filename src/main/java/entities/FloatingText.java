package main.java.entities;

public class FloatingText {
    public float x, y;
    public String text;
    public float life;
    public final float initialLife;
    private float vy;
    public float r, g, b;

    public FloatingText(float x, float y, String text, float life, float vy, float r, float g, float b) {
        this.x = x;
        this.y = y;
        this.text = text;
        this.life = life;
        this.initialLife = life;
        this.vy = vy;
        this.r = r;
        this.g = g;
        this.b = b;
    }

    public void update(float dt) {
        life -= dt;
        y += vy * dt;
        vy *= 0.98f; // Lassuló emelkedés
    }
}