package main.java;


import org.lwjgl.*;
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

    private int score = 0;

    // XP + szintelés
    private int xp = 99;
    private int level = 1;
    private int xpToNext = 100;

    private Player player;
    private List<Bullet> bullets = new ArrayList<>();
    private List<Enemy> enemies = new ArrayList<>();
    private List<XPOrb> xpOrbs = new ArrayList<>(); // XP orbok listája
    private List<FloatingText> floatingTexts = new ArrayList<>(); // lebegő feliratok
    
    private boolean levelUpMenuActive = false;
    private List<Gadget> availableGadgets = new ArrayList<>(); // Szintlépéskor felkínált gadgetek

    // Gadget lista
    private List<Gadget> gadgets = new ArrayList<>();

    private double lastTime;
    private boolean keyUp, keyDown, keyLeft, keyRight, keyShoot;

    private double enemySpawnTimer = 0.0;
    private double enemySpawnInterval = 0.9; // mp

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
        SoundManager.setVolume("shoot", 0.1f);
        SoundManager.loadSound("xp", "src/main/sounds/xp.ogg");
        SoundManager.setVolume("xp", 0.4f);
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

        // Autolövés: lő a legközelebbi ellenség felé
        float autoShootRange = 500.0f; // módosítsd szükség szerint

	     // --- Módosított rész az update()-ben: csak akkor lő auto ellenségre, ha az a megadott távolságon belül van ---
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
	                 // csak akkor lőjünk, ha a legközelebbi ellenség a megadott tartományon belül van
	                 if (dist <= autoShootRange) {
	                     float dx = nearest.x - player.x;
	                     float dy = nearest.y - player.y;
	                     float len = (float)Math.sqrt(dx*dx + dy*dy);
	                     if (len == 0) len = 1.0f;
	                     float bSpeed = 700.0f;
	                     bullets.add(new Bullet(player.x, player.y, dx/len * bSpeed, dy/len * bSpeed));
	                     SoundManager.play("shoot");
	                 }
	             }
	         }
	         player.shootCooldown = 0.1f;
	        }

        // Space manuális lövés
        if (keyShoot && player.shootCooldown <= 0) {
            bullets.add(new Bullet(player.x, player.y, 0.0f, -400.0f));
            player.shootCooldown = 0.2f;
        }

        // Lövedékek frissítése
        bullets.removeIf(b -> {
            b.update(deltaTime);
            return b.x < -50 || b.x > worldWidth + 50 || b.y < -50 || b.y > worldHeight + 50;
        });

        // Ellenségek spawnolása
        enemySpawnTimer += deltaTime;
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

            enemies.add(new Enemy(ex, ey, vx, vy, type));
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
                    bulletIter.remove();
                    enemy.hp -= player.damage; // játékos sebzését vonjuk le
                    if (enemy.isDead()) {
                        int xpValue = (enemy.type == EnemyType.BASIC) ? 5 : 10;
                        xpOrbs.add(new XPOrb(enemy.x, enemy.y, xpValue, enemy.type));
                        enemyIterator.remove();
                        score += enemy.type == EnemyType.TANK ? 30 : 10;
                    }
                    break;
                }
            }

            if (checkCollision(enemy, player)) {
                player.hp--; // ellenség sebzi a játékost
                int xpValue = (enemy.type == EnemyType.BASIC) ? 5 : 10;
                xpOrbs.add(new XPOrb(enemy.x, enemy.y, xpValue, enemy.type));
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
                floatingTexts.add(new FloatingText(player.x, player.y - player.size, (orb.value >= 10 ? "+10" : "+5"), 1.2f, -40.0f, 1.0f, 1.0f, 0.2f));
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

        // FloatingText frissítése (világ koordinátákban)
        Iterator<FloatingText> ftIt = floatingTexts.iterator();
        while (ftIt.hasNext()) {
            FloatingText ft = ftIt.next();
            ft.update(deltaTime);
            if (ft.life <= 0f) ftIt.remove();
        }
    }

    private void render() {
        glClearColor(0.08f, 0.08f, 0.12f, 1.0f);
        glClear(GL_COLOR_BUFFER_BIT);

        glUseProgram(program);
        

        // Kamera: középre a játékos, de ne lépjünk túl a világ határain
        camLeft = player.x - width / 2.0f;
        float camRight = player.x + width / 2.0f;
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
            float size = orb.size;
            float r = 1.0f, g = 1.0f, b = 0.2f;
            if (orb.type == EnemyType.BASIC) {
                size = 8.0f;
                r = 1.0f; g = 1.0f; b = 0.45f;
            } else {
                size = 12.0f;
                r = 1.0f; g = 1.0f; b = 0.12f;
            }
            float pulse = 1.0f + 0.08f * (float)Math.sin(glfwGetTime() * 8.0 + orb.hashCode() % 10);
            glUniformMatrix4fv(uniModel, false, createModelMatrix(orb.x, orb.y, size * pulse, size * pulse));
            glUniform4f(uniColor, r, g, b, 1.0f);
            glDrawElements(GL_TRIANGLES, 6, GL_UNSIGNED_INT, 0);
        }

        glBindVertexArray(0);

        // --- HUD render (XP sáv + Score + szint) ---
        // A HUD-hoz képernyő-koordinátába váltunk: program shader-ünket úgy állítjuk, hogy 0..width, 0..height legyen.
        glUseProgram(program);
        glUniformMatrix4fv(uniProjection, false, ortho(0, width, height, 0, -1.0f, 1.0f));

        // Score (szöveg)
        renderText("Score: " + score, 20, 40, 1.0f, 1f,1f,1f,1f);
        
        
        
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
        // pl.: 100 * 1.4^(lvl-1)
        double val = 100.0 * Math.pow(1.4, Math.max(0, lvl - 1));
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
            
            // Gadget információk - középre igazítva
            String levelText = "(" + gadget.level + "/" + gadget.maxLevel + ")";
            
            // Név középre igazítása
            float nameWidth = getTextWidth(gadget.name, 0.9f);
            float nameX = x - nameWidth / 2f;
            renderText(gadget.name, nameX, y - (boxH/2f) - 20f, 0.9f, 1f, 1f, 1f, 1f);
            
            // Szint információ középre igazítása
            float levelWidth = getTextWidth(levelText, 0.8f);
            float levelX = x - levelWidth / 2f;
            renderText(levelText, levelX, y - boxH/2f + 100f, 0.8f, 1f, 1f, 1f, 1f);
            
            // Következő szint hatása
            if (gadget.level < gadget.maxLevel) {
                String effect = getNextLevelEffect(gadget);
                float effectWidth = getTextWidth(effect, 0.7f);
                float effectX = x - effectWidth / 2f;
                renderText(effect, effectX, y - boxH/2f + 80f, 0.7f, 0f, 1f, 0f, 1f);
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
                return "Blades: " + (3 + gadget.level) + " -> " + (3 + gadget.level + 1);
            case "Attack Speed":
                return "Speed: +" + (gadget.level * 20) + "% -> +" + ((gadget.level + 1) * 20) + "%";
            case "Life Steal":
                return "500 point = +1 HP";
            case "Attack Damage":
                return "Damage: +" + gadget.level + " -> +" + (gadget.level + 1);
            case "Max HP":
                return "Max HP: +" + gadget.level + " -> +" + (gadget.level + 1);
            case "Movement Speed":
                return "Speed: +" + (gadget.level * 10) + "% -> +" + ((gadget.level + 1) * 10) + "%";
            case "Multi Attack":
                int currentDirs = gadget.level == 0 ? 1 : (gadget.level == 1 ? 2 : 4);
                int nextDirs = gadget.level == 0 ? 2 : (gadget.level == 1 ? 4 : 4);
                return "Directions: " + currentDirs + " -> " + nextDirs;
            case "Laser":
                return "Cooldown: " + (10 - gadget.level) + "s -> " + (9 - gadget.level) + "s";
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
    	    ByteBuffer ttfBuffer = BufferUtils.createByteBuffer(fontBytes.length);
    	    ttfBuffer.put(fontBytes);
    	    ttfBuffer.flip();

        int bitmapW = 512, bitmapH = 512;
        ByteBuffer bitmap = memAlloc(bitmapW * bitmapH);

        cdata = STBTTBakedChar.malloc(96);
        stbtt_BakeFontBitmap(ttfBuffer, 24, bitmap, bitmapW, bitmapH, 32, cdata);

        fontTexture = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, fontTexture);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RED, bitmapW, bitmapH, 0, GL_RED, GL_UNSIGNED_BYTE, bitmap);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);

        memFree(bitmap);
        memFree(ttfBuffer);
    	}
    }
    
    private void initGadgets() {
        gadgets.add(new Gadget("Orbit Blade", 0, 3));
        gadgets.add(new Gadget("Attack Speed", 0, 5));
        gadgets.add(new Gadget("Life Steal", 0, 1));
        gadgets.add(new Gadget("Attack Damage", 0, 5));
        gadgets.add(new Gadget("Max HP", 0, 5));
        gadgets.add(new Gadget("Movement Speed", 0, 5));
        gadgets.add(new Gadget("Multi Attack", 0, 3));
        gadgets.add(new Gadget("Laser", 0, 3));
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
        Player(float x, float y, int maxHp, int damage, float moveSpeed) {
            super(x, y, 32, maxHp);
            this.maxHp = maxHp;
            this.hp = maxHp;
            this.damage = damage;
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
        EnemyType type;
        XPOrb(float x, float y, int value, EnemyType type) {
            super(x, y, 10, 1); // alap méretet rendernél felülírjuk
            this.value = value;
            this.type = type;
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
        float vx, vy;
        EnemyType type;

        Enemy(float x, float y, float vx, float vy, EnemyType type) {
            super(x, y, getSize(type), getHp(type));
            this.vx = vx; this.vy = vy; this.type = type;
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

                float wobbleFreq = 8.0f;
                float wobbleAmp = 60.0f;
                float wobble = (float)Math.sin((float)glfwGetTime() * wobbleFreq) * wobbleAmp;

                x += vx * deltaTime + perpX * wobble * deltaTime;
                y += vy * deltaTime + perpY * wobble * deltaTime;
            } else {
                x += vx * deltaTime;
                y += vy * deltaTime;
            }
        }

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