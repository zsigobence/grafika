package main.java.world;

import main.java.audio.SoundManager;
import main.java.entities.Enemy;
import main.java.entities.FloatingText;
import main.java.entities.Player;
import main.java.entities.XPOrb;
import main.java.systems.Gadget;
import main.java.entities.BossEnemy;
import main.java.entities.ExplosionEffect;


import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class LevelingSystem {

    private final GameWorld world;
    private final Player player;

    public LevelingSystem(GameWorld world, Player player) {
        this.world = world;
        this.player = player;
    }

    public void onEnemyKilled(Enemy enemy) {
        world.getXPOrbs().add(new XPOrb(enemy.x, enemy.y, enemy.getXp()));
        
        world.score += enemy.type == main.java.entities.EnemyType.TANK ? 30 : 10;
        
        if (enemy instanceof BossEnemy) {
            world.getVisualEffects().add(
                new ExplosionEffect(enemy.x, enemy.y, 350f, 1.0f)
            );
        }
        
        
        int lsLevel = world.getGadgetLevel("Life Steal");
        if (lsLevel > 0) {
            float chance = lsLevel * 0.03f;
            if (Math.random() < chance) {
                player.heal(1);
                world.getFloatingTexts().add(new FloatingText(player.x, player.y - 40, "+HP", 1.0f, -40f, 0.3f, 1f, 0.3f));
            }
        }
    }

    public void onPlayerCollectedOrb(XPOrb orb) {

        if (world.levelUpMenuActive) {
            return;
        }

        world.xp += orb.value;
        world.getFloatingTexts().add(new FloatingText(
                player.x, player.y - player.size,
                "+" + orb.value,
                1.2f, -40.0f, 1.0f, 1.0f, 0.2f
        ));
        SoundManager.playOverlap("xp");

        // Szintlépések kezelése
        while (world.xp >= world.xpToNext) {
            world.xp -= world.xpToNext;
            world.level++;
            world.xpToNext = calcXpForLevel(world.level);
            world.pendingLevelUps++;
        }

        if (!world.levelUpMenuActive && world.pendingLevelUps > 0) {
            levelUp();
        }
    }

    private void levelUp() {

        world.pendingLevelUps--;

        world.levelUpMenuActive = true;

        world.getFloatingTexts().add(new FloatingText(
                player.x,
                player.y - player.size - 20,
                "Level Up!",
                1.6f, -70.0f, 1.0f, 0.8f, 0.0f
        ));

        SoundManager.play("levelup");

        generateLevelUpOptions();
    }


    private void generateLevelUpOptions() {
        world.availableGadgets.clear();
        List<Gadget> nonMaxGadgets = new ArrayList<>();
        for (Gadget gadget : world.getGadgets()) {
            if (gadget.level < gadget.maxLevel) {
                nonMaxGadgets.add(gadget);
            }
        }
        Collections.shuffle(nonMaxGadgets);
        int count = Math.min(3, nonMaxGadgets.size());
        for (int i = 0; i < count; i++) {
            world.availableGadgets.add(nonMaxGadgets.get(i));
        }
    }

    public void selectGadget(Gadget gadget) {
        gadget.levelUp();
        world.recomputePlayerStats();
        world.levelUpMenuActive = false;

        world.getFloatingTexts().add(
            new FloatingText(player.x, player.y, gadget.name + " +1",
            1.5f, -50f, 0f, 1f, 0f)
        );

        if (world.pendingLevelUps > 0) {
            levelUp();
        }
    }

    private int calcXpForLevel(int lvl) {
        double val = 100.0 * Math.pow(Math.min(1.45, 1.25 + lvl * 5), Math.max(0, lvl - 1));
        return Math.max(20, (int) Math.round(val));
    }

}