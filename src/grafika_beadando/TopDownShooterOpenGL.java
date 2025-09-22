package grafika_beadando;

import org.lwjgl.*;
import org.lwjgl.glfw.*;
import org.lwjgl.opengl.*;
import org.lwjgl.system.*;

import java.nio.*;
import java.util.*;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33C.*;
import static org.lwjgl.system.MemoryStack.*;
import static org.lwjgl.system.MemoryUtil.*;

public class TopDownShooterOpenGL {

    private long window;
    private int width = 800, height = 600;
    private int program;
    private int vao, vbo, ebo;
    private int uniProjection;
    private int uniModel;
    private int uniColor;
    private Player player;
    private List<Bullet> bullets = new ArrayList<>();
    private List<Enemy> enemies = new ArrayList<>();
    private double lastTime;
    private boolean keyUp, keyDown, keyLeft, keyRight, keyShoot;
    private double enemySpawnTimer = 0.0;

    public static void main(String[] args) {
        System.out.println("Program elindult");
        new TopDownShooterOpenGL().run();
    }

    public void run() {
        try {
            init();
            loop();
        } catch (Exception e) {
            System.err.println("Hiba történt: " + e.getMessage());
            e.printStackTrace();
        } finally {
            cleanup();
        }
    }

    private void init() {
        System.out.println("Inicializálás...");
        
        // GLFW inicializálás
        if (!glfwInit()) {
            throw new IllegalStateException("Nem sikerült inicializálni a GLFW-t");
        }

        // Ablak konfigurálása
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_FALSE);

        window = glfwCreateWindow(width, height, "Top-Down Shooter", NULL, NULL);
        if (window == NULL) {
            throw new RuntimeException("Nem sikerült létrehozni az ablakot");
        }

        // Billentyűzet callback
        glfwSetKeyCallback(window, (win, key, scancode, action, mods) -> {
            if (key == GLFW_KEY_ESCAPE && action == GLFW_PRESS) {
                glfwSetWindowShouldClose(win, true);
            }
            
            boolean pressed = action == GLFW_PRESS || action == GLFW_REPEAT;
            if (key == GLFW_KEY_W) keyUp = pressed;
            if (key == GLFW_KEY_S) keyDown = pressed;
            if (key == GLFW_KEY_A) keyLeft = pressed;
            if (key == GLFW_KEY_D) keyRight = pressed;
            if (key == GLFW_KEY_SPACE) keyShoot = pressed;
        });

        // Ablak pozícionálása a képernyő közepére
        try (MemoryStack stack = stackPush()) {
            IntBuffer pWidth = stack.mallocInt(1);
            IntBuffer pHeight = stack.mallocInt(1);
            glfwGetWindowSize(window, pWidth, pHeight);
            GLFWVidMode vidmode = glfwGetVideoMode(glfwGetPrimaryMonitor());
            glfwSetWindowPos(
                window,
                (vidmode.width() - pWidth.get(0)) / 2,
                (vidmode.height() - pHeight.get(0)) / 2
            );
        }

        glfwMakeContextCurrent(window);
        glfwSwapInterval(1); // Vsync bekapcsolása
        glfwShowWindow(window);

        // OpenGL kontextus létrehozása
        GL.createCapabilities();
        System.out.println("OpenGL verzió: " + glGetString(GL_VERSION));

        // Shaderek létrehozása
        System.out.println("Shaderek létrehozása...");
        program = createProgram();
        uniProjection = glGetUniformLocation(program, "uProjection");
        uniModel = glGetUniformLocation(program, "uModel");
        uniColor = glGetUniformLocation(program, "uColor");
        
        System.out.println("Shader uniformok: proj=" + uniProjection + ", model=" + uniModel + ", color=" + uniColor);

        // Vertex buffer létrehozása
        float[] vertices = {
            -0.5f, -0.5f,  // bal alsó
             0.5f, -0.5f,  // jobb alsó
             0.5f,  0.5f,  // jobb felső
            -0.5f,  0.5f   // bal felső
        };

        int[] indices = {
            0, 1, 2,  // első háromszög
            2, 3, 0   // második háromszög
        };

        vao = glGenVertexArrays();
        vbo = glGenBuffers();
        ebo = glGenBuffers();
        
        System.out.println("VAO: " + vao + ", VBO: " + vbo + ", EBO: " + ebo);

