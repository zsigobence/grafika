package main.java.systems;

import main.java.audio.SoundManager;
import main.java.entities.*;
import main.java.rendering.Renderer;
import main.java.world.GameWorld;
import main.java.config.Config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.lwjgl.glfw.GLFW.glfwGetTime;

public class GadgetSystem {
    private final GameWorld world;
    private final Player player;
    private final Map<Enemy, Float> orbitHitTimers = new HashMap<>();
    private final List<Enemy> deadEnemiesCache = new ArrayList<>();
    private float laserTimer = 0f;
    private static float magnetCooldown = 0.0f;
    private static final float MAGNET_COOLDOWN_TIME = Config.Gameplay.MAGNET_COOLDOWN_SEC;

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
        float radius = Config.Gameplay.ORBIT_BLADE_RADIUS;
        float spinSpeed = Config.Gameplay.ORBIT_BLADE_SPEED;
        float damageCooldown = Config.Gameplay.ORBIT_BLADE_CD;
        int damage = 2 + level;

        Iterator<Map.Entry<Enemy, Float>> it = orbitHitTimers.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Enemy, Float> entry = it.next();
            entry.setValue(entry.getValue() - dt);
        }

        deadEnemiesCache.clear();


        for (int i = 0; i < count; i++) {
            float angle = time * spinSpeed + i * ((float) Math.PI * 2f / count);
            float bx = player.x + (float) Math.cos(angle) * radius;
            float by = player.y + (float) Math.sin(angle) * radius;

            for (Enemy enemy : world.getEnemies()) {
                float dx = enemy.x - bx;
                float dy = enemy.y - by;
                float distSq = dx * dx + dy * dy;
                float hitDist = enemy.size / 2f + 15f;
                float hitDistSq = hitDist * hitDist;

                if (distSq < hitDistSq) {
                    float timer = orbitHitTimers.getOrDefault(enemy, 0f);
                    
                    if (timer <= 0f) {
                        enemy.takeDamage(damage);
                        orbitHitTimers.put(enemy, damageCooldown);
                        SoundManager.playOverlap("flying-blade");

                        if (enemy.isDead()) {
                            deadEnemiesCache.add(enemy);
                            orbitHitTimers.remove(enemy); 
                        }
                    }
                }
            }
        }

        for (Enemy dead : deadEnemiesCache) {
            world.killEnemy(dead);
        }

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
            float distSq = dx * dx + dy * dy;
            float len = (float) Math.sqrt(distSq);
            if (len > 0.0001f) {
                float speed = Config.Gameplay.LASER_SPEED;
                int damage = 2 + level * 2;
                int pierce = 4 + level * 2;
                
                world.getBullets().add(new LaserBullet(
                    player.x, player.y, 
                    (dx / len) * speed, 
                    (dy / len) * speed, 
                    damage, pierce
                ));
                
                SoundManager.play("laser");
                laserTimer = Math.max(1f, 9f - level * 2);
            }
        }
    }
    
    public void renderLaserBullets(Renderer renderer) {
        for (Bullet b : world.getBullets()) {
            if (b instanceof LaserBullet) {
                renderer.drawQuad(b.x, b.y, b.size * 2.1f, b.size * 2.4f, 1.0f, 0.2f, 0.2f, 0.22f); // Ragyogás
                renderer.drawQuad(b.x, b.y, b.size, b.size, 1.0f, 0.08f, 0.08f, 1.0f); // Mag
            }
        }
    }


}