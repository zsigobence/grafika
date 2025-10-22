package main.java.rendering;

import main.java.entities.*;
import main.java.entities.Character;
import main.java.world.GameWorld;
import org.lwjgl.stb.STBTTAlignedQuad;
import org.lwjgl.stb.STBTTBakedChar;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL33C.*;
import static org.lwjgl.stb.STBTruetype.*;

public class Renderer {
    private final int width, height;
    private UIRenderer uiRenderer;
    private TextureLoader textureLoader;

    private int program, textProgram, textureProgram;
    private int vao, vbo, ebo;
    private int uniProjection, uniModel, uniColor;
    private int texUniProjection, texUniModel, texUniTexture;

    private int fontTexture, textVAO, textVBO;
    private STBTTBakedChar.Buffer cdata;

    private float camLeft = 0, camTop = 0;

    // Shader Source
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

    // Új: Textúra shader
    private static final String textureVertexShaderSource =
        "#version 330 core\n" +
        "layout (location = 0) in vec2 aPos;\n" +
        "uniform mat4 uModel;\n" +
        "uniform mat4 uProjection;\n" +
        "out vec2 TexCoord;\n" +
        "void main() {\n" +
        "   gl_Position = uProjection * uModel * vec4(aPos, 0.0, 1.0);\n" +
        "   TexCoord = aPos + vec2(0.5); // Convert from [-0.5,0.5] to [0,1]\n" +
        "}";

    private static final String textureFragmentShaderSource =
        "#version 330 core\n" +
        "in vec2 TexCoord;\n" +
        "out vec4 FragColor;\n" +
        "uniform sampler2D uTexture;\n" +
        "void main() { FragColor = texture(uTexture, TexCoord); }";


    public Renderer(int width, int height) {
        this.width = width;
        this.height = height;
        textureLoader = TextureLoader.getInstance();
    }
    

    public void init() {
        program = createShader(vertexShaderSource, fragmentShaderSource);
        uniProjection = glGetUniformLocation(program, "uProjection");
        uniModel = glGetUniformLocation(program, "uModel");
        uniColor = glGetUniformLocation(program, "uColor");

        textProgram = createShader(textVertexShaderSource, textFragmentShaderSource);
        
        // Új: Textúra shader program
        textureProgram = createShader(textureVertexShaderSource, textureFragmentShaderSource);
        texUniProjection = glGetUniformLocation(textureProgram, "uProjection");
        texUniModel = glGetUniformLocation(textureProgram, "uModel");
        texUniTexture = glGetUniformLocation(textureProgram, "uTexture");

        initFont();

        // General VAO for quads
        float[] vertices = {-0.5f, -0.5f, 0.5f, -0.5f, 0.5f, 0.5f, -0.5f, 0.5f};
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

        // Text VAO/VBO
        textVAO = glGenVertexArrays();
        textVBO = glGenBuffers();
        glBindVertexArray(textVAO);
        glBindBuffer(GL_ARRAY_BUFFER, textVBO);
        glVertexAttribPointer(0, 2, GL_FLOAT, false, 4 * Float.BYTES, 0);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(1, 2, GL_FLOAT, false, 4 * Float.BYTES, 2 * Float.BYTES);
        glEnableVertexAttribArray(1);

        glBindVertexArray(0);
        
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        
        this.uiRenderer = new UIRenderer(this, width, height);
        loadTextures();
    }

    public void render(GameWorld world) {
        glClearColor(0.08f, 0.08f, 0.12f, 1.0f);
        glClear(GL_COLOR_BUFFER_BIT);

        // --- WORLD RENDERING ---
        setupCamera(world);
        
        // Render world with normal shader
        glUseProgram(program);
        glUniformMatrix4fv(uniProjection, false, ortho(camLeft, camLeft + width, camTop + height, camTop, -1.0f, 1.0f));
        glBindVertexArray(vao);

        drawGrid(world);
        drawXPOrbs(world);
        drawPlayer(world.getPlayer());
        drawEnemies(world);
        drawBullets(world);

        world.getGadgetSystem().render(this);

        glBindVertexArray(0);
        
        // --- UI RENDERING ---
        // Switch to UI projection (screen coordinates)
        glUseProgram(program);
        glUniformMatrix4fv(uniProjection, false, ortho(0, width, height, 0, -1.0f, 1.0f));
        glBindVertexArray(vao);
        
        uiRenderer.render(world, camLeft, camTop);
    }
    
    private void loadTextures() {
        try {
            // Betöltjük a textúrákat
            textureLoader.loadTexture("src/main/assets/blade.png");
        } catch (Exception e) {
            System.err.println("Error loading textures: " + e.getMessage());
        }
    }
    
