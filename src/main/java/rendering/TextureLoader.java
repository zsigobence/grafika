package main.java.rendering;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.lwjgl.stb.STBImage;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.HashMap;
import java.util.Map;

public class TextureLoader {
    private static TextureLoader instance;
    private Map<String, TextureInfo> textureInfoCache;
    
    public static class TextureInfo {
        public final int width;
        public final int height;
        public final int textureId;
        
        public TextureInfo(int width, int height, int textureId) {
            this.width = width;
            this.height = height;
            this.textureId = textureId;
        }
    }
    
    private TextureLoader() {
        textureInfoCache = new HashMap<>();
    }
    
    public static TextureLoader getInstance() {
        if (instance == null) {
            instance = new TextureLoader();
        }
        return instance;
    }
    
    public TextureInfo loadTexture(String path) {
        if (textureInfoCache.containsKey(path)) {
            return textureInfoCache.get(path);
        }
        
        try {
            IntBuffer width = BufferUtils.createIntBuffer(1);
            IntBuffer height = BufferUtils.createIntBuffer(1);
            IntBuffer channels = BufferUtils.createIntBuffer(1);
            
            STBImage.stbi_set_flip_vertically_on_load(false);
            ByteBuffer imageBuffer = STBImage.stbi_load(path, width, height, channels, 4);
            
            if (imageBuffer == null) {
                System.err.println("Failed to load texture: " + path + " - " + STBImage.stbi_failure_reason());
                return null;
            }
            
            int textureWidth = width.get(0);
            int textureHeight = height.get(0);
            
            int textureId = GL11.glGenTextures();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId);
            
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, textureWidth, textureHeight, 
                            0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, imageBuffer);
            
            GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
            STBImage.stbi_image_free(imageBuffer);
            
            TextureInfo textureInfo = new TextureInfo(textureWidth, textureHeight, textureId);
            textureInfoCache.put(path, textureInfo);
            
            System.out.println("Texture loaded: " + path + " (" + textureWidth + "x" + textureHeight + ", ID: " + textureId + ")");
            return textureInfo;
            
        } catch (Exception e) {
            System.err.println("Exception loading texture " + path + ": " + e.getMessage());
            return null;
        }
    }
    
    public TextureInfo getTextureInfo(String texturePath) {
        TextureInfo info = textureInfoCache.get(texturePath);
        if (info == null) {
            info = loadTexture(texturePath);
        }
        return info;
    }
    
    public boolean isTextureLoaded(String texturePath) {
        return textureInfoCache.containsKey(texturePath);
    }
    
    public void cleanup() {
        for (TextureInfo textureInfo : textureInfoCache.values()) {
            GL11.glDeleteTextures(textureInfo.textureId);
        }
        textureInfoCache.clear();
    }
}