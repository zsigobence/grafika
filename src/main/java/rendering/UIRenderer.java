package main.java.rendering;

import main.java.entities.FloatingText;
import main.java.systems.GadgetSystem;
import main.java.systems.Gadget;
import main.java.world.GameWorld;
import main.java.config.AssetPaths;
import main.java.entities.BossEnemy;

public class UIRenderer {
    private final Renderer renderer;
    private final int width, height;

    public UIRenderer(Renderer renderer, int width, int height) {
        this.renderer = renderer;
        this.width = width;
        this.height = height;
    }


    // Színes négyszögek renderelése (Batch)
    public void renderQuads(GameWorld world) {
        renderHUDQuads(world); 
        
        if (world.levelUpMenuActive) {
            renderLevelUpMenuQuads(world);
        } else if (world.isPaused()) {
            renderPauseMenuQuads(world);
        } else if (world.isGameOver()) {
            renderGameOverQuads(world);
        }
    }
    
    // Textúrák renderelése (Batch)
    public void renderTextures(GameWorld world) {
        renderHUDTextures(world);
        if (world.levelUpMenuActive) {
            renderLevelUpMenuTextures(world);
        }
    }


    // Szövegek renderelése (Immediate)
    public void renderText(GameWorld world, float camLeft, float camTop) {
        renderHUDText(world); 
        
        if (!world.levelUpMenuActive && !world.isPaused() && !world.isGameOver()) {
            renderFloatingTexts(world, camLeft, camTop); 
        }
        if (world.levelUpMenuActive) {
            renderLevelUpMenuText(world);
        } else if (world.isPaused()) { 
            renderPauseMenuText(world);
        } else if (world.isGameOver()) {
            renderGameOverText(world);
        }
    }

    private void renderHUDQuads(GameWorld world) {
        float barW = UIConstants.XP_BAR_WIDTH;
        float barH = UIConstants.XP_BAR_HEIGHT;
        float barX = width / 2.0f - barW / 2f;
        float barY = UIConstants.XP_BAR_LEVEL_TEXT_Y + UIConstants.XP_BAR_Y_OFFSET;

        renderer.drawQuad(barX + barW/2f, barY + barH/2f, barW, barH, 0.85f, 0.85f, 0.87f, 1.0f);
        float percent = world.xpToNext > 0 ? (float) world.xp / world.xpToNext : 0f;
        float fillW = barW * percent;
        if (fillW > 0.001f) {
            renderer.drawQuad(barX + fillW/2f, barY + barH/2f, fillW, barH - UIConstants.XP_BAR_INSET, 
                0.2f + percent * 1.2f, 0.9f - percent * 0.2f, 0.05f, 1f);
        }
        renderBossHealthBar(world);
    }
    
    private void renderHUDTextures(GameWorld world) {
        float iconSize = 64.0f; 
        float padding = 20.0f;  

        float iconCenterX = width - padding - iconSize / 2f;
        float iconCenterY = height - padding - iconSize / 2f;
        
        float cooldown = GadgetSystem.getMagnetCooldown();
        if (cooldown > 0) {
            renderer.renderTexture("src/main/assets/magnet.png", iconCenterX, iconCenterY, iconSize, iconSize, 0.3f);
        } else {
            renderer.renderTexture("src/main/assets/magnet.png", iconCenterX, iconCenterY, iconSize, iconSize, 1f);
        }
    }
    