    // ÚJ: Textúra renderelés metódusok - ezeket használja a UIRenderer
    public void renderTexture(String texturePath, float centerX, float centerY, float width, float height) {
        TextureLoader.TextureInfo textureInfo = textureLoader.getTextureInfo(texturePath);
        if (textureInfo == null) {
            System.err.println("Texture not found: " + texturePath);
            return;
        }

        // Textúra shader használata
        glUseProgram(textureProgram);
        glBindVertexArray(vao);
        
        // Projection beállítása (UI mód)
        glUniformMatrix4fv(texUniProjection, false, ortho(0, this.width, this.height, 0, -1.0f, 1.0f));
        
        // Model mátrix beállítása
        glUniformMatrix4fv(texUniModel, false, createModelMatrix(centerX, centerY, width, height));
        
        // Textúra kötése
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, textureInfo.textureId);
        glUniform1i(texUniTexture, 0);
        
        // Rajzolás
        glDrawElements(GL_TRIANGLES, 6, GL_UNSIGNED_INT, 0);
        
        // Textúra leválasztása
        glBindTexture(GL_TEXTURE_2D, 0);
    }
    
    public void renderTextureAspectRatio(String texturePath, float centerX, float centerY, float maxSize) {
        TextureLoader.TextureInfo textureInfo = textureLoader.getTextureInfo(texturePath);
        if (textureInfo == null) return;
        
        float aspectRatio = (float)textureInfo.width / textureInfo.height;
        float width, height;
        
        if (aspectRatio > 1) {
            width = maxSize;
            height = maxSize / aspectRatio;
        } else {
            height = maxSize;
            width = maxSize * aspectRatio;
        }
        
        renderTexture(texturePath, centerX, centerY, width, height);
    }
    
    public void renderTextureRotated(String texturePath, float centerX, float centerY, float width, float height, float angle) {
        // Egyszerűsített változat - kihagyjuk a forgatást most
        renderTexture(texturePath, centerX, centerY, width, height);
    }

    public void renderTextureInWorld(String texturePath, float centerX, float centerY, float width, float height) {
        TextureLoader.TextureInfo textureInfo = textureLoader.getTextureInfo(texturePath);
        if (textureInfo == null) {
            textureInfo = textureLoader.loadTexture(texturePath);
            if (textureInfo == null) {
                // Fallback: színes négyzet
                drawQuad(centerX, centerY, width, height, 0.8f, 0.2f, 0.2f, 0.5f);
                return;
            }
        }

        // Textúra shader használata
        glUseProgram(textureProgram);
        glBindVertexArray(vao);
        
        // FONTOS: Világ projekció használata (nem UI projekció)
        glUniformMatrix4fv(texUniProjection, false, ortho(camLeft, camLeft + this.width, camTop + this.height, camTop, -1.0f, 1.0f));
        
        // Model mátrix beállítása
        glUniformMatrix4fv(texUniModel, false, createModelMatrix(centerX, centerY, width, height));
        
        // Textúra kötése
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, textureInfo.textureId);
        glUniform1i(texUniTexture, 0);
        
        // Rajzolás
        glDrawElements(GL_TRIANGLES, 6, GL_UNSIGNED_INT, 0);
        
        // Textúra leválasztása
        glBindTexture(GL_TEXTURE_2D, 0);
    }
    public TextureLoader getTextureLoader() {
        return textureLoader;
    }
    
    private void setupCamera(GameWorld world) {
        Player player = world.getPlayer();
        camLeft = player.x - this.width / 2.0f;
        camTop = player.y - this.height / 2.0f;
        if (camLeft < 0) camLeft = 0;
        if (camLeft > world.worldWidth - this.width) camLeft = world.worldWidth - this.width;
        if (camTop < 0) camTop = 0;
        if (camTop > world.worldHeight - this.height) camTop = world.worldHeight - this.height;
    }

    // Drawing methods
    private void drawGrid(GameWorld world) {
        int cell = 64;
        for (int gx = 0; gx < world.worldWidth; gx += cell) {
            for (int gy = 0; gy < world.worldHeight; gy += cell) {
                drawQuad(gx + cell/2.0f, gy + cell/2.0f, cell - 2, cell - 2, 0.16f, 0.16f, 0.19f, 1.0f);
            }
        }
    }
    
    private void drawPlayer(Player player) {
        drawQuad(player.x, player.y, player.size, player.size, 0.2f, 0.9f, 0.2f, 1.0f);
        renderHealthBar(player);
    }
    
    private void drawEnemies(GameWorld world) {
        for (Enemy enemy : world.getEnemies()) {
            float r=0,g=0,b=0;
            switch (enemy.type) {
                case BASIC: r=0.9f; g=0.2f; b=0.2f; break;
                case FAST:  r=0.2f; g=0.9f; b=0.9f; break;
                case TANK:  r=0.5f; g=0.5f; b=0.2f; break;
            }
            drawQuad(enemy.x, enemy.y, enemy.size, enemy.size, r,g,b,1.0f);
            renderHealthBar(enemy);
        }
    }
    
    private void drawBullets(GameWorld world) {
        for (Bullet b : world.getBullets()) {
             if (b instanceof LaserBullet) continue; // Rendered by GadgetSystem
             drawQuad(b.x, b.y, b.size, b.size, 1.0f, 0.9f, 0.2f, 1.0f);
        }
    }
    
    private void drawXPOrbs(GameWorld world) {
        for (XPOrb orb : world.getXPOrbs()) {
            float size = 15f - (15f - orb.value / 2);
            float pulse = 1.0f + 0.08f * (float)Math.sin(System.currentTimeMillis() / 100.0 + orb.hashCode() % 10);
            drawQuad(orb.x, orb.y, size * pulse, size * pulse, 1.0f, 1.0f, 0.45f, 1.0f);
        }
    }

    // Public utility for other systems (like GadgetSystem)
    public void drawQuad(float x, float y, float w, float h, float r, float g, float b, float a) {
        glUseProgram(program);
        glBindVertexArray(vao);
        glUniformMatrix4fv(uniModel, false, createModelMatrix(x, y, w, h));
        glUniform4f(uniColor, r, g, b, a);
        glDrawElements(GL_TRIANGLES, 6, GL_UNSIGNED_INT, 0);
    }
    
    public void renderHealthBar(Character character) {
        float barWidth = character.size, barHeight = 5;
        float healthPercent = Math.max(0, (float) character.hp / character.maxHp);
        float yPos = character.y - character.size / 2 - 10;
        
        drawQuad(character.x, yPos, barWidth, barHeight, 0.8f, 0, 0, 1f);
        if (healthPercent > 0) {
            drawQuad(character.x - barWidth * (1f - healthPercent) / 2, yPos, barWidth * healthPercent, barHeight, 0, 0.8f, 0, 1f);
        }
    }
    
    public void renderText(String text, float x, float y, float scale, float r, float g, float b, float a) {
        glUseProgram(textProgram);
        glUniformMatrix4fv(glGetUniformLocation(textProgram, "uProjection"), false, ortho(0, width, height, 0, -1, 1));
        glUniform4f(glGetUniformLocation(textProgram, "uTextColor"), r, g, b, a);
        glUniform1i(glGetUniformLocation(textProgram, "uTexture"), 0);

        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, fontTexture);
        glBindVertexArray(textVAO);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer xBuf = stack.floats(x / scale);
            FloatBuffer yBuf = stack.floats(y / scale);

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
    
    // Cleanup
    public void cleanup() {
        glDeleteProgram(program);
        glDeleteProgram(textProgram);
        glDeleteProgram(textureProgram);
        glDeleteVertexArrays(vao);
        glDeleteBuffers(vbo);
        glDeleteBuffers(ebo);
        glDeleteVertexArrays(textVAO);
        glDeleteBuffers(textVBO);
        glDeleteTextures(fontTexture);
        if (cdata != null) cdata.free();
        textureLoader.cleanup();
    }
    
    // --- PRIVATE HELPERS ---
    
    private void initFont() {
        try (InputStream in = getClass().getResourceAsStream("/fonts/arial.ttf")) {
            if (in == null) throw new Exception("Font not found");
            byte[] fontBytes = in.readAllBytes();
            ByteBuffer ttfBuffer = MemoryUtil.memAlloc(fontBytes.length).put(fontBytes).flip();
            
            int bitmapW = 512, bitmapH = 512;
            ByteBuffer bitmap = MemoryUtil.memAlloc(bitmapW * bitmapH);
            cdata = STBTTBakedChar.malloc(96);
            stbtt_BakeFontBitmap(ttfBuffer, 24, bitmap, bitmapW, bitmapH, 32, cdata);
            
            fontTexture = glGenTextures();
            glBindTexture(GL_TEXTURE_2D, fontTexture);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RED, bitmapW, bitmapH, 0, GL_RED, GL_UNSIGNED_BYTE, bitmap);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
            
            MemoryUtil.memFree(bitmap);
            MemoryUtil.memFree(ttfBuffer);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private int createShader(String vsSource, String fsSource) {
        int vs = glCreateShader(GL_VERTEX_SHADER);
        glShaderSource(vs, vsSource);
        glCompileShader(vs);
        if (glGetShaderi(vs, GL_COMPILE_STATUS) == GL_FALSE)
            throw new RuntimeException("Vertex shader error: " + glGetShaderInfoLog(vs));

        int fs = glCreateShader(GL_FRAGMENT_SHADER);
        glShaderSource(fs, fsSource);
        glCompileShader(fs);
        if (glGetShaderi(fs, GL_COMPILE_STATUS) == GL_FALSE)
            throw new RuntimeException("Fragment shader error: " + glGetShaderInfoLog(fs));

        int prog = glCreateProgram();
        glAttachShader(prog, vs);
        glAttachShader(prog, fs);
        glLinkProgram(prog);
        if (glGetProgrami(prog, GL_LINK_STATUS) == GL_FALSE)
            throw new RuntimeException("Program link error: " + glGetProgramInfoLog(prog));

        glDeleteShader(vs);
        glDeleteShader(fs);
        return prog;
    }
    
    public float[] ortho(float left, float right, float bottom, float top, float near, float far) {
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

    public float[] createModelMatrix(float x, float y, float w, float h) {
        return new float[]{ w, 0, 0, 0, 0, h, 0, 0, 0, 0, 1, 0, x, y, 0, 1 };
    }
}