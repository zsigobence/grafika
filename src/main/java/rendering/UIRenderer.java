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

    public void render(GameWorld world, float camLeft, float camTop) {
        renderHUD(world);
        renderGadgetsInfo(world);
        renderFloatingTexts(world, camLeft, camTop);
        if (world.levelUpMenuActive) {
            renderLevelUpMenu(world);
        }
    }
    
    private void renderHUD(GameWorld world) {
        // Score
        renderer.renderText("Score: " + world.score, 20, 40, 1.0f, 1f,1f,1f,1f);
        
        // Timer
        int totalSeconds = (int) Math.floor(world.elapsedTime);
        String timeStr = String.format("%02d:%02d", totalSeconds / 60, totalSeconds % 60);
        float textW = getTextWidth(timeStr, 1.0f);
        renderer.renderText(timeStr, width - 20 - textW, 40, 1.0f, 1f, 1f, 1f, 1f);

        // XP Bar
        String levelText = "Level: " + world.level;
        float levelTextWidth = getTextWidth(levelText, 1.0f);
        renderer.renderText(levelText, width / 2f - levelTextWidth / 2f, 18f, 1.0f, 1f, 1f, 1f, 1f);
        
        float barW = 220f, barH = 18f;
        float barX = width / 2.0f - barW / 2f;
        float barY = 18f + 26f;

        renderer.drawQuad(barX + barW/2f, barY + barH/2f, barW, barH, 0.85f, 0.85f, 0.87f, 1.0f);
        float percent = world.xpToNext > 0 ? (float) world.xp / world.xpToNext : 0f;
        float fillW = barW * percent;
        if (fillW > 0.001f) {
            renderer.drawQuad(barX + fillW/2f, barY + barH/2f, fillW, barH - 2, 0.2f + percent * 1.2f, 0.9f - percent * 0.2f, 0.05f, 1f);
        }
    }
    
    private void renderGadgetsInfo(GameWorld world) {
        float startY = height - 100f;
        renderer.renderText("Gadgets:", 20f, startY, 0.8f, 1f, 1f, 1f, 1f);
        int drawn = 0;
        for (Gadget g : world.getGadgets()) {
            if (g.level > 0) {
                String text = g.name + ": " + g.level + "/" + g.maxLevel;
                renderer.renderText(text, 20f, startY + (drawn + 1) * 15f, 0.7f, 0.8f, 0.8f, 0.8f, 1f);
                drawn++;
            }
        }
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

    private void renderLevelUpMenu(GameWorld world) {
        renderer.drawQuad(width / 2f, height / 2f, width, height, 0f, 0f, 0f, 0.7f);
        String title = "LEVEL UP! Choose a gadget:";
        renderer.renderText(title, width / 2f - getTextWidth(title, 1.2f) / 2f, 60f, 1.2f, 1f, 1f, 0f, 1f);

        float boxW = 220f, boxH = 280f, gap = 20f;
        float centerX = width / 2f, startY = height / 2f;

        for (int i = 0; i < world.availableGadgets.size(); i++) {
            Gadget gadget = world.availableGadgets.get(i);
            float x = centerX + (i - (world.availableGadgets.size() - 1) / 2f) * (boxW + gap);
            
            renderer.drawQuad(x, startY, boxW, boxH, 0.2f, 0.4f, 0.8f, 1.0f);

            // Text and level indicators
            renderer.renderText(gadget.name, x - getTextWidth(gadget.name, 0.9f) / 2f, startY - boxH/2f + 20f, 0.9f, 1f, 1f, 1f, 1f);
            
            String effect = getNextLevelEffect(gadget);
            renderer.renderText(effect, x - getTextWidth(effect, 0.7f)/2f, startY - boxH/2f + 80f, 0.7f, 0f, 1f, 0f, 1f);
            
            // Level squares
            int max = gadget.maxLevel;
            float squareSize = 16f, squareGap = 6f;
            float totalWidth = max * squareSize + (max - 1) * squareGap;
            float startSqX = x - totalWidth / 2f;
            for (int s = 0; s < max; s++) {
                float sx = startSqX + s * (squareSize + squareGap) + squareSize/2f;
                if (s < gadget.level) renderer.drawQuad(sx, startY, squareSize, squareSize, 1.0f, 0.85f, 0.05f, 1.0f);
                else if (s == gadget.level) renderer.drawQuad(sx, startY, squareSize, squareSize, 1.0f, 0.85f, 0.05f, 0.4f);
                else renderer.drawQuad(sx, startY, squareSize, squareSize, 0.05f, 0.05f, 0.05f, 1.0f);
            }
        }
    }
    
    private float getTextWidth(String text, float scale) {
        return text.length() * 10f * scale; // Approximate
    }

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
}