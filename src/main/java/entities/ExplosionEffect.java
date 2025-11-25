package main.java.entities;

public class ExplosionEffect extends VisualEffect {

    private float life;
    private float maxLife;
    private float startSize;
    private float endSize;

    public ExplosionEffect(float x, float y, float maxSize, float duration) {
        super(x, y, 0f); // Kezdeti méret 0, az animáció során növekszik
        this.life = duration;
        this.maxLife = duration;
        this.startSize = 40f;
        this.endSize = maxSize;
        this.size = startSize;
    }

    @Override
    public void update(float dt) {
        life -= dt;
        if (life < 0) life = 0;

        float t = 1f - (life / maxLife);

        // Lineáris interpoláció a méret növeléséhez
        this.size = startSize + (endSize - startSize) * t;
    }

    @Override
    public boolean isDead() {
        return life <= 0;
    }

    @Override
    public float getAlpha() {
        float t = 1f - (life / maxLife);
        return 1f - t; // Az átlátszóság csökken az idő múlásával
    }

    public float getProgress() {
        return 1f - (life / maxLife);
    }
}