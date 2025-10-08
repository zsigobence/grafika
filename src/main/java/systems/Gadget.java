package main.java.systems;

public class Gadget {
    public String name;
    public String description;
    public int level;
    public final int maxLevel;

    public Gadget(String name, String description, int maxLevel) {
        this.name = name;
        this.description = description;
        this.level = 0;
        this.maxLevel = maxLevel;
    }
    
    public void levelUp() {
        if (level < maxLevel) level++;
    }
}