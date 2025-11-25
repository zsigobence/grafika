package main.java.config;

public class Config {
    // Ablak és alapbeállítások
    public static final int WINDOW_WIDTH = 800;
    public static final int WINDOW_HEIGHT = 600;
    public static final String WINDOW_TITLE = "Top-Down Shooter - VampireStyle";
    public static final int TARGET_UPS = 60;
    
    public static final int MAX_QUADS = 10000;
    public static final int GRID_CELL_SIZE = 64;
    
    // Játékmenet egyensúly konstansok
    public static class Gameplay {
        public static final float MAGNET_COOLDOWN_SEC = 30.0f;
        public static final float ORBIT_BLADE_RADIUS = 100f;
        public static final float ORBIT_BLADE_SPEED = 3.2f;
        public static final float LASER_SPEED = 1200f;
        public static final float ORBIT_BLADE_CD = 0.5f;
        
        public static final int BOSS_DAMAGE = 5;
        public static final int ENEMY_COLLISION_DAMAGE = 1;
    }
}