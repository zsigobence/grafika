package main.java.entities;

import main.java.config.AssetPaths;

public enum BossType {
    GHOST(AssetPaths.TEXTURE_BOSS_GHOST),
    DEMON(AssetPaths.TEXTURE_BOSS_DEMON),
    DRAGON(AssetPaths.TEXTURE_BOSS_DRAGON);
    
    private final String texturePath;
    
    BossType(String texturePath) {
        this.texturePath = texturePath;
    }
    
    public String getTexturePath() {
        return texturePath;
    }
}
