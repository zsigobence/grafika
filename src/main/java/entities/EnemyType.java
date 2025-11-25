package main.java.entities;

import main.java.config.AssetPaths;

public enum EnemyType {
    BASIC(AssetPaths.TEXTURE_ENEMY_BASIC),
    FAST(AssetPaths.TEXTURE_ENEMY_FAST),
    TANK(AssetPaths.TEXTURE_ENEMY_TANK),
    RANGED(AssetPaths.TEXTURE_ENEMY_RANGED);

    private final String texturePath;

    EnemyType(String texturePath) {
        this.texturePath = texturePath;
    }

    public String getTexturePath() {
        return texturePath;
    }
}