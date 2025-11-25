package main.java.rendering;

import org.lwjgl.system.MemoryUtil;
import java.nio.FloatBuffer;
import static org.lwjgl.opengl.GL33C.*;

public class TextureRenderer {

    private int textureProgram;
    private int texUniProjection, texUniTexture;
    private int textureVAO, textureVBO, textureEBO;

    private static final int MAX_QUADS = 10000;
    private static final int MAX_VERTICES = MAX_QUADS * 4;
    private static final int MAX_INDICES = MAX_QUADS * 6;
    
    // Pozíció (2) + UV (2) + Szín (4)
    private static final int VERTEX_SIZE_FLOATS = 2 + 2 + 4; 
    private static final int VERTEX_SIZE_BYTES = VERTEX_SIZE_FLOATS * Float.BYTES;

    private FloatBuffer textureBatchBuffer;
    private int quadCount = 0;
    
    private float[] currentProjectionMatrix;
    private int currentTextureId;

    private static final String textureVertexShaderSource =
        "#version 330 core\n" +
        "layout (location = 0) in vec2 aPos;\n" +
        "layout (location = 1) in vec2 aTexCoord;\n" +
        "layout (location = 2) in vec4 aTint;\n" + 
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
        "    FragColor = texColor * vTint;\n" + 
        "}";

    
    public void init() {
        textureProgram = createShader(textureVertexShaderSource, textureFragmentShaderSource);
        texUniProjection = glGetUniformLocation(textureProgram, "uProjection");
        texUniTexture = glGetUniformLocation(textureProgram, "uTexture");
        
        textureBatchBuffer = MemoryUtil.memAllocFloat(MAX_VERTICES * VERTEX_SIZE_FLOATS);

        textureVAO = glGenVertexArrays();
        glBindVertexArray(textureVAO);

        textureVBO = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, textureVBO);
        glBufferData(GL_ARRAY_BUFFER, (long)MAX_VERTICES * VERTEX_SIZE_BYTES, GL_DYNAMIC_DRAW);
        
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
        
        glVertexAttribPointer(0, 2, GL_FLOAT, false, VERTEX_SIZE_BYTES, 0);
        glEnableVertexAttribArray(0);
        
        glVertexAttribPointer(1, 2, GL_FLOAT, false, VERTEX_SIZE_BYTES, 2 * Float.BYTES);
        glEnableVertexAttribArray(1);
        
        glVertexAttribPointer(2, 4, GL_FLOAT, false, VERTEX_SIZE_BYTES, 4 * Float.BYTES);
        glEnableVertexAttribArray(2);

        glBindVertexArray(0);
    }
    
    public void startBatch(float[] projectionMatrix) {
        this.currentProjectionMatrix = projectionMatrix;
        
        glUseProgram(textureProgram);
        glUniformMatrix4fv(texUniProjection, false, projectionMatrix);
        glBindVertexArray(textureVAO);
        
        textureBatchBuffer.clear();
        quadCount = 0;
    }
    
    public void bindTexture(int textureId) {
        this.currentTextureId = textureId;
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, textureId);
        glUniform1i(texUniTexture, 0);
    }

    public void flushBatch() {
        if (quadCount == 0) return;

        textureBatchBuffer.flip();
        
        glBindVertexArray(textureVAO);
        glBindBuffer(GL_ARRAY_BUFFER, textureVBO);
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, textureEBO);
        
        glBufferSubData(GL_ARRAY_BUFFER, 0, textureBatchBuffer);
        
        glDrawElements(GL_TRIANGLES, quadCount * 6, GL_UNSIGNED_INT, 0);
        
        textureBatchBuffer.clear();
        quadCount = 0;
    }

    public void addQuadToBatch(float x0, float y0, float x1, float y1,
                               float u0, float v0, float u1, float v1,
                               float r, float g, float b, float a) {   
        
        if (quadCount >= MAX_QUADS) {
            flushBatch();
            
            glUseProgram(textureProgram);
            glUniformMatrix4fv(texUniProjection, false, this.currentProjectionMatrix);
            glBindVertexArray(textureVAO);
            bindTexture(this.currentTextureId);
        }

        textureBatchBuffer.put(x0).put(y0).put(u0).put(v0).put(r).put(g).put(b).put(a);
        textureBatchBuffer.put(x1).put(y0).put(u1).put(v0).put(r).put(g).put(b).put(a);
        textureBatchBuffer.put(x1).put(y1).put(u1).put(v1).put(r).put(g).put(b).put(a);
        textureBatchBuffer.put(x0).put(y1).put(u0).put(v1).put(r).put(g).put(b).put(a);

        quadCount++;
    }


    public void cleanup() {
        glDeleteProgram(textureProgram);
        glDeleteVertexArrays(textureVAO);
        glDeleteBuffers(textureVBO);
        glDeleteBuffers(textureEBO);
        if (textureBatchBuffer != null) {
            MemoryUtil.memFree(textureBatchBuffer);
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
}