    private void renderHUDText(GameWorld world) {
        renderer.renderText("Score: " + world.score, 
            UIConstants.HUD_PADDING_X, UIConstants.HUD_SCORE_Y, 1.0f, 1f,1f,1f,1f);
        
        int totalSeconds = (int) Math.floor(world.elapsedTime);
        String timeStr = String.format("%02d:%02d", totalSeconds / 60, totalSeconds % 60);
        float textW = renderer.getTextWidth(timeStr, 1.0f);
        renderer.renderText(timeStr, width - UIConstants.HUD_TIMER_PADDING_X - textW, 
            UIConstants.HUD_SCORE_Y, 1.0f, 1f, 1f, 1f, 1f);

        String levelText = "Level: " + world.level;
        float levelTextWidth = renderer.getTextWidth(levelText, 1.0f);
        renderer.renderText(levelText, width / 2f - levelTextWidth / 2f, 
            UIConstants.XP_BAR_LEVEL_TEXT_Y, 1.0f, 1f, 1f, 1f, 1f);
        
        float iconSize = 64.0f;
        float padding = 20.0f;
        float iconCenterX = width - padding - iconSize / 2f;
        float iconCenterY = height - padding - iconSize / 2f;
        
        float cooldown = GadgetSystem.getMagnetCooldown();
        if (cooldown > 0) {
            String cooldownText = String.format("%d", (int) Math.ceil(cooldown));
            float textWidth = renderer.getTextWidth(cooldownText, 1.0f);
            renderer.renderText(cooldownText, iconCenterX - textWidth / 2f, iconCenterY + 10f, 1.0f, 1f, 1f, 1f, 1f);
        }
    }
    
    private void renderBossHealthBar(GameWorld world) {
        BossEnemy boss = world.getEnemies().stream()
                .filter(e -> e instanceof BossEnemy)
                .map(e -> (BossEnemy) e)
                .findFirst()
                .orElse(null);

        if (boss == null) return;

        float healthPercent = Math.max(0f, Math.min(1f, (float) boss.hp / boss.maxHp));
        float barWidth = width * 0.5f;
        float barHeight = 10f;
        float centerX = width / 2f;
        float xpBarBottom = UIConstants.XP_BAR_LEVEL_TEXT_Y + 40f;
        float centerY = xpBarBottom + 25f;

        float r = Math.min(1f, (1f - healthPercent) * 2f);
        float g = Math.min(1f, healthPercent * 2f);
        float b = 0.1f;

        renderer.drawQuad(centerX, centerY, barWidth, barHeight + 2f, 0.1f, 0.1f, 0.1f, 1f);
        renderer.drawQuad(centerX - (barWidth - barWidth * healthPercent) / 2f,
                centerY, barWidth * healthPercent, barHeight, r, g, b, 1f);

        String text = "BOSS HP: " + (int) boss.hp + " / " + (int) boss.maxHp;
        float textWidth = renderer.getTextWidth(text, 1.0f);
        renderer.renderText(text, centerX - textWidth / 2f, centerY + 26f, 1.0f, 1f, 1f, 1f, 1f);
    }
    
    private void renderFloatingTexts(GameWorld world, float camLeft, float camTop) {
        if (world.levelUpMenuActive || world.isPaused() || world.isGameOver()) return;
        for (FloatingText ft : world.getFloatingTexts()) {
            float screenX = ft.x - camLeft;
            float screenY = ft.y - camTop;
            float alpha = Math.max(0f, Math.min(1f, ft.life / ft.initialLife));
            renderer.renderText(ft.text, screenX, screenY, 1.0f, ft.r, ft.g, ft.b, alpha);
        }
    }

