package main.java.entities;

import static org.lwjgl.glfw.GLFW.glfwGetTime;

public class XPOrb extends GameObject {
    public final int value;
    private final float magnetRadius = 70f;
    private final float magnetBaseSpeed = 90f;
    private final float magnetPullForce = 200f;

    public XPOrb(float x, float y, int value) {
        super(x, y, 10);
        this.value = value;
    }
    
    public void update(float deltaTime, Player player) {
        float dx = player.x - x;
        float dy = player.y - y;
        float dist = (float) Math.hypot(dx, dy);

        if (dist < magnetRadius && dist > 1f) {
            // Mágneses vonzás
            float pullExtra = (magnetRadius - dist) / magnetRadius * magnetPullForce;
            float speedToPlayer = magnetBaseSpeed + pullExtra;
            x += dx / dist * speedToPlayer * deltaTime;
            y += dy / dist * speedToPlayer * deltaTime;
        } else {
            // Lebegés
            y += Math.sin(glfwGetTime() * 3.0f + this.hashCode()) * 6.0f * deltaTime;
        }
    }
}