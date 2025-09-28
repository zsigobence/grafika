package grafika_beadando;

import org.lwjgl.*;
import org.lwjgl.glfw.*;
import org.lwjgl.opengl.*;
import org.lwjgl.stb.STBTTAlignedQuad;
import org.lwjgl.stb.STBTTBakedChar;
import org.lwjgl.system.*;

import java.nio.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33C.*;
import static org.lwjgl.system.MemoryStack.*;
import static org.lwjgl.system.MemoryUtil.*;
import static org.lwjgl.stb.STBTruetype.*;

public class TopDownShooterOpenGL {

    private long window;
    private int width = 800, height = 600;
    private int program, textProgram;
    private int vao, vbo, ebo;
    private int uniProjection, uniModel, uniColor;
    private int fontTexture, textVAO, textVBO;
    private STBTTBakedChar.Buffer cdata;
    private int score = 0;

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

        window = glfwCreateWindow(width, height, "Top-Down Shooter", NULL, NULL);
        if (window == NULL) throw new RuntimeException("Nem sikerült létrehozni az ablakot");

        glfwSetKeyCallback(window, (win, key, scancode, action, mods) -> {
            if (key == GLFW_KEY_ESCAPE && action == GLFW_PRESS)
                glfwSetWindowShouldClose(win, true);

            boolean pressed = action == GLFW_PRESS || action == GLFW_REPEAT;
            if (key == GLFW_KEY_W) keyUp = pressed;
            if (key == GLFW_KEY_S) keyDown = pressed;
            if (key == GLFW_KEY_A) keyLeft = pressed;
            if (key == GLFW_KEY_D) keyRight = pressed;
            if (key == GLFW_KEY_SPACE) keyShoot = pressed;
        });

        try (MemoryStack stack = stackPush()) {
            IntBuffer pWidth = stack.mallocInt(1);
            IntBuffer pHeight = stack.mallocInt(1);
            glfwGetWindowSize(window, pWidth, pHeight);
            GLFWVidMode vidmode = glfwGetVideoMode(glfwGetPrimaryMonitor());
            glfwSetWindowPos(window,
                    (vidmode.width() - pWidth.get(0)) / 2,
                    (vidmode.height() - pHeight.get(0)) / 2);
        }

        glfwMakeContextCurrent(window);
        glfwSwapInterval(1);
        glfwShowWindow(window);

        GL.createCapabilities();
        System.out.println("OpenGL verzió: " + glGetString(GL_VERSION));

        // Shaderek
        program = createProgram();
        uniProjection = glGetUniformLocation(program, "uProjection");
        uniModel = glGetUniformLocation(program, "uModel");
        uniColor = glGetUniformLocation(program, "uColor");

        float[] vertices = {
                -0.5f, -0.5f,
                0.5f, -0.5f,
                0.5f, 0.5f,
                -0.5f, 0.5f
        };
        int[] indices = {0, 1, 2, 2, 3, 0};

        vao = glGenVertexArrays();
        vbo = glGenBuffers();
        ebo = glGenBuffers();

