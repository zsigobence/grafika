package main.java.systems;

import main.java.audio.SoundManager;
import main.java.entities.*;
import main.java.rendering.Renderer;
import main.java.world.GameWorld;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.lwjgl.glfw.GLFW.glfwGetTime;

public class GadgetSystem {
    private final GameWorld world;
    private final Player player;
    private final Map<Enemy, Float> orbitHitTimers = new HashMap<>();
    private float laserTimer = 0f;
    private static float magnetCooldown = 0.0f;
    private static final float MAGNET_COOLDOWN_TIME = 30.0f;

    public GadgetSystem(GameWorld world, Player player) {
        this.world = world;
        this.player = player;
    }

    public void update(float dt) {
        updateOrbitBlades(dt);
        updateLaser(dt);
        updateMagnet(dt);
    }
    
    public static float getMagnetCooldown() {
    	return magnetCooldown;
    }

    private void updateMagnet(float dt) {
    	if (magnetCooldown > 0) {
            magnetCooldown -= dt;
        }
    }
    
    public static void activateMagnet(GameWorld world) {
    	if(magnetCooldown > 0) return;
        List<XPOrb> orbs = world.getXPOrbs(); 
        for (XPOrb orb : orbs) {
            orb.setMagnetized(true);
        }
        magnetCooldown = MAGNET_COOLDOWN_TIME;
    }

    private void updateOrbitBlades(float dt) {
        int level = world.getGadgetLevel("Orbit Blade");
        if (level <= 0) return;

        float time = (float) glfwGetTime();
        int count = 2 + level;
        float radius = 100f;
        float spinSpeed = 3.2f;
        float damageCooldown = 0.5f;
        int damage = 2 + level;

        orbitHitTimers.replaceAll((enemy, t) -> t - dt);

        // külön lista a meghalt ellenségeknek
        List<Enemy> deadEnemies = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            float angle = time * spinSpeed + i * ((float) Math.PI * 2f / count);
            float bx = player.x + (float) Math.cos(angle) * radius;
            float by = player.y + (float) Math.sin(angle) * radius;

            for (Enemy enemy : world.getEnemies()) {
                float dx = enemy.x - bx;
                float dy = enemy.y - by;
                float dist = (float) Math.sqrt(dx * dx + dy * dy);

                if (dist < enemy.size / 2f + 15f) {
                    float timer = orbitHitTimers.getOrDefault(enemy, 0f);
                    if (timer <= 0f) {
                        enemy.takeDamage(damage);
                        orbitHitTimers.put(enemy, damageCooldown);
                        SoundManager.playOverlap("flying-blade");

                        if (enemy.isDead()) {
                            deadEnemies.add(enemy); 
                            orbitHitTimers.remove(enemy);
                        }
                    }
                }
            }
        }

        // most, hogy vége az iterációnak, törölhetünk
        for (Enemy dead : deadEnemies) {
            world.killEnemy(dead);
        }

        // tisztítsuk a hit timert
        orbitHitTimers.keySet().removeIf(e -> !world.getEnemies().contains(e));
    }


    
    public void renderOrbitBlades(Renderer renderer) {
        int level = world.getGadgetLevel("Orbit Blade");
        if (level <= 0) return;
        String texturePath = "src/main/assets/blade.png";
        int count = 2 + level;
        float time = (float) glfwGetTime();
        float radius = 100f, spin = 3.2f;

        for (int i = 0; i < count; i++) {
            float angle = time * spin + i * ((float) Math.PI * 2f / count);
            float bx = player.x + (float) Math.cos(angle) * radius;
            float by = player.y + (float) Math.sin(angle) * radius;
            if (renderer.getTextureLoader().isTextureLoaded(texturePath)) {
                renderer.renderTextureInWorld(texturePath, bx, by, 30f, 30f);
            } else {
            	renderer.drawQuad(bx, by, 30f, 30f, 0.9f, 0.9f, 0.2f, 1.0f);
            }
            
        }
    }
    
    private void updateLaser(float dt) {
        laserTimer -= dt;
        int level = world.getGadgetLevel("Laser Beam");
        if (level <= 0 || laserTimer > 0f || world.getEnemies().isEmpty()) return;
        
        Enemy nearest = player.findNearestEnemy(world.getEnemies());
        if (nearest != null) {
            float dx = nearest.x - player.x;
            float dy = nearest.y - player.y;
            float len = (float) Math.hypot(dx, dy);
            float speed = 1200f;
            int damage = 2 + level * 2;
            int pierce = 4 + level * 2;
            world.getBullets().add(new LaserBullet(player.x, player.y, dx / len * speed, dy / len * speed, damage, pierce));
            SoundManager.play("laser");
            laserTimer = Math.max(1f, 9f - level * 2);
        }
    }
    
    public void renderLaserBullets(Renderer renderer) {
        for (Bullet b : world.getBullets()) {
            if (b instanceof LaserBullet) {
                renderer.drawQuad(b.x, b.y, b.size * 2.1f, b.size * 2.4f, 1.0f, 0.2f, 0.2f, 0.22f); // Glow
                renderer.drawQuad(b.x, b.y, b.size, b.size, 1.0f, 0.08f, 0.08f, 1.0f); // Core
            }
        }
    }


}