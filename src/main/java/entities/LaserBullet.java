package main.java.entities;

import main.java.audio.SoundManager;

public class LaserBullet extends Bullet {
    public int pierce;
    public int damage;

    public LaserBullet(float x, float y, float vx, float vy, int damage, int pierce) {
        super(x, y, vx, vy);
        this.damage = damage;
        this.pierce = pierce;
        this.size = 25; 
    }

    public void onHit(Enemy e) {
    	System.out.println(e.type);
    	if(e instanceof BossEnemy boss) {
    		boss.takeDamage(damage + pierce/2);
    		pierce = 0;
    	}else {
            e.takeDamage(damage);
            pierce--;
    	}
        SoundManager.playOverlap("damage");
    }
}