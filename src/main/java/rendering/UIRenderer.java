package main.java.rendering;

import main.java.entities.FloatingText;
import main.java.systems.Gadget;
import main.java.world.GameWorld;

public class UIRenderer {
    private final Renderer renderer;
    private final int width, height;

    public UIRenderer(Renderer renderer, int width, int height) {
        this.renderer = renderer;
        this.width = width;
        this.height = height;
    }


    public void renderQuads(GameWorld world) {
        renderHUDQuads(world);
        if (world.levelUpMenuActive) {
            renderLevelUpMenuQuads(world);
        }
    }


    public void renderImmediate(GameWorld world, float camLeft, float camTop) {
        renderHUDImmediate(world);
        renderFloatingTexts(world, camLeft, camTop); 
        if (world.levelUpMenuActive) {
            renderLevelUpMenuImmediate(world);
        }
    }

    // --- FELBONTOTT HUD RENDERELÉS ---

    private void renderHUDQuads(GameWorld world) {
        // XP Bar (Csak a quadok)
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
    }

    private void renderHUDImmediate(GameWorld world) {
        // Score (Csak a szöveg)
        renderer.renderText("Score: " + world.score, 
            UIConstants.HUD_PADDING_X, UIConstants.HUD_SCORE_Y, 1.0f, 1f,1f,1f,1f);
        
        // Timer (Csak a szöveg)
        int totalSeconds = (int) Math.floor(world.elapsedTime);
        String timeStr = String.format("%02d:%02d", totalSeconds / 60, totalSeconds % 60);
        float textW = renderer.getTextWidth(timeStr, 1.0f);
        renderer.renderText(timeStr, width - UIConstants.HUD_TIMER_PADDING_X - textW, 
            UIConstants.HUD_SCORE_Y, 1.0f, 1f, 1f, 1f, 1f);

        // XP Bar (Csak a szöveg)
        String levelText = "Level: " + world.level;
        float levelTextWidth = renderer.getTextWidth(levelText, 1.0f);
        renderer.renderText(levelText, width / 2f - levelTextWidth / 2f, 
            UIConstants.XP_BAR_LEVEL_TEXT_Y, 1.0f, 1f, 1f, 1f, 1f);
    }
    


    
    private void renderFloatingTexts(GameWorld world, float camLeft, float camTop) {
        if (world.levelUpMenuActive) return;
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
            
            // Doboz quad
            renderer.drawQuad(x, startY, boxW, boxH, 0.4f, 0.4f, 0.4f, 1.0f);

            // Level squares
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

    private void renderLevelUpMenuImmediate(GameWorld world) {
        // Cím
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

            // Szövegek
            renderer.renderText(gadget.name, x - renderer.getTextWidth(gadget.name, UIConstants.LEVEL_UP_BOX_TITLE_SCALE) / 2f, 
                startY - boxH/2f + UIConstants.LEVEL_UP_BOX_TITLE_Y_OFFSET, UIConstants.LEVEL_UP_BOX_TITLE_SCALE, 1f, 1f, 1f, 1f);
            
            String effect = getNextLevelEffect(gadget);
            renderer.renderText(effect, x - renderer.getTextWidth(effect, UIConstants.LEVEL_UP_BOX_EFFECT_SCALE)/2f, 
                startY - boxH/2f + UIConstants.LEVEL_UP_BOX_EFFECT_Y_OFFSET, UIConstants.LEVEL_UP_BOX_EFFECT_SCALE, 0f, 1f, 0f, 1f);
            
            // Textúra
            String texturePath = getGadgetTexturePath(gadget.name);
            renderer.renderTexture(texturePath, x, startY + boxH/2f - UIConstants.LEVEL_UP_BOX_ICON_Y_FROM_BOTTOM, 
                UIConstants.LEVEL_UP_BOX_ICON_SIZE, UIConstants.LEVEL_UP_BOX_ICON_SIZE);
        }
    }

    // --- SEGÉDFÜGGVÉNYEK (VÁLTOZATLAN) ---
    
    private String getNextLevelEffect(Gadget gadget) {
        int nextLevel = gadget.level + 1;
        switch (gadget.name) {
            case "Attack Damage": return "Damage: +" + gadget.level + " -> +" + nextLevel;
            case "Attack Speed": return "Speed: +" + (gadget.level * 20) + "% -> +" + (nextLevel * 20) + "%";
            case "Max HP": return "Max HP: +" + gadget.level + " -> +" + nextLevel;
            case "Movement Speed": return "Speed: +" + (gadget.level * 10) + "% -> +" + (nextLevel * 10) + "%";
            case "Multi Attack": return "Projectiles: " + (gadget.level + 1) + " -> " + (nextLevel + 1);
            case "Life Steal": return "Chance: " + (gadget.level * 3) + "% -> " + (nextLevel * 3) + "%";
            case "Orbit Blade": return "Blades: " + (2 + gadget.level) + " -> " + (2 + nextLevel);
            case "Laser Beam": return "Cooldown: " + Math.max(1f, 9f - gadget.level * 2) + "s -> " + Math.max(1f, 9f - nextLevel * 2) + "s";
            default: return "Upgrade";
        }
    }
    
    private String getGadgetTexturePath(String gadgetName) {
        switch (gadgetName) {
            case "Attack Damage":
                return "src/main/assets/damage.png";
            case "Attack Speed":
                return "src/main/assets/attack_speed.png";
            case "Max HP":
                return "src/main/assets/heart.png";
            case "Movement Speed":
                return "src/main/assets/move_speed.png";
            case "Multi Attack":
                return "src/main/assets/multishot.png";
            case "Life Steal":
                return "src/main/assets/heart_half.png";
            case "Orbit Blade":
                return "src/main/assets/blade.png";
            case "Laser Beam":
                return "src/main/assets/laser.png";
            default:
                return "";
        }
    }
}