package main.java.entities;

public abstract class Character extends GameObject {
    public int hp, maxHp;

    public Character(float x, float y, float size, int maxHp) {
        super(x, y, size);
        this.maxHp = maxHp;
        this.hp = maxHp;
    }

    public boolean isDead() { return hp <= 0; }
    public void takeDamage(int amount) { this.hp -= amount; }
    public void heal(int amount) { this.hp = Math.min(this.maxHp, this.hp + amount); }
}