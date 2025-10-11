package main.java;


import org.lwjgl.glfw.*;
import org.lwjgl.opengl.*;
import org.lwjgl.stb.STBTTAlignedQuad;
import org.lwjgl.stb.STBTTBakedChar;
import org.lwjgl.system.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.*;
import java.util.*;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33C.*;
import static org.lwjgl.system.MemoryStack.*;
import static org.lwjgl.system.MemoryUtil.*;
import static org.lwjgl.stb.STBTruetype.*;

/**
 * Teljes forrás: tartalmazza az XP-orbokat, mágnes viselkedést, vizuális különbségeket típusonként,
 * lebegő feliratokat pickupkor, és XP-szintelést + HUD XP-sávot (a bal felső XP szöveg helyett).
 */
public class TopDownShooterOpenGL {

    private long window;
    private int width = 800, height = 600;

    private int program, textProgram;
    private int vao, vbo, ebo;
    private int uniProjection, uniModel, uniColor;

    // Világ méretei
    private int worldWidth = 2000;
    private int worldHeight = 2000;

    // Font / text
    private int fontTexture, textVAO, textVBO;
    private STBTTBakedChar.Buffer cdata;
    
    // --- Kamera shake ---
    private float shakeTime = 0f;
    private float shakeIntensity = 0f;

    private int score = 0;

    // XP + szintelés
    private int xp = 0;
    private int level = 1;
    private int xpToNext = 50;

    private Player player;
    private List<Bullet> bullets = new ArrayList<>();
    private List<Enemy> enemies = new ArrayList<>();
    private List<XPOrb> xpOrbs = new ArrayList<>(); // XP orbok listája
    private List<FloatingText> floatingTexts = new ArrayList<>(); // lebegő feliratok
    
    private boolean levelUpMenuActive = false;
    private List<Gadget> availableGadgets = new ArrayList<>(); // Szintlépéskor felkínált gadgetek

    // Gadget lista
    private List<Gadget> gadgets = new ArrayList<>();
    
 // a többi private mező mellé add hozzá
    private GadgetSystem gadgetSystem;



    private double lastTime;
    private boolean keyUp, keyDown, keyLeft, keyRight;

 // idő + spawn vezérlés
    private double elapsedTime = 0.0;           // eltelt idő másodpercben
    private double enemySpawnTimer = 0.0;       // meglévő timer
    private float baseEnemySpawnInterval = 0.9f; // kezdeti (alap) spawn intervallum
    private float enemySpawnInterval = baseEnemySpawnInterval; // aktív intervallum (frissül percenként)


    // Kamera offsetok (render során frissítve)
    private float camLeft = 0, camTop = 0;

    
    public static void main(String[] args) {
        System.out.println("Program elindult");
        new TopDownShooterOpenGL().run();
    }

    public void run() {
        try {
            init();
            loop();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            cleanup();
        }
    }

    private void init() throws Exception {
    	SoundManager.init();
        System.out.println("Inicializálás...");

        if (!glfwInit()) throw new IllegalStateException("Nem sikerült inicializálni a GLFW-t");

        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_FALSE);

        window = glfwCreateWindow(width, height, "Top-Down Shooter - VampireStyle", NULL, NULL);
        if (window == NULL) throw new RuntimeException("Nem sikerült létrehozni az ablakot");

        glfwSetKeyCallback(window, (win, key, scancode, action, mods) -> {
            if (key == GLFW_KEY_ESCAPE && action == GLFW_PRESS)
                glfwSetWindowShouldClose(win, true);

            boolean pressed = action == GLFW_PRESS || action == GLFW_REPEAT;
            if (key == GLFW_KEY_W) keyUp = pressed;
            if (key == GLFW_KEY_S) keyDown = pressed;
            if (key == GLFW_KEY_A) keyLeft = pressed;
            if (key == GLFW_KEY_D) keyRight = pressed;
        });
        
        glfwSetMouseButtonCallback(window, (win, button, action, mods) -> {
            if (button == GLFW_MOUSE_BUTTON_LEFT && action == GLFW_PRESS) {
                double[] xpos = new double[1];
                double[] ypos = new double[1];
                glfwGetCursorPos(window, xpos, ypos);
                handleMouseClick((float)xpos[0], (float)ypos[0]);
            }
        });

        try (MemoryStack stack = stackPush()) {
            IntBuffer pWidth = stack.mallocInt(1);
            IntBuffer pHeight = stack.mallocInt(1);
            glfwGetWindowSize(window, pWidth, pHeight);
            GLFWVidMode vidmode = glfwGetVideoMode(glfwGetPrimaryMonitor());
            glfwSetWindowPos(window,
                    (vidmode.width() - pWidth.get(0)) / 2,
                    (vidmode.height() - pHeight.get(0)) / 2);
        }

        glfwMakeContextCurrent(window);
        glfwSwapInterval(1);
        glfwShowWindow(window);

        GL.createCapabilities();
        System.out.println("OpenGL verzió: " + glGetString(GL_VERSION));

        // Shaderek
        program = createProgram();
        if (program == 0) throw new RuntimeException("Shader program létrehozása sikertelen");
        uniProjection = glGetUniformLocation(program, "uProjection");
        uniModel = glGetUniformLocation(program, "uModel");
        uniColor = glGetUniformLocation(program, "uColor");

        float[] vertices = {
                -0.5f, -0.5f,
                0.5f, -0.5f,
                0.5f, 0.5f,
                -0.5f, 0.5f
        };
        int[] indices = {0, 1, 2, 2, 3, 0};

        vao = glGenVertexArrays();
        vbo = glGenBuffers();
        ebo = glGenBuffers();

