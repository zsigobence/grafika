package main.java.rendering;

import org.lwjgl.system.MemoryUtil;
import java.nio.FloatBuffer;
import static org.lwjgl.opengl.GL33C.*;

/**
 * Ez az osztály felelős kizárólag a textúrázott négyzetek (quad-ok) KÖTEGELT kirajzolásáért.
 * Kezeli a saját shader programját és a hozzá tartozó dinamikus VAO-t/VBO-t.
 */
public class TextureRenderer {

    private int textureProgram;
    private int texUniProjection, texUniTexture;
    private int textureVAO, textureVBO, textureEBO;

    // --- Kötegelési beállítások ---
    private static final int MAX_QUADS = 10000;
    private static final int MAX_VERTICES = MAX_QUADS * 4;
    private static final int MAX_INDICES = MAX_QUADS * 6;
    
    // Vertex attribútumok: 
    // Pozíció (x, y) = 2 float
    // UV (u, v) = 2 float
    // Szín (r, g, b, a) = 4 float
    private static final int VERTEX_SIZE_FLOATS = 2 + 2 + 4; 
    private static final int VERTEX_SIZE_BYTES = VERTEX_SIZE_FLOATS * Float.BYTES;

    private FloatBuffer textureBatchBuffer;
    private int quadCount = 0;
    
    // Állapot a flush-oláshoz
    private float[] currentProjectionMatrix;
    private int currentTextureId;


    // --- MÓDOSÍTOTT Shaderek a kötegeléshez ---
    
    private static final String textureVertexShaderSource =
        "#version 330 core\n" +
        "layout (location = 0) in vec2 aPos;\n" +
        "layout (location = 1) in vec2 aTexCoord;\n" +
        "layout (location = 2) in vec4 aTint;\n" + // Színezés
        "uniform mat4 uProjection;\n" +
        "out vec2 TexCoord;\n" +
        "out vec4 vTint;\n" +
        "void main() {\n" +
        "   gl_Position = uProjection * vec4(aPos, 0.0, 1.0);\n" +
        "   TexCoord = aTexCoord;\n" +
        "   vTint = aTint;\n" +
        "}";

    private static final String textureFragmentShaderSource =
        "#version 330 core\n" +
        "in vec2 TexCoord;\n" +
        "in vec4 vTint;\n" +
        "out vec4 FragColor;\n" +
        "uniform sampler2D uTexture;\n" +
        "void main() {\n" +
        "    vec4 texColor = texture(uTexture, TexCoord);\n" +
        "    FragColor = texColor * vTint;\n" + // Szorzásos színezés
        "}";

    
    /**
     * Inicializálja a textúra shader programot és a kötegelő VAO/VBO-t.
     */
    public void init() {
        textureProgram = createShader(textureVertexShaderSource, textureFragmentShaderSource);
        texUniProjection = glGetUniformLocation(textureProgram, "uProjection");
        texUniTexture = glGetUniformLocation(textureProgram, "uTexture");
        
        textureBatchBuffer = MemoryUtil.memAllocFloat(MAX_VERTICES * VERTEX_SIZE_FLOATS);

        textureVAO = glGenVertexArrays();
        glBindVertexArray(textureVAO);

        textureVBO = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, textureVBO);
        // Hely foglalása (dinamikus rajzolás)
        glBufferData(GL_ARRAY_BUFFER, (long)MAX_VERTICES * VERTEX_SIZE_BYTES, GL_DYNAMIC_DRAW);
        