        glBindVertexArray(vao);

        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, vertices, GL_STATIC_DRAW);

        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ebo);
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, indices, GL_STATIC_DRAW);

        glVertexAttribPointer(0, 2, GL_FLOAT, false, 2 * Float.BYTES, 0);
        glEnableVertexAttribArray(0);

        glBindVertexArray(0);

        // Játék inicializálása
        player = new Player(width / 2.0f, height / 2.0f);
        lastTime = glfwGetTime();

        // Alpha blending
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        
        System.out.println("Inicializálás kész");
    }

    private void loop() {
        System.out.println("Fő ciklus indítása");
        
        // Ellenőrizzük, hogy a shader program érvényes-e
        if (program == 0) {
            System.err.println("Hibás shader program!");
            return;
        }
        
        while (!glfwWindowShouldClose(window)) {
            double currentTime = glfwGetTime();
            float deltaTime = (float) (currentTime - lastTime);
            lastTime = currentTime;

            update(deltaTime);
            render();

            glfwSwapBuffers(window);
            glfwPollEvents();
        }
        
        System.out.println("Fő ciklus vége");
    }

    private void update(float deltaTime) {
        // Játékos mozgása
        float speed = 200.0f;
        float moveX = 0.0f;
        float moveY = 0.0f;

        if (keyUp) moveY -= 1.0f;
        if (keyDown) moveY += 1.0f;
        if (keyLeft) moveX -= 1.0f;
        if (keyRight) moveX += 1.0f;

        // Normalizálás
        if (moveX != 0.0f || moveY != 0.0f) {
            float length = (float) Math.sqrt(moveX * moveX + moveY * moveY);
            moveX /= length;
            moveY /= length;
        }

        player.x += moveX * speed * deltaTime;
        player.y += moveY * speed * deltaTime;

        // Játékos szélekhez kötése
        player.x = Math.max(player.size / 2, Math.min(width - player.size / 2, player.x));
        player.y = Math.max(player.size / 2, Math.min(height - player.size / 2, player.y));

        // Lövés
        player.shootCooldown -= deltaTime;
        if (keyShoot && player.shootCooldown <= 0.0f) {
            bullets.add(new Bullet(player.x, player.y, 0.0f, -400.0f));
            player.shootCooldown = 0.2f;
        }

        // Lövedékek frissítése
        Iterator<Bullet> bulletIterator = bullets.iterator();
        while (bulletIterator.hasNext()) {
            Bullet bullet = bulletIterator.next();
            bullet.update(deltaTime);
            
            // Lövedék törlése ha kiment a képernyőről
            if (bullet.x < -10 || bullet.x > width + 10 || 
                bullet.y < -10 || bullet.y > height + 10) {
                bulletIterator.remove();
            }
            
        }

        // Ellenségek spawnolása
        enemySpawnTimer += deltaTime;
        if (enemySpawnTimer > 1.0) {
            enemySpawnTimer = 0.0;
            float x = (float) (Math.random() * (width - 40) + 20);
            float speedY = 80.0f + (float) (Math.random() * 40.0f);
            enemies.add(new Enemy(x, -20.0f, 0.0f, speedY));
        }

        // Ellenségek frissítése
        Iterator<Enemy> enemyIterator = enemies.iterator();
        while (enemyIterator.hasNext()) {
            Enemy enemy = enemyIterator.next();
            enemy.update(deltaTime);
            
            // Ellenség törlése ha kiment a képernyő aljáról
            if (enemy.y > height + 40) {
                enemyIterator.remove();
                continue;
            }
            
            // Ütközés ellenőrzése lövedékekkel
            Iterator<Bullet> bulletIterator2 = bullets.iterator();
            while (bulletIterator2.hasNext()) {
                Bullet bullet = bulletIterator2.next();
                if (checkCollision(enemy, bullet)) {
                    bulletIterator2.remove();
                    enemy.hp -= 1; // sebzés
                    if (enemy.isDead()) {
                        enemyIterator.remove();
                    }
                    break;
                }

            }
            
            // Ütközés ellenőrzése játékossal
            if (checkCollision(enemy, player)) {
                player.hp -= 1;
                enemyIterator.remove();

                if (player.isDead()) {
                    System.out.println("Game Over!");
                    // Reset: pl. ablak bezárása vagy újrakezdés
                    glfwSetWindowShouldClose(window, true);
                } 
            }
        }
    }

    private void render() {
        // Háttérszín beállítása
        glClearColor(0.08f, 0.08f, 0.12f, 1.0f);
        glClear(GL_COLOR_BUFFER_BIT);
        
        // Shader program használata - FONTOS: minden frame-nél be kell állítani!
        glUseProgram(program);
        
        // Projection mátrix beállítása
        float[] projectionMatrix = ortho(0.0f, width, height, 0.0f, -1.0f, 1.0f);
        glUniformMatrix4fv(uniProjection, false, projectionMatrix);

        glBindVertexArray(vao);

        // Játékos renderelése
        float[] modelMatrix = createModelMatrix(player.x, player.y, player.size, player.size);
        glUniformMatrix4fv(uniModel, false, modelMatrix);
        glUniform4f(uniColor, 0.2f, 0.9f, 0.2f, 1.0f);
        glDrawElements(GL_TRIANGLES, 6, GL_UNSIGNED_INT, 0);
        renderHealthBar(player); 

        // Lövedékek renderelése
        for (Bullet bullet : bullets) {
            modelMatrix = createModelMatrix(bullet.x, bullet.y, bullet.size, bullet.size);
            glUniformMatrix4fv(uniModel, false, modelMatrix);
            glUniform4f(uniColor, 1.0f, 0.9f, 0.2f, 1.0f);
            glDrawElements(GL_TRIANGLES, 6, GL_UNSIGNED_INT, 0);
        }

        // Ellenségek renderelése
        for (Enemy enemy : enemies) {
            modelMatrix = createModelMatrix(enemy.x, enemy.y, enemy.size, enemy.size);
            glUniformMatrix4fv(uniModel, false, modelMatrix);
            glUniform4f(uniColor, 0.9f, 0.2f, 0.2f, 1.0f);
            glDrawElements(GL_TRIANGLES, 6, GL_UNSIGNED_INT, 0);
            renderHealthBar(enemy);
        }

        glBindVertexArray(0);
        // NE kapcsoljuk ki a shader programot itt, mert a következő frame-nél szükség lesz rá
        // glUseProgram(0); - EZT KOMMENTÁLTAM KI
        
        // OpenGL hiba ellenőrzése
        int error = glGetError();
        if (error != GL_NO_ERROR) {
            System.err.println("OpenGL hiba: " + error);
        }
    }

    private void cleanup() {
        System.out.println("Takarítás...");
        
        glDeleteBuffers(ebo);
        glDeleteBuffers(vbo);
        glDeleteVertexArrays(vao);
        glDeleteProgram(program);
        
        glfwDestroyWindow(window);
        glfwTerminate();
        
        System.out.println("Takarítás kész");
    }

    private int createProgram() {
        int vertexShader = glCreateShader(GL_VERTEX_SHADER);
        glShaderSource(vertexShader, vertexShaderSource);
        glCompileShader(vertexShader);
        
        // Shader fordítási hibák ellenőrzése
        int success = glGetShaderi(vertexShader, GL_COMPILE_STATUS);
        if (success == GL_FALSE) {
            String log = glGetShaderInfoLog(vertexShader);
            System.err.println("Vertex shader fordítási hiba: " + log);
            glDeleteShader(vertexShader);
            return 0;
        }
        
        int fragmentShader = glCreateShader(GL_FRAGMENT_SHADER);
        glShaderSource(fragmentShader, fragmentShaderSource);
        glCompileShader(fragmentShader);
        
        // Shader fordítási hibák ellenőrzése
        success = glGetShaderi(fragmentShader, GL_COMPILE_STATUS);
        if (success == GL_FALSE) {
            String log = glGetShaderInfoLog(fragmentShader);
            System.err.println("Fragment shader fordítási hiba: " + log);
            glDeleteShader(vertexShader);
            glDeleteShader(fragmentShader);
            return 0;
        }
        
        int program = glCreateProgram();
        glAttachShader(program, vertexShader);
        glAttachShader(program, fragmentShader);
        glLinkProgram(program);
        
        // Program linkelési hibák ellenőrzése
        success = glGetProgrami(program, GL_LINK_STATUS);
        if (success == GL_FALSE) {
            String log = glGetProgramInfoLog(program);
            System.err.println("Program linkelési hiba: " + log);
            glDeleteShader(vertexShader);
            glDeleteShader(fragmentShader);
            glDeleteProgram(program);
            return 0;
        }
        
        glDeleteShader(vertexShader);
        glDeleteShader(fragmentShader);
        
        return program;
    }

    private float[] ortho(float left, float right, float bottom, float top, float near, float far) {
        float[] matrix = new float[16];
        matrix[0] = 2.0f / (right - left);
        matrix[5] = 2.0f / (top - bottom);
        matrix[10] = -2.0f / (far - near);
        matrix[12] = -(right + left) / (right - left);
        matrix[13] = -(top + bottom) / (top - bottom);
        matrix[14] = -(far + near) / (far - near);
        matrix[15] = 1.0f;
        return matrix;
    }

    private float[] createModelMatrix(float x, float y, float width, float height) {
        return new float[]{
            width, 0,     0, 0,
            0,     height,0, 0,
            0,     0,     1, 0,
            x,     y,     0, 1
        };
    }

    private boolean checkCollision(Entity a, Entity b) {
        return Math.abs(a.x - b.x) < (a.size + b.size) / 2.0f &&
               Math.abs(a.y - b.y) < (a.size + b.size) / 2.0f;
    }
    
    
    private void HealthBar(Entity e) {
        float barWidth = e.size;
        float barHeight = 5.0f;
        float healthPercent = Math.max(0, e.hp) / 5.0f; // ha max HP = 5, skálázható

        float[] modelMatrix = createModelMatrix(e.x, e.y - e.size / 2 - 10, barWidth, barHeight);
        glUniformMatrix4fv(uniModel, false, modelMatrix);
        glUniform4f(uniColor, 0.8f, 0.0f, 0.0f, 1.0f); // piros háttér
        glDrawElements(GL_TRIANGLES, 6, GL_UNSIGNED_INT, 0);

        modelMatrix = createModelMatrix(e.x - barWidth * (1.0f - healthPercent) / 2, 
                                        e.y - e.size / 2 - 10,
                                        barWidth * healthPercent, barHeight);
        glUniformMatrix4fv(uniModel, false, modelMatrix);
        glUniform4f(uniColor, 0.0f, 0.8f, 0.0f, 1.0f); // zöld életsáv
        glDrawElements(GL_TRIANGLES, 6, GL_UNSIGNED_INT, 0);
    }


    private static abstract class Entity {
        float x, y;
        float size;
        int hp;
        int maxHp;

        Entity(float x, float y, float size, int hp) {
            this.x = x;
            this.y = y;
            this.size = size;
            this.hp = hp;
            this.maxHp = hp; // induláskor a maxHp = kezdeti hp
        }

        boolean isDead() {
            return hp <= 0;
        }
    }



    private static class Player extends Entity {
        float shootCooldown = 0.0f;

        Player(float x, float y) {
            super(x, y, 32.0f, 5); // pl. 5 HP
        }
    }


    private static class Bullet extends Entity {
        float vx, vy;
        
        Bullet(float x, float y, float vx, float vy) {
            super(x, y, 8.0f,1);
            this.vx = vx;
            this.vy = vy;
        }
        
        void update(float deltaTime) {
            x += vx * deltaTime;
            y += vy * deltaTime;
        }
    }

    private static class Enemy extends Entity {
        float vx, vy;

        Enemy(float x, float y, float vx, float vy) {
            super(x, y, 28.0f, 3); // pl. 3 HP
            this.vx = vx;
            this.vy = vy;
        }

        void update(float deltaTime) {
            x += vx * deltaTime;
            y += vy * deltaTime;
        }
    }
    private void renderHealthBar(Entity e) {
        float barWidth = e.size;
        float barHeight = 5.0f;
        float healthPercent = Math.max(0, (float)e.hp / (float)e.maxHp);

        // piros háttér
        float[] modelMatrix = createModelMatrix(e.x, e.y - e.size / 2 - 10, barWidth, barHeight);
        glUniformMatrix4fv(uniModel, false, modelMatrix);
        glUniform4f(uniColor, 0.8f, 0.0f, 0.0f, 1.0f);
        glDrawElements(GL_TRIANGLES, 6, GL_UNSIGNED_INT, 0);

        // zöld kitöltés (arányosan a HP-hoz)
        modelMatrix = createModelMatrix(
            e.x - barWidth * (1.0f - healthPercent) / 2,
            e.y - e.size / 2 - 10,
            barWidth * healthPercent,
            barHeight
        );
        glUniformMatrix4fv(uniModel, false, modelMatrix);
        glUniform4f(uniColor, 0.0f, 0.8f, 0.0f, 1.0f);
        glDrawElements(GL_TRIANGLES, 6, GL_UNSIGNED_INT, 0);
    }



    private static final String vertexShaderSource =
        "#version 330 core\n" +
        "layout (location = 0) in vec2 aPos;\n" +
        "uniform mat4 uModel;\n" +
        "uniform mat4 uProjection;\n" +
        "void main() {\n" +
        "    gl_Position = uProjection * uModel * vec4(aPos, 0.0, 1.0);\n" +
        "}\n";

    private static final String fragmentShaderSource =
        "#version 330 core\n" +
        "out vec4 FragColor;\n" +
        "uniform vec4 uColor;\n" +
        "void main() {\n" +
        "    FragColor = uColor;\n" +
        "}\n";
}