    private void renderLevelUpMenuQuads(GameWorld world) {
        renderer.drawQuad(width / 2f, height / 2f, width, height, 0f, 0f, 0f, UIConstants.LEVEL_UP_BG_ALPHA);

        float boxW = UIConstants.LEVEL_UP_BOX_WIDTH;
        float boxH = UIConstants.LEVEL_UP_BOX_HEIGHT;
        float gap = UIConstants.LEVEL_UP_BOX_GAP;
        float centerX = width / 2f, startY = height / 2f;
        float startBoxY = startY - 10f;

        for (int i = 0; i < world.availableGadgets.size(); i++) {
            Gadget gadget = world.availableGadgets.get(i);
            float x = centerX + (i - (world.availableGadgets.size() - 1) / 2f) * (boxW + gap);
            
            renderer.drawQuad(x, startY, boxW, boxH, 0.4f, 0.4f, 0.4f, 1.0f);

            int max = gadget.maxLevel;
            float squareSize = UIConstants.LEVEL_UP_LEVEL_SQUARE_SIZE;
            float squareGap = UIConstants.LEVEL_UP_LEVEL_SQUARE_GAP;
            float totalWidth = max * squareSize + (max - 1) * squareGap;
            float startSqX = x - totalWidth / 2f;
            for (int s = 0; s < max; s++) {
                float sx = startSqX + s * (squareSize + squareGap) + squareSize/2f;
                if (s < gadget.level) renderer.drawQuad(sx, startBoxY, squareSize, squareSize, 1.0f, 0.85f, 0.05f, 1.0f);
                else if (s == gadget.level) renderer.drawQuad(sx, startBoxY, squareSize, squareSize, 1.0f, 0.85f, 0.05f, 0.4f);
                else renderer.drawQuad(sx, startBoxY, squareSize, squareSize, 0.05f, 0.05f, 0.05f, 1.0f);
            }
        }
    }
    
    private void renderLevelUpMenuTextures(GameWorld world) {
        float boxW = UIConstants.LEVEL_UP_BOX_WIDTH;
        float boxH = UIConstants.LEVEL_UP_BOX_HEIGHT;
        float gap = UIConstants.LEVEL_UP_BOX_GAP;
        float centerX = width / 2f, startY = height / 2f;

        for (int i = 0; i < world.availableGadgets.size(); i++) {
            Gadget gadget = world.availableGadgets.get(i);
            float x = centerX + (i - (world.availableGadgets.size() - 1) / 2f) * (boxW + gap);
            
            String texturePath = getGadgetTexturePath(gadget.name);
            renderer.renderTexture(texturePath, x, startY + boxH/2f - UIConstants.LEVEL_UP_BOX_ICON_Y_FROM_BOTTOM, 
                UIConstants.LEVEL_UP_BOX_ICON_SIZE, UIConstants.LEVEL_UP_BOX_ICON_SIZE);
        }
    }

    private void renderLevelUpMenuText(GameWorld world) {
        String title = "LEVEL UP! Choose a gadget:";
        renderer.renderText(title, width / 2f - renderer.getTextWidth(title, UIConstants.LEVEL_UP_TITLE_SCALE) / 2f, 
            UIConstants.LEVEL_UP_TITLE_Y, UIConstants.LEVEL_UP_TITLE_SCALE, 1f, 1f, 0f, 1f);

        float boxW = UIConstants.LEVEL_UP_BOX_WIDTH;
        float boxH = UIConstants.LEVEL_UP_BOX_HEIGHT;
        float gap = UIConstants.LEVEL_UP_BOX_GAP;
        float centerX = width / 2f, startY = height / 2f;

        for (int i = 0; i < world.availableGadgets.size(); i++) {
            Gadget gadget = world.availableGadgets.get(i);
            float x = centerX + (i - (world.availableGadgets.size() - 1) / 2f) * (boxW + gap);

            renderer.renderText(gadget.name, x - renderer.getTextWidth(gadget.name, UIConstants.LEVEL_UP_BOX_TITLE_SCALE) / 2f, 
                startY - boxH/2f + UIConstants.LEVEL_UP_BOX_TITLE_Y_OFFSET, UIConstants.LEVEL_UP_BOX_TITLE_SCALE, 1f, 1f, 1f, 1f);
            
            String effect = getNextLevelEffect(gadget);
            renderer.renderText(effect, x - renderer.getTextWidth(effect, UIConstants.LEVEL_UP_BOX_EFFECT_SCALE)/2f, 
                startY - boxH/2f + UIConstants.LEVEL_UP_BOX_EFFECT_Y_OFFSET, UIConstants.LEVEL_UP_BOX_EFFECT_SCALE, 0f, 1f, 0f, 1f);
        }
    }

