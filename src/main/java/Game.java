package main.java;

import main.java.audio.SoundManager;
import main.java.rendering.Renderer;
import main.java.world.GameWorld;
import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryStack;
import main.java.config.Config;

import java.nio.IntBuffer;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11C.GL_VERSION;
import static org.lwjgl.opengl.GL11C.glGetString;
import static org.lwjgl.system.MemoryUtil.NULL;

public class Game {
    private long window;

    private GameWorld gameWorld;
    private Renderer renderer;
    private InputHandler inputHandler;
    
    public static final int TARGET_UPS = 60; 
    public static final double FIXED_TIMESTEP = 1.0 / TARGET_UPS;

    private double accumulator = 0.0;

    public static void main(String[] args) {
        System.out.println("Program elindult");
        new Game().run();
    }

    public void run() {
        try {
            init();
            loop();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            cleanup();
        }
    }

    private void init() throws Exception {
        System.out.println("Inicializálás...");
        if (!glfwInit()) throw new IllegalStateException("Nem sikerült inicializálni a GLFW-t");

        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_FALSE);

        window = glfwCreateWindow(Config.WINDOW_WIDTH, 
                Config.WINDOW_HEIGHT, 
                Config.WINDOW_TITLE, 
                NULL, NULL);
        if (window == NULL) throw new RuntimeException("Nem sikerült létrehozni az ablakot");

        // Ablak középre igazítása a monitoron
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer pWidth = stack.mallocInt(1);
            IntBuffer pHeight = stack.mallocInt(1);
            glfwGetWindowSize(window, pWidth, pHeight);
            GLFWVidMode vidmode = glfwGetVideoMode(glfwGetPrimaryMonitor());
            if (vidmode != null) {
                glfwSetWindowPos(window, (vidmode.width() - pWidth.get(0)) / 2, (vidmode.height() - pHeight.get(0)) / 2);
            }
        }

        glfwMakeContextCurrent(window);
        glfwSwapInterval(1); // V-Sync bekapcsolása
        glfwShowWindow(window);
        GL.createCapabilities();
        System.out.println("OpenGL verzió: " + glGetString(GL_VERSION));

        SoundManager.init();
        
        // Fő rendszerek példányosítása
        gameWorld = new GameWorld();
        renderer = new Renderer(Config.WINDOW_WIDTH, Config.WINDOW_HEIGHT);
        inputHandler = new InputHandler(window, gameWorld);
        
        gameWorld.init();
        renderer.init();

        System.out.println("Inicializálás kész");
    }

    private void loop() {
        double lastTime = glfwGetTime();
        while (!glfwWindowShouldClose(window)) {
            double currentTime = glfwGetTime();
            float deltaTime = (float) (currentTime - lastTime);
            lastTime = currentTime;
            
            accumulator += deltaTime;
            inputHandler.processInput(gameWorld.getPlayer());
            glfwPollEvents();

            // Fizikai frissítés fix időlépéssel
            while (accumulator >= FIXED_TIMESTEP) {
                gameWorld.update((float) FIXED_TIMESTEP);
                accumulator -= FIXED_TIMESTEP;
            }
            
            renderer.render(gameWorld);
            glfwSwapBuffers(window);
        }
    }

    private void cleanup() {
        SoundManager.cleanup();
        renderer.cleanup();
        glfwDestroyWindow(window);
        glfwTerminate();
    }
}