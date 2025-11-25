package main.java;

import main.java.entities.Player;
import main.java.systems.Gadget;
import main.java.systems.GadgetSystem;
import main.java.world.GameWorld;

import static org.lwjgl.glfw.GLFW.*;

public class InputHandler {
    private boolean keyUp, keyDown, keyLeft, keyRight, keySpace;
    private final GameWorld gameWorld;

    public InputHandler(long window, GameWorld gameWorld) {
        this.gameWorld = gameWorld;
        
        glfwSetKeyCallback(window, (win, key, scancode, action, mods) -> {
            if (key == GLFW_KEY_ESCAPE && action == GLFW_PRESS) {
                if (gameWorld.isGameOver()) {
                    glfwSetWindowShouldClose(win, true);
                } else if (!gameWorld.levelUpMenuActive) {
                    gameWorld.togglePause();
                }
            }
            
            if (key == GLFW_KEY_SPACE && action == GLFW_PRESS) {
                if (gameWorld.isGameOver()) {
                    gameWorld.reset();
                    return; 
                }
            }
            
            if (action == GLFW_PRESS && gameWorld.levelUpMenuActive) {
                Gadget selectedGadget = null;
                int availableCount = gameWorld.availableGadgets.size();

                if (key == GLFW_KEY_1 && availableCount >= 1) {
                    selectedGadget = gameWorld.availableGadgets.get(0);
                } else if (key == GLFW_KEY_2 && availableCount >= 2) {
                    selectedGadget = gameWorld.availableGadgets.get(1);
                } else if (key == GLFW_KEY_3 && availableCount >= 3) {
                    selectedGadget = gameWorld.availableGadgets.get(2);
                }

                if (selectedGadget != null) {
                    gameWorld.selectGadget(selectedGadget);
                    return; 
                }
            }

            boolean pressed = action == GLFW_PRESS || action == GLFW_REPEAT;
            if (key == GLFW_KEY_W) keyUp = pressed;
            if (key == GLFW_KEY_S) keyDown = pressed;
            if (key == GLFW_KEY_A) keyLeft = pressed;
            if (key == GLFW_KEY_D) keyRight = pressed;
            if (key == GLFW_KEY_SPACE) keySpace = pressed;
        });

        glfwSetMouseButtonCallback(window, (win, button, action, mods) -> {
            if (button == GLFW_MOUSE_BUTTON_LEFT && action == GLFW_PRESS) {
                double[] xpos = new double[1];
                double[] ypos = new double[1];
                glfwGetCursorPos(window, xpos, ypos);
                handleMouseClick((float)xpos[0], (float)ypos[0]);
            }
        });
    }

    public void processInput(Player player) {
        if (gameWorld.levelUpMenuActive) return;

        float moveX = 0, moveY = 0;
        if (keyUp) moveY -= 1;
        if (keyDown) moveY += 1;
        if (keyLeft) moveX -= 1;
        if (keyRight) moveX += 1;
        if (keySpace) GadgetSystem.activateMagnet(gameWorld);

        player.setMovementDirection(moveX, moveY);
    }
    
    private void handleMouseClick(float mouseX, float mouseY) {
        if (gameWorld.levelUpMenuActive) {
            float boxW = 220f, boxH = 280f, gap = 20f;
            float centerX = 800 / 2f; 
            float startY = 600 / 2f; 

            for (int i = 0; i < gameWorld.availableGadgets.size(); i++) {
                float x = centerX + (i - (gameWorld.availableGadgets.size() - 1) / 2f) * (boxW + gap);
                
                if (mouseX >= x - boxW/2f && mouseX <= x + boxW/2f && 
                    mouseY >= startY - boxH/2f && mouseY <= startY + boxH/2f) {
                    
                    Gadget selected = gameWorld.availableGadgets.get(i);
                    gameWorld.selectGadget(selected);
                    break;
                }
            }
        }
    }
}