        glBindVertexArray(vao);

        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, vertices, GL_STATIC_DRAW);

        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ebo);
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, indices, GL_STATIC_DRAW);

        glVertexAttribPointer(0, 2, GL_FLOAT, false, 2 * Float.BYTES, 0);
        glEnableVertexAttribArray(0);
        glBindVertexArray(0);

        player = new Player(worldWidth / 2.0f, worldHeight / 2.0f, 
                10,   // maxHp
                1,    // damage
                250f  // moveSpeed
);

        lastTime = glfwGetTime();

        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        // Text program és font init
        textProgram = createTextProgram();
        initFont();
        
        // Gadgetek inicializálása
        initGadgets();
        // init() végén, initGadgets() után:
        recomputePlayerStats();
        gadgetSystem = new GadgetSystem();


        // Text VAO/VBO egyszer létrehozva
        textVAO = glGenVertexArrays();
        textVBO = glGenBuffers();
        glBindVertexArray(textVAO);
        glBindBuffer(GL_ARRAY_BUFFER, textVBO);
        glVertexAttribPointer(0, 2, GL_FLOAT, false, 4 * Float.BYTES, 0);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(1, 2, GL_FLOAT, false, 4 * Float.BYTES, 2 * Float.BYTES);
        glEnableVertexAttribArray(1);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(0);
       


        
        SoundManager.loadSound("shoot", "src/main/sounds/shoot.ogg");
        SoundManager.setVolume("shoot", 0.3f);
        SoundManager.loadSound("xp", "src/main/sounds/xp.ogg");
        SoundManager.setVolume("xp", 0.4f);
        SoundManager.loadSound("damage", "src/main/sounds/damage.ogg");
        SoundManager.setVolume("damage", 0.4f);
        SoundManager.loadSound("laser", "src/main/sounds/laser.ogg");
        SoundManager.setVolume("laser", 0.4f);
        SoundManager.loadSound("flying-blade", "src/main/sounds/flying-blade.ogg");
        SoundManager.setVolume("flying-blade", 0.4f);
        SoundManager.loadSound("levelup", "src/main/sounds/levelup.ogg");
        SoundManager.setVolume("levelup", 0.5f);
        SoundManager.loadSound("bgm", "src/main/sounds/bgm.ogg");
        SoundManager.setVolume("bgm", 1.0f);
        SoundManager.loop("bgm");


        System.out.println("Inicializálás kész");
    }

    private void loop() {
        while (!glfwWindowShouldClose(window)) {
            double currentTime = glfwGetTime();
            float deltaTime = (float) (currentTime - lastTime);
            lastTime = currentTime;

            update(deltaTime);
            render();

            glfwSwapBuffers(window);
            glfwPollEvents();
        }
    }

    private void update(float deltaTime) {
    	
    	if (levelUpMenuActive) {
    	    return; // játék megáll, amíg választás nem történik
    	}
    	// frissítjük az összesített eltelt időt
    	elapsedTime += deltaTime;
    	
        float moveX = 0, moveY = 0;
        if (keyUp) moveY -= 1;
        if (keyDown) moveY += 1;
        if (keyLeft) moveX -= 1;
        if (keyRight) moveX += 1;

        if (moveX != 0 || moveY != 0) {
            float len = (float) Math.sqrt(moveX * moveX + moveY * moveY);
            moveX /= len;
            moveY /= len;
        }

        // játékos sebessége Player mezőből
        player.x += moveX * player.moveSpeed * deltaTime;
        player.y += moveY * player.moveSpeed * deltaTime;

        player.x = Math.max(player.size / 2, Math.min(worldWidth - player.size / 2, player.x));
        player.y = Math.max(player.size / 2, Math.min(worldHeight - player.size / 2, player.y));



        player.shootCooldown -= deltaTime;
        if (player.shootCooldown <= 0) {
            if (!enemies.isEmpty()) {
                Enemy nearest = null;
                float bestDistSq = Float.MAX_VALUE;
                for (Enemy e : enemies) {
                    float dx = e.x - player.x;
                    float dy = e.y - player.y;
                    float d2 = dx * dx + dy * dy;
                    if (d2 < bestDistSq) { bestDistSq = d2; nearest = e; }
                }
                if (nearest != null) {
                    float dist = (float)Math.sqrt(bestDistSq);
                    if (dist <= 500.0f) { // auto shoot range
                        float dx = nearest.x - player.x;
                        float dy = nearest.y - player.y;
                        float len = (float)Math.sqrt(dx*dx + dy*dy);
                        if (len == 0) len = 1.0f;
                        float bSpeed = 700.0f;
                        spawnPlayerBullets(player.x, player.y, dx/len, dy/len, bSpeed);
                        SoundManager.play("shoot");
                    }
                }
            }
            // beállítjuk a cooldown-t Attack Speed alapján
            player.shootCooldown = 0.75f * getAttackSpeedMultiplier();
        }


        // Lövedékek frissítése
        bullets.removeIf(b -> {
            b.update(deltaTime);
            return b.x < -50 || b.x > worldWidth + 50 || b.y < -50 || b.y > worldHeight + 50;
        });

        // Ellenségek spawnolása
     // percben és 3-perces lépésekben számolt nehézség
        int minutesElapsed = (int)(elapsedTime / 60.0);
        int difficultyStages = minutesElapsed / 3; // minden 3. perc után egy stage

        // spawn interval csökken percenként (kis mértékben). Clampeljük minimum értékre.
        float spawnMultiplier = Math.max(0.25f, 1.0f - 0.15f * minutesElapsed); // percenként ~7% gyorsulás, legfeljebb 75% gyorsulás
        enemySpawnInterval = baseEnemySpawnInterval * spawnMultiplier;
        enemySpawnTimer += deltaTime;
        // nehézség szorzó (HP és sebesség növelésére minden 3. perc után)
        float difficultyMultiplier = 1.0f + 0.15f * difficultyStages; // minden stage +15% erő
        float spawnRadius = Math.max(width, height) * 0.8f + 200.0f;
        if (enemySpawnTimer > enemySpawnInterval) {
            enemySpawnTimer = 0.0;
            double angle = Math.random() * Math.PI * 2.0;
            float ex = (float)(player.x + Math.cos(angle) * spawnRadius);
            float ey = (float)(player.y + Math.sin(angle) * spawnRadius);
            ex = Math.max(20, Math.min(worldWidth - 20, ex));
            ey = Math.max(20, Math.min(worldHeight - 20, ey));

            EnemyType type;
            double r = Math.random();
            if (r < 0.6) type = EnemyType.BASIC;
            else if (r < 0.85) type = EnemyType.FAST;
            else type = EnemyType.TANK;

            float dirX = player.x - ex;
            float dirY = player.y - ey;
            float len = (float)Math.sqrt(dirX*dirX + dirY*dirY);
            if (len == 0) len = 1.0f;

            float baseSpeed;
            switch (type) {
                case BASIC: baseSpeed = 80.0f + (float)(Math.random()*40.0); break;
                case FAST:  baseSpeed = 150.0f + (float)(Math.random()*60.0); break;
                case TANK:  baseSpeed = 45.0f + (float)(Math.random()*30.0); break;
                default:    baseSpeed = 80.0f; break;
            }

            float vx = dirX / len * baseSpeed;
            float vy = dirY / len * baseSpeed;

            Enemy spawned = new Enemy(ex, ey, vx, vy, type);

            spawned.maxHp = Math.max(1, Math.round(spawned.maxHp * difficultyMultiplier));
            spawned.hp = spawned.maxHp;

            enemies.add(spawned);

        }

        // Ellenségek frissítése és ütközések
        Iterator<Enemy> enemyIterator = enemies.iterator();
        while (enemyIterator.hasNext()) {
            Enemy enemy = enemyIterator.next();
            enemy.update(deltaTime, player);

            Iterator<Bullet> bulletIter = bullets.iterator();
            while (bulletIter.hasNext()) {
                Bullet b = bulletIter.next();
                if (checkCollision(enemy, b)) {
                    if (b instanceof LaserBullet) {
                        LaserBullet lb = (LaserBullet) b;
                        lb.onHit(enemy); // sebzés és pierce csökkentés
                        if (lb.pierce <= 0) bulletIter.remove();
                    } else {
                        // normál lövedék: sebzés a játékos damage alapján, és lövedék eltávolítása
                        enemy.hp -= player.damage;
                        SoundManager.playOverlap("damage");
                        bulletIter.remove();
                    }

                    if (enemy.isDead()) {
                        xpOrbs.add(new XPOrb(enemy.x, enemy.y, enemy.getXp()));
                        enemyIterator.remove();
                        score += enemy.type == EnemyType.TANK ? 30 : 10;

                        // Life Steal: ha van Life Steal szint, gyógyítunk öléskor
                        int ls = getGadgetLevel("Life Steal");
                        if (ls > 0) {
                            float chance = ls * 0.03f; // 3% per szint
                            if (Math.random() < chance) {
                                player.hp = Math.min(player.maxHp, player.hp + 1);
                                floatingTexts.add(new FloatingText(
                                    player.x, player.y - 40,
                                    "+HP", 1.0f, -40f,
                                    0.3f, 1f, 0.3f
                                ));
                            }
                        }
                    }
                    break;
                }
            }


            if (checkCollision(enemy, player)) {
                player.hp--; // ellenség sebzi a játékost
                triggerShake(8f, 0.3f);
                xpOrbs.add(new XPOrb(enemy.x, enemy.y, enemy.getXp())); 
                enemyIterator.remove();
                if (player.isDead()) {
                    System.out.println("Game Over!");
                    glfwSetWindowShouldClose(window, true);
                }
            }
        }

        // XP orbok frissítése: mágnes hatás, pickup ellenőrzés, és határokon túl törlés
        Iterator<XPOrb> xpIter = xpOrbs.iterator();
        while (xpIter.hasNext()) {
            XPOrb orb = xpIter.next();

            // Mágnes viselkedés: ha a játékos közel (pl. < 200 px), mozogjon felé
            float dx = player.x - orb.x;
            float dy = player.y - orb.y;
            float dist = (float)Math.sqrt(dx*dx + dy*dy);
            float magnetRadius = 70f;
            if (dist < magnetRadius && dist > 1f) {
                float baseSpeed = 90f; // alap lassú sebesség
                float pullExtra = (magnetRadius - dist) / magnetRadius * 200f; // min->max gyorsulás
                float speedToPlayer = baseSpeed + pullExtra;
                orb.x += dx / dist * speedToPlayer * deltaTime;
                orb.y += dy / dist * speedToPlayer * deltaTime;
            } else {
                // enyhe lebegés / ringatózás
                orb.y += Math.sin(glfwGetTime() * 3.0f + orb.hashCode() % 10) * 2.0f * deltaTime;
            }

            if (orb.x < -100 || orb.x > worldWidth + 100 || orb.y < -100 || orb.y > worldHeight + 100) {
                xpIter.remove();
                continue;
            }

            if (checkCollision(orb, player)) {
                xp += orb.value;

                // lebegő felirat pickupról
                floatingTexts.add(new FloatingText(player.x, player.y - player.size, "+" + orb.value, 1.2f, -40.0f, 1.0f, 1.0f, 0.2f));
                SoundManager.play("xp");
                
                // szintellenőrzés és esetleges szintlépés
                if (xp >= xpToNext) {
                    xp -= xpToNext;
                    level++;
                    xpToNext = calcXpForLevel(level);
                    floatingTexts.add(new FloatingText(player.x, player.y - player.size - 20, "Level Up!", 1.6f, -70.0f, 1.0f, 0.8f, 0.0f));
                    levelUpMenuActive = true;   // szintlépéskor menü aktiválódik
                    SoundManager.play("levelup");
                    generateLevelUpOptions();
                }

                xpIter.remove();
            }
        }
        

        if (!levelUpMenuActive) {
            gadgetSystem.update(deltaTime);
        }


        // FloatingText frissítése (világ koordinátákban)
        Iterator<FloatingText> ftIt = floatingTexts.iterator();
        while (ftIt.hasNext()) {
            FloatingText ft = ftIt.next();
            ft.update(deltaTime);
            if (ft.life <= 0f) ftIt.remove();
        }
        
        if (shakeTime > 0) {
            shakeTime -= deltaTime;
            if (shakeTime < 0) shakeTime = 0;
        }
    }

    private void render() {
        glClearColor(0.08f, 0.08f, 0.12f, 1.0f);
        glClear(GL_COLOR_BUFFER_BIT);

        glUseProgram(program);
        

        // Kamera: középre a játékos, de ne lépjünk túl a világ határain
        camLeft = player.x - width / 2.0f;
        float camRight = player.x + width / 2.0f;
        
        if (shakeTime > 0) {
            double angle = Math.random() * Math.PI * 2.0;
            float offset = shakeIntensity * (float) (Math.random() * 2 - 1);
            camLeft += Math.cos(angle) * offset;
            camTop  += Math.sin(angle) * offset;
        }
        camTop = player.y - height / 2.0f;
        float camBottom = player.y + height / 2.0f;
        if (camLeft < 0) { camLeft = 0; camRight = width; }
        if (camRight > worldWidth) { camRight = worldWidth; camLeft = worldWidth - width; }
        if (camTop < 0) { camTop = 0; camBottom = height; }
        if (camBottom > worldHeight) { camBottom = worldHeight; camTop = worldHeight - height; }

        float[] projectionMatrix = ortho(camLeft, camRight, camBottom, camTop, -1.0f, 1.0f);
        glUniformMatrix4fv(uniProjection, false, projectionMatrix);

        glBindVertexArray(vao);

        // Rács háttér a világon
        int cell = 64;
        for (int gx = 0; gx < worldWidth; gx += cell) {
            for (int gy = 0; gy < worldHeight; gy += cell) {
                float[] model = createModelMatrix(gx + cell/2.0f, gy + cell/2.0f, cell-2, cell-2);
                glUniformMatrix4fv(uniModel, false, model);
                glUniform4f(uniColor, 0.16f, 0.16f, 0.19f, 1.0f);
                glDrawElements(GL_TRIANGLES, 6, GL_UNSIGNED_INT, 0);
            }
        }

        // Player
        glUniformMatrix4fv(uniModel, false, createModelMatrix(player.x, player.y, player.size, player.size));
        glUniform4f(uniColor, 0.2f, 0.9f, 0.2f, 1.0f);
        glDrawElements(GL_TRIANGLES, 6, GL_UNSIGNED_INT, 0);
        renderHealthBar(player);
        


        // Lövedékek 
        for (Bullet b : bullets) {
            if (b instanceof LaserBullet) continue; // Laser-okat a GadgetSystem rendeli
            glUniformMatrix4fv(uniModel, false, createModelMatrix(b.x, b.y, b.size, b.size));
            glUniform4f(uniColor, 1.0f, 0.9f, 0.2f, 1.0f);
            glDrawElements(GL_TRIANGLES, 6, GL_UNSIGNED_INT, 0);
        }





        // Ellenségek
        for (Enemy enemy : enemies) {
            glUniformMatrix4fv(uniModel, false, createModelMatrix(enemy.x, enemy.y, enemy.size, enemy.size));
            switch (enemy.type) {
                case BASIC: glUniform4f(uniColor, 0.9f, 0.2f, 0.2f, 1.0f); break;
                case FAST:  glUniform4f(uniColor, 0.2f, 0.9f, 0.9f, 1.0f); break;
                case TANK:  glUniform4f(uniColor, 0.5f, 0.5f, 0.2f, 1.0f); break;
            }
            glDrawElements(GL_TRIANGLES, 6, GL_UNSIGNED_INT, 0);
            renderHealthBar(enemy);
        }

        // XP Orbs kirajzolása (típusonként más méret / fényerő)
        for (XPOrb orb : xpOrbs) {
            float size = 15f - (15f - orb.value / 2);
            float r = 1.0f, g = 1.0f, b = 0.2f;
            r = 1.0f; g = 1.0f; b = 0.45f;
            float pulse = 1.0f + 0.08f * (float)Math.sin(glfwGetTime() * 8.0 + orb.hashCode() % 10);
            glUniformMatrix4fv(uniModel, false, createModelMatrix(orb.x, orb.y, size * pulse, size * pulse));
            glUniform4f(uniColor, r, g, b, 1.0f);
            glDrawElements(GL_TRIANGLES, 6, GL_UNSIGNED_INT, 0);
        }
        
        // GadgetSystem render (orbit blade + LaserBullet vizualok)
        if (gadgetSystem != null) gadgetSystem.render();
        glBindVertexArray(0);

        // --- HUD render (XP sáv + Score + szint) ---
        // A HUD-hoz képernyő-koordinátába váltunk: program shader-ünket úgy állítjuk, hogy 0..width, 0..height legyen.
        glUseProgram(program);
        glUniformMatrix4fv(uniProjection, false, ortho(0, width, height, 0, -1.0f, 1.0f));

        // Score (szöveg)
        renderText("Score: " + score, 20, 40, 1.0f, 1f,1f,1f,1f);
        
        // Idő formázása mm:ss
        int totalSeconds = (int) Math.floor(elapsedTime);
        int mins = totalSeconds / 60;
        int secs = totalSeconds % 60;
        String timeStr = String.format("%02d:%02d", mins, secs);

        // egyszerű approx szélesség (használhatod a getTextWidth-et is pontosításhoz)
        float textW = getTextWidth(timeStr, 1.0f);
        renderText(timeStr, width - 20 - textW, 40, 1.0f, 1f, 1f, 1f, 1f);

        
        
        // XP sáv: a bal felső sarok alá
     // Középre igazított XP sáv és fölötte a szint szöveg
        float barW = 220f;
        float barH = 18f;
        float centerScreenX = width / 2.0f;

        // Szint szöveg (középre igazítva - egyszerű approx szélességszámítással)
        String levelText = "Level: " + level;
        float approxCharWidth = 10f; // egyszerű becslés karakterenként
        float levelTextWidth = levelText.length() * approxCharWidth;
        float levelTextX = centerScreenX - levelTextWidth / 2f;
        float levelTextY = 18f; // tetejétől néhány pixel lefelé
        renderText(levelText, levelTextX, levelTextY, 1.0f, 1f, 1f, 1f, 1f);

        // XP sáv közvetlenül a szint alatt
        float barX = centerScreenX - barW / 2f;
        float barY = levelTextY + 26f; // egy kis távolság a szöveg és a sáv között
        float barCenterX = barX + barW / 2f;
        float barCenterY = barY + barH / 2f;

        glUseProgram(program);
        glUniformMatrix4fv(uniProjection, false, ortho(0, width, height, 0, -1.0f, 1.0f));
        glBindVertexArray(vao);

        // Háttérsáv
        glUniformMatrix4fv(uniModel, false, createModelMatrix(barCenterX, barCenterY, barW, barH));
        glUniform4f(uniColor, 0.85f, 0.85f, 0.87f, 1.0f);
        glDrawElements(GL_TRIANGLES, 6, GL_UNSIGNED_INT, 0);

        // Kitöltött rész (progress)
        float percent = xpToNext > 0 ? (float) xp / (float) xpToNext : 0f;
        percent = Math.max(0f, Math.min(1f, percent));
        float fillW = barW * percent;
        if (fillW > 0.001f) {
            float fillCenterX = barX + fillW / 2f;
            glUniformMatrix4fv(uniModel, false, createModelMatrix(fillCenterX, barCenterY, fillW, barH - 2));
            float fr = Math.min(1f, 0.2f + percent * 1.2f);
            float fg = Math.min(1f, 0.9f - percent * 0.2f + 0.1f);
            float fb = 0.05f;
            glUniform4f(uniColor, fr, fg, fb, 1.0f);
            glDrawElements(GL_TRIANGLES, 6, GL_UNSIGNED_INT, 0);
        }

        glBindVertexArray(0);
        
     // Gadgetek megjelenítése (bal alsó sarok)
        renderGadgetsInfo();

        // Level up menu
        if (levelUpMenuActive) {
            renderLevelUpMenu();
        }

        // FloatingTexts kirajzolása: világ koordinátában vannak -> konvertáljuk képernyő koordinátára
        glUseProgram(textProgram);
        if (!levelUpMenuActive) {
            for (FloatingText ft : floatingTexts) {
                float screenX = ft.x - camLeft;
                float screenY = ft.y - camTop;
                float alpha = Math.max(0f, Math.min(1f, ft.life / ft.initialLife));
                renderText(ft.text, screenX, screenY, 1.0f, ft.r, ft.g, ft.b, alpha);
            }
        }
    }
    
    private void generateLevelUpOptions() {
        availableGadgets.clear();
        
        // Összegyűjtjük a nem maximális szintű gadgeteket
        List<Gadget> nonMaxGadgets = new ArrayList<>();
        for (Gadget gadget : gadgets) {
            if (gadget.level < gadget.maxLevel) {
                nonMaxGadgets.add(gadget);
            }
        }
        
        // Véletlenszerűen kiválasztunk 3-at (vagy amennyi van, ha kevesebb)
        Collections.shuffle(nonMaxGadgets);
        int count = Math.min(3, nonMaxGadgets.size());
        for (int i = 0; i < count; i++) {
            availableGadgets.add(nonMaxGadgets.get(i));
        }
    }

    private void cleanup() {
    	SoundManager.cleanup();
        glDeleteBuffers(ebo);
        glDeleteBuffers(vbo);
        glDeleteVertexArrays(vao);

        glDeleteProgram(program);
        glDeleteProgram(textProgram);
        glDeleteTextures(fontTexture);

        if (cdata != null) cdata.free();
        glDeleteBuffers(textVBO);
        glDeleteVertexArrays(textVAO);

        glfwDestroyWindow(window);
        glfwTerminate();
    }

    // Számolja a következő szint XP küszöbét (növekvő)
    private int calcXpForLevel(int lvl) {
        // pl.: 100 * 1.45^(lvl-1)
        double val = 100.0 * Math.pow(1.45, Math.max(0, lvl - 1));
        return Math.max(20, (int)Math.round(val));
    }

    // Shader programok
    private int createProgram() {
        int vs = glCreateShader(GL_VERTEX_SHADER);
        glShaderSource(vs, vertexShaderSource);
        glCompileShader(vs);
        if (glGetShaderi(vs, GL_COMPILE_STATUS) == GL_FALSE)
            throw new RuntimeException("Vertex shader hiba: " + glGetShaderInfoLog(vs));

        int fs = glCreateShader(GL_FRAGMENT_SHADER);
        glShaderSource(fs, fragmentShaderSource);
        glCompileShader(fs);
        if (glGetShaderi(fs, GL_COMPILE_STATUS) == GL_FALSE)
            throw new RuntimeException("Fragment shader hiba: " + glGetShaderInfoLog(fs));

        int prog = glCreateProgram();
        glAttachShader(prog, vs);
        glAttachShader(prog, fs);
        glLinkProgram(prog);

        if (glGetProgrami(prog, GL_LINK_STATUS) == GL_FALSE)
            throw new RuntimeException("Program link hiba: " + glGetProgramInfoLog(prog));

        glDeleteShader(vs);
        glDeleteShader(fs);
        return prog;
    }
    
    
    private void handleMouseClick(float mouseX, float mouseY) {
        if (levelUpMenuActive) {
            float boxW = 220f, boxH = 120f;
            float gap = 20f;
            float centerX = width / 2f;
            float startY = height / 2f - 50f;

            for (int i = 0; i < availableGadgets.size(); i++) {
                Gadget gadget = availableGadgets.get(i);
                float x = centerX + (i - (availableGadgets.size()-1)/2f) * (boxW + gap);
                float y = startY;
                
                float left = x - boxW/2f;
                float right = x + boxW/2f;
                float top = y - boxH/2f;
                float bottom = y + boxH/2f;
                
                if (mouseX >= left && mouseX <= right && mouseY >= top && mouseY <= bottom) {
                    // Gadget szintjének növelése
                    gadget.levelUp();

                    // azonnal alkalmazzuk a gadget hatását (újraszámoljuk a játékos statjait)
                    recomputePlayerStats();

                    levelUpMenuActive = false;

                    // Vizuális visszajelzés
                    floatingTexts.add(new FloatingText(player.x, player.y, gadget.name + " +1", 1.5f, -50f, 0f, 1f, 0f));
                    break;
                }

            }
        }
    }
    private void renderGadgetsInfo() {
        float startX = 20f;
        float startY = height - 100f;
        float lineHeight = 15f;
        
        renderText("Gadgets:", startX, startY, 0.8f, 1f, 1f, 1f, 1f);
        
        for (int i = 0; i < gadgets.size(); i++) {
            Gadget g = gadgets.get(i);
            if (g.level > 0) {
                String text = g.name + ": " + g.level + "/" + g.maxLevel;
                renderText(text, startX, startY + (i + 1) * lineHeight, 0.7f, 0.8f, 0.8f, 0.8f, 1f);
            }
        }
    }
    
    
 // lekéri egy gadget jelenlegi szintjét
    private int getGadgetLevel(String name) {
        for (Gadget g : gadgets) if (g.name.equals(name)) return g.level;
        return 0;
    }

    // újraszámolja a játékos statjait az alapértékek és a gadgetek alapján
    private void recomputePlayerStats() {
        player.damage = player.baseDamage + getGadgetLevel("Attack Damage");

        int newMaxHp = player.baseMaxHp + getGadgetLevel("Max HP");
        if (newMaxHp != player.maxHp) {
            // megőrizzük az aktuális életerő arányát
            float percent = (float)player.hp / (float)player.maxHp;
            player.maxHp = newMaxHp;
            player.hp = Math.min(player.maxHp, Math.max(1, Math.round(player.maxHp * percent)));
        }

        player.moveSpeed = player.baseMoveSpeed * (1.0f + 0.1f * getGadgetLevel("Movement Speed"));
    }

    // attack speed: kisebb multiplier = gyorsabb lövés
    private float getAttackSpeedMultiplier() {
        int lvl = getGadgetLevel("Attack Speed");
        return 1f / (1f + 0.2f * lvl); // lvl 1 => ~0.83x cooldown, lvl2 => ~0.71x...
    }


    // lövedék spawn a játékostól, figyelembe veszi a multiattacket
    private void spawnPlayerBullets(float px, float py, float dirX, float dirY, float speed) {
        int count = getGadgetLevel("Multi Attack") + 1;
        float baseAngle = (float)Math.atan2(dirY, dirX);
        float spread = (count == 1) ? 0f : (float)Math.toRadians(12f);

        // középső lövedék mindig
        bullets.add(new Bullet(px, py, (float)Math.cos(baseAngle) * speed, (float)Math.sin(baseAngle) * speed));
        if (count == 1) return;

        int remaining = count - 1;
        int pairs = remaining / 2; // hány teljes párt tudunk létrehozni

        // párok + és - irányba
        for (int j = 1; j <= pairs; j++) {
            float off = j * spread;
            float aPos = baseAngle + off;
            float aNeg = baseAngle - off;
            bullets.add(new Bullet(px, py, (float)Math.cos(aPos) * speed, (float)Math.sin(aPos) * speed));
            bullets.add(new Bullet(px, py, (float)Math.cos(aNeg) * speed, (float)Math.sin(aNeg) * speed));
        }

        // ha marad egy egyenes lövés (például count=2 vagy count=4 esetén), tegyük a + oldalra
        if (remaining % 2 == 1) {
            float off = (pairs + 1) * spread;
            float aExtra = baseAngle + off;
            bullets.add(new Bullet(px, py, (float)Math.cos(aExtra) * speed, (float)Math.sin(aExtra) * speed));
        }
    }


    private void renderLevelUpMenu() {
        // Sötét háttér
        glUseProgram(program);
        glUniformMatrix4fv(uniProjection, false, ortho(0, width, height, 0, -1.0f, 1.0f));
        glBindVertexArray(vao);
        
        // Átlátszó fekete háttér
        glUniformMatrix4fv(uniModel, false, createModelMatrix(width/2f, height/2f, width, height));
        glUniform4f(uniColor, 0f, 0f, 0f, 0.7f);
        glDrawElements(GL_TRIANGLES, 6, GL_UNSIGNED_INT, 0);
        
        // Cím
        renderText("LEVEL UP! Choose a gadget:", width/2f - getTextWidth("LEVEL UP! Choose a gadget:", 1.2f)/2f, 60f, 1.2f, 1f, 1f, 0f, 1f);
        
     // Gadget opciók
        float boxW = 220f, boxH = 280f;
        float gap = 20f;
        float centerX = width / 2f;
        float startY = height / 2f;

        for (int i = 0; i < availableGadgets.size(); i++) {
            Gadget gadget = availableGadgets.get(i);
            float x = centerX + (i - (availableGadgets.size()-1)/2f) * (boxW + gap);
            float y = startY;
            
            glUseProgram(program);        
            glBindVertexArray(vao);   

            // Doboz
            glUniformMatrix4fv(uniModel, false, createModelMatrix(x, y, boxW, boxH));
            glUniform4f(uniColor, 0.2f, 0.4f, 0.8f, 1.0f);
            glDrawElements(GL_TRIANGLES, 6, GL_UNSIGNED_INT, 0);
            

            
            // Név középre igazítása
            float nameWidth = getTextWidth(gadget.name, 0.9f);
            float nameX = x - nameWidth / 2f;
            renderText(gadget.name, nameX, y - (boxH/2f) - 20f, 0.9f, 1f, 1f, 1f, 1f);
           

            
            // Következő szint hatása
            if (gadget.level < gadget.maxLevel) {
                String effect = getNextLevelEffect(gadget);
                float effectWidth = getTextWidth(effect, 0.7f);
                float effectX = x - effectWidth / 2f;
                renderText(effect, effectX, y - boxH/2f + 80f, 0.7f, 0f, 1f, 0f, 1f);
            }
         // --- SZINTEK VIZUALIZÁCIÓJA (kis négyzetek a gadget szintjéhez) ---
            int max = gadget.maxLevel;
            int filled = Math.max(0, Math.min(gadget.level, max));

            float squareSize = 16f;
            float squareGap  = 6f;
            float totalWidth = max * squareSize + (max - 1) * squareGap;
            float startSqX   = x - totalWidth / 2f;
            float sqY = y - 100;

            glUseProgram(program);
            glBindVertexArray(vao);
            
            for (int s = 0; s < max; s++) {
                float sx = startSqX + s * (squareSize + squareGap);
                glUniformMatrix4fv(uniModel, false, createModelMatrix(sx + squareSize / 2f, sqY, squareSize, squareSize));

                if (s < filled) {
                    // már megszerzett szint -> erős sárga
                    glUniform4f(uniColor, 1.0f, 0.85f, 0.05f, 1.0f);
                } else if (s == filled && filled < max) {
                    // a következő szint -> halvány sárga
                    glUniform4f(uniColor, 1.0f, 0.85f, 0.05f, 0.4f);
                } else {
                    // még nem elért -> fekete
                    glUniform4f(uniColor, 0.05f, 0.05f, 0.05f, 1.0f);
                }

                glDrawElements(GL_TRIANGLES, 6, GL_UNSIGNED_INT, 0);
            }

        }

        glBindVertexArray(0);
    }
    
    private float getTextWidth(String text, float scale) {
        return text.length() * 10f * scale; 
    }

    private String getNextLevelEffect(Gadget gadget) {
        switch (gadget.name) {
            case "Orbit Blade":
                return "Blades: " + (gadget.level != 0 ? (2 + gadget.level) + " -> " + (2 + gadget.level + 1) : "3");
            case "Attack Speed":
                return "Speed: +" + (gadget.level * 20) + "% -> +" + ((gadget.level + 1) * 20) + "%";
            case "Life Steal":
                return "Chance to lifesteal " + (gadget.level * 3) + "% -> +" + ((gadget.level + 1) * 5 + "%");
            case "Attack Damage":
                return "Damage: +" + gadget.level + " -> +" + (gadget.level + 1);
            case "Max HP":
                return "Max HP: +" + gadget.level + " -> +" + (gadget.level + 1);
            case "Movement Speed":
                return "Speed: +" + (gadget.level * 10) + "% -> +" + ((gadget.level + 1) * 10) + "%";
            case "Multi Attack":
                return "Directions: " + (gadget.level + 1) + " -> " + (gadget.level + 2);
            case "Laser":
                return "Cooldown: " + (gadget.level != 0 ? (9 - gadget.level * 2) + "s -> " + (7 - gadget.level * 2) + "s" : "7" + "s");
            default:
                return "Upgrade";
        }
    }

    private int createTextProgram() {
        int vs = glCreateShader(GL_VERTEX_SHADER);
        glShaderSource(vs, textVertexShaderSource);
        glCompileShader(vs);
        if (glGetShaderi(vs, GL_COMPILE_STATUS) == GL_FALSE)
            throw new RuntimeException("Text vertex shader hiba: " + glGetShaderInfoLog(vs));

        int fs = glCreateShader(GL_FRAGMENT_SHADER);
        glShaderSource(fs, textFragmentShaderSource);
        glCompileShader(fs);
        if (glGetShaderi(fs, GL_COMPILE_STATUS) == GL_FALSE)
            throw new RuntimeException("Text fragment shader hiba: " + glGetShaderInfoLog(fs));

        int prog = glCreateProgram();
        glAttachShader(prog, vs);
        glAttachShader(prog, fs);
        glLinkProgram(prog);

        if (glGetProgrami(prog, GL_LINK_STATUS) == GL_FALSE)
            throw new RuntimeException("Text program link hiba: " + glGetProgramInfoLog(prog));

        glDeleteShader(vs);
        glDeleteShader(fs);
        return prog;
    }

    private float[] ortho(float left, float right, float bottom, float top, float near, float far) {
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

    private float[] createModelMatrix(float x, float y, float w, float h) {
        return new float[]{
                w, 0, 0, 0,
                0, h, 0, 0,
                0, 0, 1, 0,
                x, y, 0, 1
        };
    }

    private boolean checkCollision(Entity a, Entity b) {
        return Math.abs(a.x - b.x) < (a.size + b.size) / 2f &&
               Math.abs(a.y - b.y) < (a.size + b.size) / 2f;
    }

    private void renderHealthBar(Entity e) {
        float barWidth = e.size, barHeight = 5;
        float healthPercent = Math.max(0, (float) e.hp / e.maxHp);

        float[] model = createModelMatrix(e.x, e.y - e.size / 2 - 10, barWidth, barHeight);
        glUniformMatrix4fv(uniModel, false, model);
        glUniform4f(uniColor, 0.8f, 0, 0, 1f);
        glDrawElements(GL_TRIANGLES, 6, GL_UNSIGNED_INT, 0);

        model = createModelMatrix(e.x - barWidth * (1f - healthPercent) / 2,
                e.y - e.size / 2 - 10,
                barWidth * healthPercent, barHeight);
        glUniformMatrix4fv(uniModel, false, model);
        glUniform4f(uniColor, 0, 0.8f, 0, 1f);
        glDrawElements(GL_TRIANGLES, 6, GL_UNSIGNED_INT, 0);
    }

    private void initFont() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/fonts/arial.ttf")) {
            if (in == null) {
                throw new IOException("Font file not found!");
            }
            byte[] fontBytes = in.readAllBytes();

            // ALLOCATE THE FONT BYTES WITH memAlloc so we can memFree() later safely
            ByteBuffer ttfBuffer = MemoryUtil.memAlloc(fontBytes.length);
            ttfBuffer.put(fontBytes);
            ttfBuffer.flip();

            int bitmapW = 512, bitmapH = 512;
            ByteBuffer bitmap = MemoryUtil.memAlloc(bitmapW * bitmapH);

            cdata = STBTTBakedChar.malloc(96);
            int rc = stbtt_BakeFontBitmap(ttfBuffer, 24, bitmap, bitmapW, bitmapH, 32, cdata);
            if (rc <= 0) {
                // ha valamiért nem sikerül a sütés, dobjunk kivételt (megjegyzés: rc>0 általában OK)
                MemoryUtil.memFree(bitmap);
                MemoryUtil.memFree(ttfBuffer);
                cdata.free();
                throw new IOException("stbtt_BakeFontBitmap failed (rc=" + rc + ")");
            }

            fontTexture = glGenTextures();
            glBindTexture(GL_TEXTURE_2D, fontTexture);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RED, bitmapW, bitmapH, 0, GL_RED, GL_UNSIGNED_BYTE, bitmap);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
            glBindTexture(GL_TEXTURE_2D, 0);

            // freed the native buffers we allocated
            MemoryUtil.memFree(bitmap);
            MemoryUtil.memFree(ttfBuffer);
        }
    }

    
    private void initGadgets() {
        gadgets.add(new Gadget("Orbit Blade", 0, 5));
        gadgets.add(new Gadget("Attack Speed", 0, 5));
        gadgets.add(new Gadget("Life Steal", 0, 3));
        gadgets.add(new Gadget("Attack Damage", 0, 5));
        gadgets.add(new Gadget("Max HP", 0, 5));
        gadgets.add(new Gadget("Movement Speed", 0, 5));
        gadgets.add(new Gadget("Multi Attack", 0, 4));
        gadgets.add(new Gadget("Laser", 0, 5));
    }
    


    /**
     * Render text (új szignatúra: tetszőleges RGBA szín)
     * x,y: képernyő koordináták (0..width, 0..height) - használjuk HUDhoz és lebegő feliratokhoz
     */
    private void renderText(String text, float x, float y, float scale, float r, float g, float b, float a) {
        glUseProgram(textProgram);
        glUniformMatrix4fv(glGetUniformLocation(textProgram, "uProjection"), false,
                ortho(0, width, height, 0, -1, 1));
        glUniform4f(glGetUniformLocation(textProgram, "uTextColor"), r, g, b, a);
        glUniform1i(glGetUniformLocation(textProgram, "uTexture"), 0);

        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, fontTexture);

        glBindVertexArray(textVAO);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer xBuf = stack.floats(x / scale);
            FloatBuffer yBuf = stack.floats(y / scale);

            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                if (c < 32 || c >= 128) continue;

                STBTTAlignedQuad q = STBTTAlignedQuad.malloc(stack);
                stbtt_GetBakedQuad(cdata, 512, 512, c - 32, xBuf, yBuf, q, true);

                float[] vertices = {
                        q.x0() * scale, q.y0() * scale, q.s0(), q.t0(),
                        q.x1() * scale, q.y0() * scale, q.s1(), q.t0(),
                        q.x0() * scale, q.y1() * scale, q.s0(), q.t1(),
                        q.x1() * scale, q.y1() * scale, q.s1(), q.t1()
                };
                glBindBuffer(GL_ARRAY_BUFFER, textVBO);
                glBufferData(GL_ARRAY_BUFFER, vertices, GL_DYNAMIC_DRAW);
                glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
            }
        }
        glBindVertexArray(0);
    }

    // Shaderok
    private static final String vertexShaderSource =
            "#version 330 core\n" +
            "layout (location = 0) in vec2 aPos;\n" +
            "uniform mat4 uModel;\n" +
            "uniform mat4 uProjection;\n" +
            "void main() { gl_Position = uProjection * uModel * vec4(aPos, 0.0, 1.0); }";

    private static final String fragmentShaderSource =
            "#version 330 core\n" +
            "out vec4 FragColor;\n" +
            "uniform vec4 uColor;\n" +
            "void main() { FragColor = uColor; }";

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

    // Entity osztályok
    private static abstract class Entity {
        float x, y, size;
        int hp, maxHp;
        Entity(float x, float y, float size, int hp) {
            this.x = x; this.y = y; this.size = size;
            this.hp = hp; this.maxHp = hp;
        }
        boolean isDead() { return hp <= 0; }
    }

    private static class Player extends Entity {
        float shootCooldown = 0;
        int damage;
        float moveSpeed;

        // újak az alap értékekhez, hogy újraszámolható legyen
        final int baseDamage;
        final float baseMoveSpeed;
        final int baseMaxHp;

        Player(float x, float y, int maxHp, int damage, float moveSpeed) {
            super(x, y, 32, maxHp);
            this.baseMaxHp = maxHp;
            this.maxHp = maxHp;
            this.hp = maxHp;

            this.baseDamage = damage;
            this.damage = damage;

            this.baseMoveSpeed = moveSpeed;
            this.moveSpeed = moveSpeed;
        }
    }


    private static class Bullet extends Entity {
        float vx, vy;
        Bullet(float x, float y, float vx, float vy) {
            super(x, y, 8, 1);
            this.vx = vx; this.vy = vy;
        }
        void update(float dt) { x += vx * dt; y += vy * dt; }
    }

    private enum EnemyType { BASIC, FAST, TANK }


    // Új: XP orb entitás (tárolja a típust is, hogy vizuálisan különböztessük)
    private static class XPOrb extends Entity {
        int value;
        XPOrb(float x, float y, int value) {
            super(x, y, 10, 1); // alap méretet rendernél felülírjuk
            this.value = value;
        }
    }

    // Floating text osztály: világ koordinátában tároljuk, akkor konvertáljuk képernyőre rendernél
    private static class FloatingText {
        float x, y;
        String text;
        float life;
        final float initialLife;
        float vy; // függőleges sebesség (negatív = fel)
        float r,g,b;
        FloatingText(float x, float y, String text, float life, float vy, float r, float g, float b) {
            this.x = x; this.y = y; this.text = text; this.life = life; this.initialLife = life; this.vy = vy;
            this.r=r; this.g=g; this.b=b;
        }
        void update(float dt) {
            life -= dt;
            y += vy * dt;
            vy *= 0.98f;
        }
    }

    private static class Enemy extends Entity {
        private float vx, vy;
        private EnemyType type;
        private int xp;
        

        Enemy(float x, float y, float vx, float vy, EnemyType type) {
            super(x, y, getSize(type), getHp(type));
            this.vx = vx; this.vy = vy; this.type = type;
            if(type == EnemyType.BASIC) this.xp = 15;
            if(type == EnemyType.FAST) this.xp = 10;
            if(type == EnemyType.TANK) this.xp = 20;
        }
        
        

        static float getSize(EnemyType type) {
            switch (type) {
                case BASIC: return 28.0f;
                case FAST:  return 20.0f;
                case TANK:  return 40.0f;
                default:    return 28.0f;
            }
        }

        static int getHp(EnemyType type) {
            switch (type) {
                case BASIC: return 3;
                case FAST:  return 1;
                case TANK:  return 6;
                default:    return 3;
            }
        }
        
        int getXp() {
        	return this.xp;
        }

        void update(float deltaTime, Player player) {
            float dirX = player.x - x;
            float dirY = player.y - y;
            float dist = (float)Math.sqrt(dirX*dirX + dirY*dirY);
            if (dist == 0f) dist = 1f;
            float targetDirX = dirX / dist;
            float targetDirY = dirY / dist;

            float curSpeed = (float)Math.hypot(vx, vy);
            if (curSpeed == 0f) {
                switch (type) {
                    case FAST: curSpeed = 150f; break;
                    case TANK: curSpeed = 50f; break;
                    default:   curSpeed = 80f; break;
                }
            }
            float curDirX = vx / curSpeed;
            float curDirY = vy / curSpeed;

            float steer = (type == EnemyType.TANK) ? 0.05f : (type == EnemyType.FAST ? 0.12f : 0.08f);

            float ndx = curDirX * (1.0f - steer) + targetDirX * steer;
            float ndy = curDirY * (1.0f - steer) + targetDirY * steer;
            float ndLen = (float)Math.sqrt(ndx*ndx + ndy*ndy);
            if (ndLen == 0f) ndLen = 1f;
            ndx /= ndLen;
            ndy /= ndLen;

            vx = ndx * curSpeed;
            vy = ndy * curSpeed;

            if (type == EnemyType.FAST) {
                float perpX = -vy;
                float perpY = vx;
                float plen = (float)Math.sqrt(perpX*perpX + perpY*perpY);
                if (plen == 0f) plen = 1f;
                perpX /= plen;
                perpY /= plen;

                float wobbleFreq = 10.0f;
                float wobbleAmp = 200.0f;
                float wobble = (float)Math.sin((float)glfwGetTime() * wobbleFreq) * wobbleAmp;

                x += vx * deltaTime + perpX * wobble * deltaTime;
                y += vy * deltaTime + perpY * wobble * deltaTime;
            } else {
                x += vx * deltaTime;
                y += vy * deltaTime;
            }
        }

    }
    
 // LaserBullet: nagyobb, áthatoló lövedék
    private static class LaserBullet extends Bullet {
        int pierce;
        int damage;

        LaserBullet(float x, float y, float vx, float vy, int damage, int pierce) {
            super(x, y, vx, vy);
            this.damage = damage;
            this.pierce = pierce;
            this.size = 18; // nagyobb vizuál
        }

        void onHit(Enemy e) {
            e.hp -= damage;
            pierce--;
            SoundManager.play("damage");
        }
    }
    
 // GadgetSystem: kezeli az orbit pengéket és a laser működését (update + render)
    private class GadgetSystem {
        private final Map<Enemy, Float> orbitHitTimers = new HashMap<>();
        private float laserTimer = 0f;
        private float radius = 100f;          
        private float spin = 3.2f;

        // frissítés (hívjuk minden frame-ben, ha nincs levelup menu)
        void update(float dt) {
            updateOrbitBlades(dt);
            updateLaser(dt);
        }

        // kirajzolás: orbit blades és laser-lövedékek vizuálja
        void render() {
            renderOrbitBlades();
            renderLaserBullets();
        }

        /* ------------------ Orbit blade logika ------------------ */
        private void updateOrbitBlades(float dt) {
            int orbitLevel = getGadgetLevel("Orbit Blade");
            if (orbitLevel <= 0) return;

            int count = 2 + orbitLevel;
            float time = (float) glfwGetTime();

            List<Enemy> toRemove = new ArrayList<>();

            for (int i = 0; i < count; i++) {
                float angle = time * this.spin + i * ((float) Math.PI * 2f / count);
                float bx = player.x + (float) Math.cos(angle) * this.radius;
                float by = player.y + (float) Math.sin(angle) * this.radius;

                // ütközés vizsgálat minden enemy-vel
                for (Enemy e : enemies) {
                    float d = (float) Math.hypot(e.x - bx, e.y - by);
                    float hitRadius = (e.size + 10f) / 2f;
                    Float cd = orbitHitTimers.get(e);
                    if (cd == null) cd = 0f;

                    if (d < hitRadius && cd <= 0f) {
                        e.hp -= 1;
                        SoundManager.play("damage");
                        orbitHitTimers.put(e, 0.45f);

                        if (e.hp <= 0 && !toRemove.contains(e)) {
                            toRemove.add(e);

                            // XP érték típus alapján
                            xpOrbs.add(new XPOrb(e.x, e.y, e.getXp()));
                            score += e.type == EnemyType.TANK ? 30 : 10;

                            // Life Steal aktiválása
                            int ls = getGadgetLevel("Life Steal");
                            if (ls > 0) {
                                float chance = ls * 0.03f; // 3% per szint
                                if (Math.random() < chance) {
                                    player.hp = Math.min(player.maxHp, player.hp + 1);
                                    floatingTexts.add(new FloatingText(
                                        player.x, player.y - 40,
                                        "+HP", 1.0f, -40f,
                                        0.3f, 1f, 0.3f
                                    ));
                                }
                            }

                           
                        }
                    }
                }
            }

            // halott ellenfelek eltávolítása
            enemies.removeAll(toRemove);

            // cooldown csökkentése
            Iterator<Map.Entry<Enemy, Float>> it = orbitHitTimers.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<Enemy, Float> en = it.next();
                float val = en.getValue() - dt;
                if (val <= 0f) it.remove();
                else en.setValue(val);
            }
        }


        private void renderOrbitBlades() {
            int orbitLevel = getGadgetLevel("Orbit Blade");
            if (orbitLevel <= 0) return;
            if (!SoundManager.isPlaying("orbit_loop")) {
                SoundManager.loop("orbit_loop");
            }
            int count = 2 + orbitLevel;
            float time = (float)glfwGetTime();

            // Használjuk ugyanazt a VAO/quad render pipeline-t mint a fő render
            glUseProgram(program);
            glBindVertexArray(vao);
            for (int i = 0; i < count; i++) {
                float angle = time * this.spin + i * ((float)Math.PI * 2f / count);
                float bx = player.x + (float)Math.cos(angle) * this.radius;
                float by = player.y + (float)Math.sin(angle) * this.radius;
                glUniformMatrix4fv(uniModel, false, createModelMatrix(bx, by, 30f, 30f));
                glUniform4f(uniColor, 0.9f, 0.9f, 0.2f, 1.0f);
                glDrawElements(GL_TRIANGLES, 6, GL_UNSIGNED_INT, 0);
            }
            glBindVertexArray(0);
        }

        /* ------------------ Laser logika ------------------ */
        private void updateLaser(float dt) {
            laserTimer -= dt;
            int laserLevel = getGadgetLevel("Laser");
            if (laserLevel <= 0) return;

            if (laserTimer <= 0f && !enemies.isEmpty()) {
                // cél: legközelebbi enemy
                Enemy nearest = null; float best = Float.MAX_VALUE;
                for (Enemy e : enemies) {
                    float dx = e.x - player.x, dy = e.y - player.y;
                    float d2 = dx*dx + dy*dy;
                    if (d2 < best) { best = d2; nearest = e; }
                }
                if (nearest != null) {
                    float dx = nearest.x - player.x;
                    float dy = nearest.y - player.y;
                    float len = (float)Math.sqrt(dx*dx + dy*dy); if (len==0f) len=1f;
                    float speed = 1200f;
                    int damage = 2 + laserLevel * 2;
                    int pierce = 4 + laserLevel * 2; // eggyel több, hogy jobban látszódjon
                    bullets.add(new LaserBullet(player.x, player.y, dx/len*speed, dy/len*speed, damage, pierce));
                    // cooldown csökkenthető a szinttel (min 1s)

                    SoundManager.play("laser");
                    laserTimer = Math.max(1f, 9f - laserLevel * 2);
                }
            }
        }

        private void renderLaserBullets() {
            glUseProgram(program);
            glBindVertexArray(vao);

            // végigmegyünk a bullets listán és csak a LaserBullet-eket rajzoljuk itt
            for (Bullet b : bullets) {
                if (!(b instanceof LaserBullet)) continue;
                LaserBullet lb = (LaserBullet) b;

                // glow (átlátszó, nagyobb kör)
                glUniformMatrix4fv(uniModel, false, createModelMatrix(lb.x, lb.y, lb.size * 2.4f, lb.size * 2.4f));
                glUniform4f(uniColor, 1.0f, 0.2f, 0.2f, 0.22f);
                glDrawElements(GL_TRIANGLES, 6, GL_UNSIGNED_INT, 0);

                // mag (élénk piros)
                glUniformMatrix4fv(uniModel, false, createModelMatrix(lb.x, lb.y, lb.size, lb.size));
                glUniform4f(uniColor, 1.0f, 0.08f, 0.08f, 1.0f);
                glDrawElements(GL_TRIANGLES, 6, GL_UNSIGNED_INT, 0);
            }

            glBindVertexArray(0);
        }

    }

    private void triggerShake(float intensity, float duration) {
        shakeIntensity = intensity;
        shakeTime = duration;
    }


    
 // Gadget osztály
    private static class Gadget {
        String name;
        int level;
        int maxLevel;
        
        Gadget(String name, int level, int maxLevel) {
            this.name = name;
            this.level = level;
            this.maxLevel = maxLevel;
        }
        
        void levelUp() {
            if (level < maxLevel) {
                level++;
            }
        }
    }
    
}