        glBindVertexArray(vao);

        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, vertices, GL_STATIC_DRAW);

        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ebo);
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, indices, GL_STATIC_DRAW);

        glVertexAttribPointer(0, 2, GL_FLOAT, false, 2 * Float.BYTES, 0);
        glEnableVertexAttribArray(0);
        glBindVertexArray(0);

        player = new Player(width / 2.0f, height / 2.0f);
        lastTime = glfwGetTime();

        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        textProgram = createTextProgram();
        initFont();

        // Text VAO/VBO egyszer létrehozva
        textVAO = glGenVertexArrays();
        textVBO = glGenBuffers();
        glBindVertexArray(textVAO);
        glBindBuffer(GL_ARRAY_BUFFER, textVBO);
        glVertexAttribPointer(0, 2, GL_FLOAT, false, 4 * Float.BYTES, 0);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(1, 2, GL_FLOAT, false, 4 * Float.BYTES, 2 * Float.BYTES);
        glEnableVertexAttribArray(1);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(0);

        System.out.println("Inicializálás kész");
    }

    private void loop() {
        while (!glfwWindowShouldClose(window)) {
            double currentTime = glfwGetTime();
            float deltaTime = (float) (currentTime - lastTime);
            lastTime = currentTime;

            update(deltaTime);
            render();

            glfwSwapBuffers(window);
            glfwPollEvents();
        }
    }

    private void update(float deltaTime) {
        float speed = 200.0f;
        float moveX = 0, moveY = 0;
        if (keyUp) moveY -= 1;
        if (keyDown) moveY += 1;
        if (keyLeft) moveX -= 1;
        if (keyRight) moveX += 1;

        if (moveX != 0 || moveY != 0) {
            float len = (float) Math.sqrt(moveX * moveX + moveY * moveY);
            moveX /= len;
            moveY /= len;
        }

        player.x += moveX * speed * deltaTime;
        player.y += moveY * speed * deltaTime;

        player.x = Math.max(player.size / 2, Math.min(width - player.size / 2, player.x));
        player.y = Math.max(player.size / 2, Math.min(height - player.size / 2, player.y));

        // Lövés
        player.shootCooldown -= deltaTime;
        if (keyShoot && player.shootCooldown <= 0) {
            bullets.add(new Bullet(player.x, player.y, 0.0f, -400.0f));
            player.shootCooldown = 0.2f;
        }

        bullets.removeIf(b -> {
            b.update(deltaTime);
            return b.x < -10 || b.x > width + 10 || b.y < -10 || b.y > height + 10;
        });

     // Ellenségek spawnolása
        enemySpawnTimer += deltaTime;
        if (enemySpawnTimer > 1.0) {
            enemySpawnTimer = 0.0;
            float x;
            float speedY;
            EnemyType type;

            double r = Math.random();
            if (r < 0.6) {         // BASIC 60%
                type = EnemyType.BASIC;
                speedY = 80.0f + (float)(Math.random() * 40);
                x = (float) (Math.random() * (width - 40) + 20);
            } else if (r < 0.85) {  // FAST 25%
                type = EnemyType.FAST;
                speedY = 150.0f + (float)(Math.random() * 50);
                x = (float) (Math.random() * (width - 60) + 30); // ne ütközzenek
            } else {                // TANK 15%
                type = EnemyType.TANK;
                speedY = 50.0f + (float)(Math.random() * 30);
                x = (float) (Math.random() * (width - 80) + 40);
            }

            enemies.add(new Enemy(x, -20.0f, 0.0f, speedY, type));
        }



        Iterator<Enemy> enemyIterator = enemies.iterator();
        while (enemyIterator.hasNext()) {
            Enemy enemy = enemyIterator.next();
            enemy.update(deltaTime);
            if (enemy.y > height + 40) {
                enemyIterator.remove();
                continue;
            }

            Iterator<Bullet> bulletIter = bullets.iterator();
            while (bulletIter.hasNext()) {
                Bullet b = bulletIter.next();
                if (checkCollision(enemy, b)) {
                    bulletIter.remove();
                    enemy.hp--;
                    if (enemy.isDead()) {
                        enemyIterator.remove();
                        score += 10;
                    }
                    break;
                }
            }

            if (checkCollision(enemy, player)) {
                player.hp--;
                enemyIterator.remove();
                if (player.isDead()) {
                    System.out.println("Game Over!");
                    glfwSetWindowShouldClose(window, true);
                }
            }
        }
    }

    private void render() {
        glClearColor(0.08f, 0.08f, 0.12f, 1.0f);
        glClear(GL_COLOR_BUFFER_BIT);

        glUseProgram(program);
        glUniformMatrix4fv(uniProjection, false, ortho(0, width, height, 0, -1, 1));
        glBindVertexArray(vao);

        // Player
        glUniformMatrix4fv(uniModel, false, createModelMatrix(player.x, player.y, player.size, player.size));
        glUniform4f(uniColor, 0.2f, 0.9f, 0.2f, 1.0f);
        glDrawElements(GL_TRIANGLES, 6, GL_UNSIGNED_INT, 0);
        renderHealthBar(player);

        // Bullets
        for (Bullet b : bullets) {
            glUniformMatrix4fv(uniModel, false, createModelMatrix(b.x, b.y, b.size, b.size));
            glUniform4f(uniColor, 1.0f, 0.9f, 0.2f, 1.0f);
            glDrawElements(GL_TRIANGLES, 6, GL_UNSIGNED_INT, 0);
        }

        // Enemies
        // Ellenségek renderelése (típusokkal)
        for (Enemy enemy : enemies) {
            float[] modelMatrix = createModelMatrix(enemy.x, enemy.y, enemy.size, enemy.size);
            glUniformMatrix4fv(uniModel, false, modelMatrix);

            switch (enemy.type) {
                case BASIC: glUniform4f(uniColor, 0.9f, 0.2f, 0.2f, 1.0f); break;
                case FAST:  glUniform4f(uniColor, 0.2f, 0.9f, 0.9f, 1.0f); break;
                case TANK:  glUniform4f(uniColor, 0.5f, 0.5f, 0.2f, 1.0f); break;
            }

            glDrawElements(GL_TRIANGLES, 6, GL_UNSIGNED_INT, 0);
            renderHealthBar(enemy);
        }


        glBindVertexArray(0);

        renderText("Score: " + score, 20, 40, 1.0f);
    }

    private void cleanup() {
        glDeleteBuffers(ebo);
        glDeleteBuffers(vbo);
        glDeleteVertexArrays(vao);

        glDeleteProgram(program);
        glDeleteProgram(textProgram);
        glDeleteTextures(fontTexture);

        if (cdata != null) cdata.free();
        glDeleteBuffers(textVBO);
        glDeleteVertexArrays(textVAO);

        glfwDestroyWindow(window);
        glfwTerminate();
    }

    // Shader programok
    private int createProgram() {
        int vs = glCreateShader(GL_VERTEX_SHADER);
        glShaderSource(vs, vertexShaderSource);
        glCompileShader(vs);
        if (glGetShaderi(vs, GL_COMPILE_STATUS) == GL_FALSE)
            throw new RuntimeException("Vertex shader hiba: " + glGetShaderInfoLog(vs));

        int fs = glCreateShader(GL_FRAGMENT_SHADER);
        glShaderSource(fs, fragmentShaderSource);
        glCompileShader(fs);
        if (glGetShaderi(fs, GL_COMPILE_STATUS) == GL_FALSE)
            throw new RuntimeException("Fragment shader hiba: " + glGetShaderInfoLog(fs));

        int prog = glCreateProgram();
        glAttachShader(prog, vs);
        glAttachShader(prog, fs);
        glLinkProgram(prog);

        glDeleteShader(vs);
        glDeleteShader(fs);
        return prog;
    }

    private int createTextProgram() {
        int vs = glCreateShader(GL_VERTEX_SHADER);
        glShaderSource(vs, textVertexShaderSource);
        glCompileShader(vs);
        if (glGetShaderi(vs, GL_COMPILE_STATUS) == GL_FALSE)
            throw new RuntimeException("Text vertex shader hiba: " + glGetShaderInfoLog(vs));

        int fs = glCreateShader(GL_FRAGMENT_SHADER);
        glShaderSource(fs, textFragmentShaderSource);
        glCompileShader(fs);
        if (glGetShaderi(fs, GL_COMPILE_STATUS) == GL_FALSE)
            throw new RuntimeException("Text fragment shader hiba: " + glGetShaderInfoLog(fs));

        int prog = glCreateProgram();
        glAttachShader(prog, vs);
        glAttachShader(prog, fs);
        glLinkProgram(prog);

        glDeleteShader(vs);
        glDeleteShader(fs);
        return prog;
    }

    private float[] ortho(float left, float right, float bottom, float top, float near, float far) {
        float[] m = new float[16];
        m[0] = 2f / (right - left);
        m[5] = 2f / (top - bottom);
        m[10] = -2f / (far - near);
        m[12] = -(right + left) / (right - left);
        m[13] = -(top + bottom) / (top - bottom);
        m[14] = -(far + near) / (far - near);
        m[15] = 1f;
        return m;
    }

    private float[] createModelMatrix(float x, float y, float w, float h) {
        return new float[]{
                w, 0, 0, 0,
                0, h, 0, 0,
                0, 0, 1, 0,
                x, y, 0, 1
        };
    }

    private boolean checkCollision(Entity a, Entity b) {
        return Math.abs(a.x - b.x) < (a.size + b.size) / 2f &&
               Math.abs(a.y - b.y) < (a.size + b.size) / 2f;
    }

    private void renderHealthBar(Entity e) {
        float barWidth = e.size, barHeight = 5;
        float healthPercent = Math.max(0, (float) e.hp / e.maxHp);

        float[] model = createModelMatrix(e.x, e.y - e.size / 2 - 10, barWidth, barHeight);
        glUniformMatrix4fv(uniModel, false, model);
        glUniform4f(uniColor, 0.8f, 0, 0, 1f);
        glDrawElements(GL_TRIANGLES, 6, GL_UNSIGNED_INT, 0);

        model = createModelMatrix(e.x - barWidth * (1f - healthPercent) / 2,
                e.y - e.size / 2 - 10,
                barWidth * healthPercent, barHeight);
        glUniformMatrix4fv(uniModel, false, model);
        glUniform4f(uniColor, 0, 0.8f, 0, 1f);
        glDrawElements(GL_TRIANGLES, 6, GL_UNSIGNED_INT, 0);
    }

    private void initFont() throws Exception {
        byte[] ttf = Files.readAllBytes(Paths.get("C:\\Users\\zalma\\grafika\\src\\grafika_beadando\\arial.ttf"));
        ByteBuffer ttfBuffer = memAlloc(ttf.length).put(ttf).flip();

        int bitmapW = 512, bitmapH = 512;
        ByteBuffer bitmap = memAlloc(bitmapW * bitmapH);

        cdata = STBTTBakedChar.malloc(96);
        stbtt_BakeFontBitmap(ttfBuffer, 24, bitmap, bitmapW, bitmapH, 32, cdata);

        fontTexture = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, fontTexture);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RED, bitmapW, bitmapH, 0, GL_RED, GL_UNSIGNED_BYTE, bitmap);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);

        memFree(bitmap);
        memFree(ttfBuffer);
    }

    private void renderText(String text, float x, float y, float scale) {
        glUseProgram(textProgram);
        glUniformMatrix4fv(glGetUniformLocation(textProgram, "uProjection"), false,
                ortho(0, width, height, 0, -1, 1));
        glUniform4f(glGetUniformLocation(textProgram, "uTextColor"), 1f, 1f, 1f, 1f);
        glUniform1i(glGetUniformLocation(textProgram, "uTexture"), 0);

        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, fontTexture);

        glBindVertexArray(textVAO);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer xBuf = stack.floats(x);
            FloatBuffer yBuf = stack.floats(y);

            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                if (c < 32 || c >= 128) continue;

                STBTTAlignedQuad q = STBTTAlignedQuad.malloc(stack);
                stbtt_GetBakedQuad(cdata, 512, 512, c - 32, xBuf, yBuf, q, true);

                float[] vertices = {
                        q.x0() * scale, q.y0() * scale, q.s0(), q.t0(),
                        q.x1() * scale, q.y0() * scale, q.s1(), q.t0(),
                        q.x0() * scale, q.y1() * scale, q.s0(), q.t1(),
                        q.x1() * scale, q.y1() * scale, q.s1(), q.t1()
                };
                glBindBuffer(GL_ARRAY_BUFFER, textVBO);
                glBufferData(GL_ARRAY_BUFFER, vertices, GL_DYNAMIC_DRAW);
                glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
            }
        }
        glBindVertexArray(0);
    }

    // Shaderok
    private static final String vertexShaderSource =
            "#version 330 core\n" +
            "layout (location = 0) in vec2 aPos;\n" +
            "uniform mat4 uModel;\n" +
            "uniform mat4 uProjection;\n" +
            "void main() { gl_Position = uProjection * uModel * vec4(aPos, 0.0, 1.0); }";

    private static final String fragmentShaderSource =
            "#version 330 core\n" +
            "out vec4 FragColor;\n" +
            "uniform vec4 uColor;\n" +
            "void main() { FragColor = uColor; }";

    private static final String textVertexShaderSource =
            "#version 330 core\n" +
            "layout (location = 0) in vec2 aPos;\n" +
            "layout (location = 1) in vec2 aTexCoord;\n" +
            "out vec2 TexCoord;\n" +
            "uniform mat4 uProjection;\n" +
            "void main() { gl_Position = uProjection * vec4(aPos, 0.0, 1.0); TexCoord = aTexCoord; }";

    private static final String textFragmentShaderSource =
            "#version 330 core\n" +
            "in vec2 TexCoord;\n" +
            "out vec4 FragColor;\n" +
            "uniform sampler2D uTexture;\n" +
            "uniform vec4 uTextColor;\n" +
            "void main() { float alpha = texture(uTexture, TexCoord).r; FragColor = vec4(uTextColor.rgb, alpha * uTextColor.a); }";

    // Entity osztályok
    private static abstract class Entity {
        float x, y, size;
        int hp, maxHp;
        Entity(float x, float y, float size, int hp) {
            this.x = x; this.y = y; this.size = size; this.hp = hp; this.maxHp = hp;
        }
        boolean isDead() { return hp <= 0; }
    }

    private static class Player extends Entity {
        float shootCooldown = 0;
        Player(float x, float y) { super(x, y, 32, 5); }
    }
    
    private enum EnemyType {
        BASIC, FAST, TANK
    }

    private static class Bullet extends Entity {
        float vx, vy;
        Bullet(float x, float y, float vx, float vy) { super(x, y, 8,1); this.vx=vx; this.vy=vy; }
        void update(float dt) { x+=vx*dt; y+=vy*dt; }
    }

    private static class Enemy extends Entity {
        float vx, vy;
        EnemyType type;

        Enemy(float x, float y, float vx, float vy, EnemyType type) {
            super(x, y, getSize(type), getHp(type));
            this.vx = vx;
            this.vy = vy;
            this.type = type;
        }

        static float getSize(EnemyType type) {
            switch (type) {
                case BASIC: return 28.0f;
                case FAST:  return 20.0f;
                case TANK:  return 40.0f;
                default:    return 28.0f;
            }
        }

        static int getHp(EnemyType type) {
            switch (type) {
                case BASIC: return 3;
                case FAST:  return 1;
                case TANK:  return 6;
                default:    return 3;
            }
        }

        void update(float deltaTime) {
        	switch (type) {
            case BASIC:
                // Egyszerű egyenes lefelé
                y += vy * deltaTime;
                break;
            case FAST:
                // Cikcakk mozgás
                x += (float)Math.sin(glfwGetTime() * 5.0) * 50.0f * deltaTime;
                y += vy * deltaTime;
                break;
            case TANK:
                // Lassan lefelé, de stabil
                y += vy * deltaTime;
                break;
        	}
        }
    }

}
