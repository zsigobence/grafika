package main.java.entities;

import main.java.audio.SoundManager;

public class LaserBullet extends Bullet {
    public int pierce;
    public int damage;

    public LaserBullet(float x, float y, float vx, float vy, int damage, int pierce) {
        super(x, y, vx, vy);
        this.damage = damage;
        this.pierce = pierce;
        this.size = 18; // Nagyobb, mint a sima lövedék
    }

    public void onHit(Enemy e) {
        e.takeDamage(damage);
        pierce--;
        SoundManager.playOverlap("damage");
    }
}