    private String getNextLevelEffect(Gadget gadget) {
        int nextLevel = gadget.level + 1;
        switch (gadget.name) {
            case "Attack Damage": return "Damage: +" + gadget.level + " -> +" + 2 * nextLevel;
            case "Attack Speed": return "Speed: +" + (gadget.level * 20) + "% -> +" + (nextLevel * 20) + "%";
            case "Max HP": return "Max HP: +" + gadget.level + " -> +" + 2 * nextLevel;
            case "Movement Speed": return "Speed: +" + (gadget.level * 10) + "% -> +" + (nextLevel * 20) + "%";
            case "Multi Attack": return "Projectiles: " + (gadget.level + 1) + " -> " + (nextLevel + 1);
            case "Life Steal": return "Chance: " + (gadget.level * 3) + "% -> " + (nextLevel * 3) + "%";
            case "Orbit Blade": return "Blades: " + (2 + gadget.level) + " -> " + (2 + nextLevel);
            case "Laser Beam": return "Cooldown: " + Math.max(1f, 9f - gadget.level * 2) + "s -> " + Math.max(1f, 9f - nextLevel * 2) + "s";
            default: return "Upgrade";
        }
    }
    
    private void renderPauseMenuQuads(GameWorld world) {
        renderer.drawQuad(width / 2f, height / 2f, width, height, 0f, 0f, 0f, 0.7f);
    }

    private void renderPauseMenuText(GameWorld world) {
        String title = "PAUSED";
        renderer.renderText(title, width / 2f - renderer.getTextWidth(title, 1.5f) / 2f, 
            height / 2f - 50f, 1.5f, 1f, 1f, 1f, 1f);
        
        String resume = "Press ESC to Resume";
        renderer.renderText(resume, width / 2f - renderer.getTextWidth(resume, 1.0f) / 2f, 
            height / 2f + 20f, 1.0f, 0.8f, 0.8f, 0.8f, 1f);
    }

    private void renderGameOverQuads(GameWorld world) {
        renderer.drawQuad(width / 2f, height / 2f, width, height, 0.2f, 0f, 0f, 0.85f);
    }

    private void renderGameOverText(GameWorld world) {
        String title = "GAME OVER";
        renderer.renderText(title, width / 2f - renderer.getTextWidth(title, 2.0f) / 2f, 
            height / 2f - 100f, 2.0f, 1f, 0.1f, 0.1f, 1f);
        
        String scoreText = "Final Score: " + world.score;
        renderer.renderText(scoreText, width / 2f - renderer.getTextWidth(scoreText, 1.2f) / 2f, 
            height / 2f, 1.2f, 1f, 1f, 1f, 1f);

        String restart = "Press SPACE to Restart";
        renderer.renderText(restart, width / 2f - renderer.getTextWidth(restart, 1.0f) / 2f, 
            height / 2f + 80f, 1.0f, 0.8f, 0.8f, 0.8f, 1f);
            
        String exit = "(Press ESC to Exit)";
        renderer.renderText(exit, width / 2f - renderer.getTextWidth(exit, 0.8f) / 2f, 
            height / 2f + 120f, 0.8f, 0.6f, 0.6f, 0.6f, 1f);
    
    }
    
    private String getGadgetTexturePath(String gadgetName) {
        switch (gadgetName) {
            case "Attack Damage":
                return AssetPaths.TEXTURE_UI_DAMAGE;
            case "Attack Speed":
                return AssetPaths.TEXTURE_UI_ATTACK_SPEED;
            case "Max HP":
                return AssetPaths.TEXTURE_UI_HEART;
            case "Movement Speed":
                return AssetPaths.TEXTURE_UI_MOVE_SPEED;
            case "Multi Attack":
                return AssetPaths.TEXTURE_UI_MULTISHOT;
            case "Life Steal":
                return AssetPaths.TEXTURE_UI_HEART_HALF;
            case "Orbit Blade":
                return AssetPaths.TEXTURE_BLADE;
            case "Laser Beam":
                return AssetPaths.TEXTURE_UI_LASER;
            default:
                return "";
        }
    }
}