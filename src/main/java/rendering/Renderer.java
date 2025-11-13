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

    // --- A dedikált textúra kötegelő ---
    private TextureRenderer textureRenderer;

    // --- Színes (Color Batch) shaderek és VAO ---
    private int program, textProgram;
    private int uniProjection;
    private static final int MAX_QUADS = 10000;
    private static final int MAX_VERTICES = MAX_QUADS * 4;
    private static final int MAX_INDICES = MAX_QUADS * 6;
    private static final int VERTEX_SIZE_FLOATS = 6;
    private static final int VERTEX_SIZE_BYTES = VERTEX_SIZE_FLOATS * Float.BYTES;

    private int colorBatchVAO, colorBatchVBO, colorBatchEBO;
    private FloatBuffer colorBatchBuffer;
    private int colorBatchQuadCount = 0;

    // --- Betűtípus (Text) shaderek és VAO ---
    private int fontTexture, textVAO, textVBO;
    private STBTTBakedChar.Buffer cdata;

    private float camLeft = 0, camTop = 0;

    // --- Mátrixok és kötegelés állapot ---
    private float[] worldProjection;
    private float[] uiProjection;
    private int currentTextureId = -1;
    private float[] currentProjectionMatrix = null;


    // --- Shader források (CSAK a szín és a szöveg maradt) ---
    private static final String vertexShaderSource =
    	    "#version 330 core\n" +
    	    "layout (location = 0) in vec2 aPos;\n" +
    	    "layout (location = 1) in vec4 aColor;\n" + 
    	    "uniform mat4 uProjection;\n" +
    	    "out vec4 vColor;\n" + 
    	    "void main() {\n" +
    	    "   gl_Position = uProjection * vec4(aPos, 0.0, 1.0);\n" + 
    	    "   vColor = aColor;\n" +
    	    "}";

    	
    private static final String fragmentShaderSource =
    	    "#version 330 core\n" +
    	    "in vec4 vColor;\n" + 
    	    "out vec4 FragColor;\n" +
    	    "void main() { FragColor = vColor; }";

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


    public Renderer(int width, int height) {
        this.width = width;
        this.height = height;
        textureLoader = TextureLoader.getInstance();
        textureRenderer = new TextureRenderer();
    }
    

    public void init() {
        program = createShader(vertexShaderSource, fragmentShaderSource);
        uniProjection = glGetUniformLocation(program, "uProjection");

        textProgram = createShader(textVertexShaderSource, textFragmentShaderSource);
        
        // Textúra kötegelő inicializálása
        textureRenderer.init();

        initFont();
        
        // Color Batch VAO inicializálása
        colorBatchVAO = glGenVertexArrays();
        glBindVertexArray(colorBatchVAO);

        colorBatchVBO = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, colorBatchVBO);
        glBufferData(GL_ARRAY_BUFFER, (long)MAX_VERTICES * VERTEX_SIZE_BYTES, GL_DYNAMIC_DRAW);

        glVertexAttribPointer(0, 2, GL_FLOAT, false, VERTEX_SIZE_BYTES, 0);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(1, 4, GL_FLOAT, false, VERTEX_SIZE_BYTES, 2 * Float.BYTES);
        glEnableVertexAttribArray(1);

        int[] indices = new int[MAX_INDICES];
        int offset = 0;
        for (int i = 0; i < MAX_QUADS; i++) {
            indices[i * 6 + 0] = offset + 0;
            indices[i * 6 + 1] = offset + 1;
            indices[i * 6 + 2] = offset + 2;
            indices[i * 6 + 3] = offset + 2;
            indices[i * 6 + 4] = offset + 3;
            indices[i * 6 + 5] = offset + 0;
            offset += 4; 
        }
        colorBatchEBO = glGenBuffers();
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, colorBatchEBO);
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, indices, GL_STATIC_DRAW);

        colorBatchBuffer = MemoryUtil.memAllocFloat(MAX_VERTICES * VERTEX_SIZE_FLOATS);
        
        // Text VAO/VBO inicializálása
        textVAO = glGenVertexArrays();
        textVBO = glGenBuffers();
        glBindVertexArray(textVAO);
        glBindBuffer(GL_ARRAY_BUFFER, textVBO);
        int vertexSizeBytes = 4 * Float.BYTES; 
	    int maxVertices = 2048 * 6; 
	
	    glBufferData(GL_ARRAY_BUFFER, (long)maxVertices * vertexSizeBytes, GL_DYNAMIC_DRAW);
	
	    glVertexAttribPointer(0, 2, GL_FLOAT, false, vertexSizeBytes, 0);
	    glEnableVertexAttribArray(0);
	    glVertexAttribPointer(1, 2, GL_FLOAT, false, vertexSizeBytes, 2 * Float.BYTES);
	    glEnableVertexAttribArray(1);

        glBindVertexArray(0);
        
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        
        this.uiRenderer = new UIRenderer(this, width, height);
        loadTextures();
    }

    /**
     * MÓDOSÍTOTT render ciklus, amely kezeli a szín- és textúra-kötegeket.
     */
    public void render(GameWorld world) {
        glClearColor(0.08f, 0.08f, 0.12f, 1.0f);
        glClear(GL_COLOR_BUFFER_BIT);

        // --- 1. KAMERA ÉS MÁTRIXOK BEÁLLÍTÁSA ---
        setupCamera(world);
        this.worldProjection = ortho(camLeft, camLeft + width, camTop + height, camTop, -1.0f, 1.0f);
        this.uiProjection = ortho(0, width, height, 0, -1.0f, 1.0f);
        
        // --- 2. VILÁG SZÍNES OBJEKTUMOK (Color Batch) ---
        startColorBatch(worldProjection);

        drawGrid(world);
        drawXPOrbs(world);
        drawBullets(world);
        drawHealthBar(world);
        drawVisualEffects(world);
        world.getGadgetSystem().renderLaserBullets(this);

        flushColorBatch();
        
        // --- 3. VILÁG TEXTÚRÁZOTT OBJEKTUMOK (Texture Batch) ---
        startTextureBatch(worldProjection);
        
        drawPlayer(world.getPlayer());
        drawEnemies(world);
        world.getGadgetSystem().renderOrbitBlades(this);
        
        flushTextureBatch();
        
        glBindVertexArray(0); // Kötegek után VAO leválasztása
        
        // --- 4. UI SZÍNES OBJEKTUMOK (Color Batch) ---
        startColorBatch(uiProjection);

        uiRenderer.renderQuads(world); 
        
        flushColorBatch();
        
        // --- 5. UI TEXTÚRÁZOTT OBJEKTUMOK (Texture Batch) ---
        // Az UIRenderer refaktorálva lett (renderTextures / renderText)
        startTextureBatch(uiProjection);
        
        uiRenderer.renderTextures(world);
        
        flushTextureBatch();

        // --- 6. UI SZÖVEG (Immediate Mode) ---
        // A szöveg a saját shaderét használja, ezért marad utoljára.
        uiRenderer.renderText(world, camLeft, camTop);
    }
    
    private void drawVisualEffects(GameWorld world) {
        for (VisualEffect e : world.getVisualEffects()) {

            if (e instanceof ExplosionEffect exp) {
                float alpha = exp.getAlpha();
                float size  = exp.size;

                // külső halvány nagy glow
                drawQuad(exp.x, exp.y, size * 1.6f, size * 1.6f,
                         1f, 0.4f, 0.0f, alpha * 0.4f);

                // belső erős fény
                drawQuad(exp.x, exp.y, size, size,
                         1f, 0.9f, 0.5f, alpha);
            } else {
                // régi, sima effekt
                float alpha = e.getAlpha();
                drawQuad(e.x, e.y, e.size, e.size,
                         1f, 0.5f, 0.2f, alpha);
            }
        }
    }
    
    public float getTextWidth(String text, float scale) {
        if (cdata == null) {
            return text.length() * 10f * scale; 
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer xBuf = stack.floats(0.0f);
            FloatBuffer yBuf = stack.floats(0.0f);
            STBTTAlignedQuad q = STBTTAlignedQuad.malloc(stack);
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                if (c < 32 || c >= 128) continue;
                stbtt_GetBakedQuad(cdata, 512, 512, c - 32, xBuf, yBuf, q, true);
            }
            return xBuf.get(0) * scale;
        } catch (Exception e) {
            System.err.println("Hiba a szöveg szélességének számítása közben: " + e.getMessage());
            return text.length() * 10f * scale;
        }
    }
    
    private void loadTextures() {
        // ... (Ez a metódus változatlan) ...
        try {
            // Gadget textúrák
            textureLoader.loadTexture("src/main/assets/blade.png");
            textureLoader.loadTexture("src/main/assets/damage.png");
            textureLoader.loadTexture("src/main/assets/attack_speed.png");
            textureLoader.loadTexture("src/main/assets/heart.png");
            textureLoader.loadTexture("src/main/assets/move_speed.png");
            textureLoader.loadTexture("src/main/assets/multishot.png");
            textureLoader.loadTexture("src/main/assets/heart_half.png");
            textureLoader.loadTexture("src/main/assets/laser.png");
            textureLoader.loadTexture("src/main/assets/magnet.png");
            
            // Entitás textúrák
            textureLoader.loadTexture("src/main/assets/player.png");
            textureLoader.loadTexture("src/main/assets/tiny.png"); // Fast
            textureLoader.loadTexture("src/main/assets/tank.png");
            textureLoader.loadTexture("src/main/assets/normal.png"); // Basic
            textureLoader.loadTexture("src/main/assets/ranged.png"); // <-- ÚJ TEXTÚRA
            
            // Boss textúrák
            textureLoader.loadTexture("src/main/assets/ghost.png");
            textureLoader.loadTexture("src/main/assets/demon.png");
            textureLoader.loadTexture("src/main/assets/dragon.png");
            
            System.out.println("All textures pre-loaded.");
        } catch (Exception e) {
            System.err.println("Error loading textures: " + e.getMessage());
        }
    }
    
    // --- ÚJ KÖTEG-KEZELŐ METÓDUSOK ---

    private void startTextureBatch(float[] projectionMatrix) {
        textureRenderer.startBatch(projectionMatrix);
        this.currentProjectionMatrix = projectionMatrix;
        this.currentTextureId = -1; // Kényszerítjük a textúra beállítását az első hívásnál
    }

    private void flushTextureBatch() {
        textureRenderer.flushBatch();
        this.currentTextureId = -1;
        this.currentProjectionMatrix = null;
    }

    /**
     * Központi textúra-rajzoló metódus, amely kezeli a köteget.
     * @param isWorld Meghatározza, hogy a világ vagy a UI mátrixot használja-e.
     */
    private void drawTexture(String texturePath, float centerX, float centerY, float w, float h, 
                             float r, float g, float b, float a, boolean isWorld) {
        
        TextureLoader.TextureInfo textureInfo = textureLoader.getTextureInfo(texturePath);
        if (textureInfo == null) {
            System.err.println("Texture not found for batching: " + texturePath);
            // Fallback: rajzoljunk egy színes quadot a másik kötegbe
            // (Figyelem: ez feltételezi, hogy a color batch aktív, ami nem biztos)
            // Biztonságosabb, ha kihagyjuk, vagy a color batch-be rajzoljuk:
            // if (isWorld) drawQuad(centerX, centerY, w, h, 1f, 0f, 1f, 0.5f);
            return;
        }
        
        float[] projection = isWorld ? this.worldProjection : this.uiProjection;

        // Ellenőrzés, hogy a megfelelő köteg aktív-e
        if (projection != this.currentProjectionMatrix) {
            flushTextureBatch();
            startTextureBatch(projection);
        }
        
        // Ellenőrzés, hogy textúrát kell-e váltani
        if (textureInfo.textureId != this.currentTextureId) {
            textureRenderer.flushBatch(); // Előző textúra kötegének kiürítése
            textureRenderer.bindTexture(textureInfo.textureId);
            this.currentTextureId = textureInfo.textureId;
        }

        // Quad koordináták
        float x0 = centerX - w / 2f;
        float y0 = centerY - h / 2f;
        float x1 = centerX + w / 2f;
        float y1 = centerY + h / 2f;
        
        // Hozzáadás a köteghez (UV: 0,0-tól 1,1-ig)
        textureRenderer.addQuadToBatch(x0, y0, x1, y1, 0f, 0f, 1f, 1f, r, g, b, a);
    }
    
    // --- MÓDOSÍTOTT Textúra renderelés metódusok (mostantól a köteget hívják) ---

    public void renderTexture(String texturePath, float centerX, float centerY, float width, float height, float alpha) {
        drawTexture(texturePath, centerX, centerY, width, height, 1f, 1f, 1f, alpha, false); // isWorld = false
    }
    
    public void renderTexture(String texturePath, float centerX, float centerY, float width, float height) {
    	drawTexture(texturePath, centerX, centerY, width, height, 1f, 1f, 1f, 1f, false); // isWorld = false
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
        // A kötegelés miatt a forgatást vertex szinten kellene megoldani,
        // ami bonyolultabb. Egyelőre forgatás nélkül adjuk a köteghez.
        renderTexture(texturePath, centerX, centerY, width, height);
    }

    public void renderTextureInWorld(String texturePath, float centerX, float centerY, float width, float height) {
        drawTexture(texturePath, centerX, centerY, width, height, 1f, 1f, 1f, 1f, true); // isWorld = true
    }
    
    public void renderTextureTinted(String texturePath, float centerX, float centerY, float width, float height,
			float r, float g, float b, float a) {
		drawTexture(texturePath, centerX, centerY, width, height, r, g, b, a, true); // isWorld = true
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

    // --- A rajzoló metódusok (drawGrid, drawPlayer, stb.) VÁLTOZATLANOK MARADNAK ---
    // Mivel a renderTexture... metódusokat hívják, amiknek a szignatúrája nem változott,
    // így ezek a metódusok is helyesen fognak működni.

    private void drawGrid(GameWorld world) {
        int cell = 64;
        for (int gx = 0; gx < world.worldWidth; gx += cell) {
            for (int gy = 0; gy < world.worldHeight; gy += cell) {
                drawQuad(gx + cell/2.0f, gy + cell/2.0f, cell - 2, cell - 2, 0.16f, 0.16f, 0.19f, 1.0f);
            }
        }
    }
    
    private void drawPlayer(Player player) {
    	renderTextureInWorld("src/main/assets/player.png", player.x, player.y, player.size, player.size);
    }
    
    private void drawEnemies(GameWorld world) {
        for (Enemy enemy : world.getEnemies()) {
        	if (enemy instanceof BossEnemy boss) {
        	    String texturePath = switch (boss.getBossType()) {
        	        case GHOST -> "src/main/assets/ghost.png";
        	        case DEMON -> "src/main/assets/demon.png";
        	        case DRAGON -> "src/main/assets/dragon.png";
        	    };

        	    float tintR = 1f, tintG = 1f, tintB = 1f, tintA = 1f;
        	    switch (boss.getPhase()) {
        	        case 1 -> { tintR = 1f; tintG = 1f; tintB = 1f; }
        	        case 2 -> { tintR = 1f; tintG = 0.7f; tintB = 0.7f; }
        	        case 3 -> { tintR = 1f; tintG = 0.3f; tintB = 0.3f; }
        	    }
        	    if (boss.getFlashTimer() > 0) {
        	        float pulse = (float) Math.sin(boss.getFlashTimer() * 25f);
        	        float intensity = 0.8f + 0.2f * Math.abs(pulse);
        	        tintR *= intensity; tintG *= intensity; tintB *= intensity;
        	    }
        	    if (boss.getBossType() == BossEnemy.BossType.GHOST) {
        	        float fadeCycle = (float) Math.sin(System.currentTimeMillis() * 0.003);
        	        tintA = 0.7f + 0.3f * fadeCycle; 
        	        if (boss.isTeleporting()) {
        	        	float progress = boss.getFlashTimer() / 0.6f; 
        	            tintA = Math.max(0.1f, progress); 
        	        }
        	        if (boss.getFlashTimer() > 0) {
        	            tintA = Math.abs((float) Math.sin(boss.getFlashTimer() * (float)Math.PI));
        	        }
        	    }
        	    renderTextureTinted(texturePath, boss.x, boss.y, boss.size, boss.size, tintR, tintG, tintB, tintA);
        	}
            else {
                switch (enemy.type) {
                    case BASIC -> renderTextureInWorld("src/main/assets/normal.png", enemy.x, enemy.y, enemy.size, enemy.size);
                    case FAST  -> renderTextureInWorld("src/main/assets/tiny.png", enemy.x, enemy.y, enemy.size, enemy.size);
                    case TANK  -> renderTextureInWorld("src/main/assets/tank.png", enemy.x, enemy.y, enemy.size, enemy.size);
                    case RANGED -> renderTextureInWorld("src/main/assets/ranged.png", enemy.x, enemy.y, enemy.size, enemy.size);
                }
            }
        }
    }
    
    private void drawHealthBar(GameWorld world) {
        renderHealthBar(world.getPlayer());
    	for (Enemy enemy : world.getEnemies()) {
            renderHealthBar(enemy);
        }
    }
    
    private void drawBullets(GameWorld world) {
        for (Bullet b : world.getBullets()) {
             if (b instanceof LaserBullet) continue;
             
             if (b.owner == Bullet.Owner.ENEMY) {
                drawQuad(b.x, b.y, b.size, b.size, 1.0f, 0.2f, 0.1f, 1.0f);
             } else {
                drawQuad(b.x, b.y, b.size, b.size, 1.0f, 0.9f, 0.2f, 1.0f);
             }
        }
    }
    
    private void drawXPOrbs(GameWorld world) {
        for (XPOrb orb : world.getXPOrbs()) {
            float size = Math.min(15f, 15f - (15f - orb.value / 2));
            float pulse = 1.0f + 0.08f * (float)Math.sin(System.currentTimeMillis() / 100.0 + orb.hashCode() % 10);
            drawQuad(orb.x, orb.y, size * pulse, size * pulse, 1.0f, 1.0f, 0.45f, 1.0f);
        }
    }

    // --- Color Batch (Színes négyzetek) segédfüggvényei (VÁLTOZATLANOK) ---
    
    public void drawQuad(float x, float y, float w, float h, float r, float g, float b, float a) {
        if (colorBatchQuadCount >= MAX_QUADS) {
            flushColorBatch();
            startColorBatch(this.currentProjectionMatrix); // Újraindítás
        }

        float x0 = x - w * 0.5f;
        float y0 = y - h * 0.5f;
        float x1 = x + w * 0.5f;
        float y1 = y + h * 0.5f;

        colorBatchBuffer.put(x0).put(y0).put(r).put(g).put(b).put(a);
        colorBatchBuffer.put(x1).put(y0).put(r).put(g).put(b).put(a);
        colorBatchBuffer.put(x1).put(y1).put(r).put(g).put(b).put(a);
        colorBatchBuffer.put(x0).put(y1).put(r).put(g).put(b).put(a);

        colorBatchQuadCount++;
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
    
    private void startColorBatch(float[] projectionMatrix) {
        glUseProgram(program);
        glUniformMatrix4fv(uniProjection, false, projectionMatrix);
        glBindVertexArray(colorBatchVAO);
        colorBatchBuffer.clear();
        colorBatchQuadCount = 0;
        this.currentProjectionMatrix = projectionMatrix; // Állapot mentése
    }

    private void flushColorBatch() {
        if (colorBatchQuadCount == 0) return;
        glUseProgram(program); // Biztonság kedvéért
        colorBatchBuffer.flip();
        glBindVertexArray(colorBatchVAO);
        glBindBuffer(GL_ARRAY_BUFFER, colorBatchVBO);
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, colorBatchEBO);
        glBufferSubData(GL_ARRAY_BUFFER, 0, colorBatchBuffer);
        glDrawElements(GL_TRIANGLES, colorBatchQuadCount * 6, GL_UNSIGNED_INT, 0);
        
        colorBatchBuffer.clear();
        colorBatchQuadCount = 0;
        this.currentProjectionMatrix = null; // Állapot törlése
    }
    
    // --- Szöveg (Text) kirajzolás (VÁLTOZATLAN) ---
    
    public void renderText(String text, float x, float y, float scale, float r, float g, float b, float a) {
        // Ez a metódus "immediate mode" jellegű, a saját shaderét és VAO-ját használja.
        // Ezért kell a kötegeken kívül, külön futtatni.
        
        glUseProgram(textProgram);
        glUniformMatrix4fv(glGetUniformLocation(textProgram, "uProjection"), false, ortho(0, width, height, 0, -1, 1));
        glUniform4f(glGetUniformLocation(textProgram, "uTextColor"), r, g, b, a);
        glUniform1i(glGetUniformLocation(textProgram, "uTexture"), 0);

        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, fontTexture);
        glBindVertexArray(textVAO);
        glBindBuffer(GL_ARRAY_BUFFER, textVBO);
        
        int charCount = 0;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer xBuf = stack.floats(x / scale);
            FloatBuffer yBuf = stack.floats(y / scale);
            STBTTAlignedQuad q = STBTTAlignedQuad.malloc(stack);
            FloatBuffer vertices = stack.mallocFloat(text.length() * 6 * 4);
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                if (c < 32 || c >= 128) continue;
                stbtt_GetBakedQuad(cdata, 512, 512, c - 32, xBuf, yBuf, q, true);
                float x0 = q.x0() * scale; float y0 = q.y0() * scale;
                float x1 = q.x1() * scale; float y1 = q.y1() * scale;
                vertices.put(x0).put(y0).put(q.s0()).put(q.t0());
                vertices.put(x0).put(y1).put(q.s0()).put(q.t1());
                vertices.put(x1).put(y0).put(q.s1()).put(q.t0());
                vertices.put(x1).put(y0).put(q.s1()).put(q.t0());
                vertices.put(x0).put(y1).put(q.s0()).put(q.t1());
                vertices.put(x1).put(y1).put(q.s1()).put(q.t1());
                charCount++;
            }
            vertices.flip();
            glBufferSubData(GL_ARRAY_BUFFER, 0, vertices);
        }
        if (charCount > 0) {
            glDrawArrays(GL_TRIANGLES, 0, charCount * 6);
        }
        glBindVertexArray(0);
    }
    
    // --- Cleanup (MÓDOSÍTVA) ---
    public void cleanup() {
        glDeleteProgram(program);
        glDeleteProgram(textProgram);
        
        textureRenderer.cleanup(); 
        
        // Color Batch cleanup
        glDeleteVertexArrays(colorBatchVAO);
        glDeleteBuffers(colorBatchVBO);
        glDeleteBuffers(colorBatchEBO);
        if (colorBatchBuffer != null) {
            MemoryUtil.memFree(colorBatchBuffer);
        }
        
        // Text cleanup
        glDeleteVertexArrays(textVAO);
        glDeleteBuffers(textVBO);
        glDeleteTextures(fontTexture);
        
        if (cdata != null) cdata.free();
        textureLoader.cleanup();
    }
    
    // --- PRIVATE HELPERS (VÁLTOZATLANOK) ---
    
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
}