        // Index puffer (statikus, mivel mindig ugyanaz a minta)
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
        textureEBO = glGenBuffers();
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, textureEBO);
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, indices, GL_STATIC_DRAW);
        
        // Vertex attribútumok beállítása
        // Hely (loc 0)
        glVertexAttribPointer(0, 2, GL_FLOAT, false, VERTEX_SIZE_BYTES, 0);
        glEnableVertexAttribArray(0);
        
        // UV (loc 1)
        glVertexAttribPointer(1, 2, GL_FLOAT, false, VERTEX_SIZE_BYTES, 2 * Float.BYTES);
        glEnableVertexAttribArray(1);
        
        // Szín (loc 2)
        glVertexAttribPointer(2, 4, GL_FLOAT, false, VERTEX_SIZE_BYTES, 4 * Float.BYTES);
        glEnableVertexAttribArray(2);

        glBindVertexArray(0);
    }
    
    /**
     * Elindít egy új köteget a megadott vetítési mátrixszal.
     * @param projectionMatrix A vetítési mátrix (UI vagy Világ)
     */
    public void startBatch(float[] projectionMatrix) {
        this.currentProjectionMatrix = projectionMatrix;
        
        glUseProgram(textureProgram);
        glUniformMatrix4fv(texUniProjection, false, projectionMatrix);
        glBindVertexArray(textureVAO);
        
        textureBatchBuffer.clear();
        quadCount = 0;
    }
    
    /**
     * Beköti a rajzoláshoz használt textúrát.
     * A Renderer felelőssége, hogy hívja, miután kiürítette az előző köteget.
     * @param textureId A betöltött textúra OpenGL azonosítója
     */
    public void bindTexture(int textureId) {
        this.currentTextureId = textureId;
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, textureId);
        glUniform1i(texUniTexture, 0);
    }

    /**
     * Kirajzolja az eddig összegyűjtött quad-okat.
     */
    public void flushBatch() {
        if (quadCount == 0) return;

        textureBatchBuffer.flip();
        
        glBindVertexArray(textureVAO); // Biztonság kedvéért
        glBindBuffer(GL_ARRAY_BUFFER, textureVBO);
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, textureEBO);
        
        // Adatok feltöltése a GPU-ra
        glBufferSubData(GL_ARRAY_BUFFER, 0, textureBatchBuffer);
        
        // Rajzolás
        glDrawElements(GL_TRIANGLES, quadCount * 6, GL_UNSIGNED_INT, 0);
        
        // Buffer kiürítése a következő ciklushoz
        textureBatchBuffer.clear();
        quadCount = 0;
    }

    /**
     * Hozzáad egy quad-ot (négy vertexet) a kötegelő pufferhez.
     * Ha a puffer megtelt, automatikusan kiüríti (flush) és újraindítja a köteget.
     */
    public void addQuadToBatch(float x0, float y0, float x1, float y1, // Pozíció
                               float u0, float v0, float u1, float v1, // UV
                               float r, float g, float b, float a) {   // Szín
        
        if (quadCount >= MAX_QUADS) {
            // Puffer megtelt, kényszerített flush
            flushBatch();
            
            // Újra kell kezdeni a köteget, de a Renderer nem tud róla,
            // ezért a jelenlegi mátrixot és textúrát használjuk.
            glUseProgram(textureProgram);
            glUniformMatrix4fv(texUniProjection, false, this.currentProjectionMatrix);
            glBindVertexArray(textureVAO);
            bindTexture(this.currentTextureId);
        }

        // 1. vertex (bal alsó)
        textureBatchBuffer.put(x0).put(y0).put(u0).put(v0).put(r).put(g).put(b).put(a);
        // 2. vertex (jobb alsó)
        textureBatchBuffer.put(x1).put(y0).put(u1).put(v0).put(r).put(g).put(b).put(a);
        // 3. vertex (jobb felső)
        textureBatchBuffer.put(x1).put(y1).put(u1).put(v1).put(r).put(g).put(b).put(a);
        // 4. vertex (bal felső)
        textureBatchBuffer.put(x0).put(y1).put(u0).put(v1).put(r).put(g).put(b).put(a);

        quadCount++;
    }


    /**
     * Törli a shader programot és a puffereket.
     */
    public void cleanup() {
        glDeleteProgram(textureProgram);
        glDeleteVertexArrays(textureVAO);
        glDeleteBuffers(textureVBO);
        glDeleteBuffers(textureEBO);
        if (textureBatchBuffer != null) {
            MemoryUtil.memFree(textureBatchBuffer);
        }
    }
    
    /**
     * Segédfüggvény a shader program létrehozásához.
     */
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
}