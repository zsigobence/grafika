package main.java.entities;

public abstract class GameObject {
    public float x, y, size;

    public GameObject(float x, float y, float size) {
        this.x = x;
        this.y = y;
        this.size = size;
    }
}