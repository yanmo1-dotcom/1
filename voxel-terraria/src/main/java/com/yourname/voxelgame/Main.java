package com.yourname.voxelgame;

import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.opengl.GL;

import com.yourname.voxelgame.world.BlockAccess;
import com.yourname.voxelgame.world.BlockType;
import com.yourname.voxelgame.world.Camera;
import com.yourname.voxelgame.world.ChunkManager;
import com.yourname.voxelgame.world.Raycaster;
import com.yourname.voxelgame.entity.Enemy;
import com.yourname.voxelgame.entity.ItemEntity;
import com.yourname.voxelgame.entity.Player;
import com.yourname.voxelgame.entity.Slime;
import com.yourname.voxelgame.entity.CombatUtil;
import com.yourname.voxelgame.inventory.CraftingGrid;
import com.yourname.voxelgame.inventory.Inventory;
import com.yourname.voxelgame.inventory.ItemRegistry;
import com.yourname.voxelgame.inventory.ItemStack;
import com.yourname.voxelgame.audio.SoundManager;
import com.yourname.voxelgame.particle.ParticleSystem;
import com.yourname.voxelgame.save.SaveManager;
import com.yourname.voxelgame.ui.FontRenderer;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.system.MemoryUtil.NULL;

/**
 * 体素泰拉瑞亚 —— 第八步：背包 UI + 合成系统 + 工具耐久。
 */
public class Main {

    private static final int WIDTH = 960;
    private static final int HEIGHT = 540;

    private static float mouseX = WIDTH / 2f;
    private static float mouseY = HEIGHT / 2f;
    private static boolean leftClickEdge = false;   // 单次点击边沿
    private static boolean rightClickEdge = false;
    private static boolean leftHeld = false;        // 按住（破坏进度）
    private static boolean inventoryOpen = false;
    private static boolean eEdge = false;

    // 背包 UI：cursor 携带的物品堆
    private static ItemStack cursorStack = new ItemStack();

    // 游戏状态机
    private enum GState { MENU, PLAYING, PAUSED }
    private static GState gstate = GState.MENU;

    // 菜单按钮（屏幕坐标）
    private static final float[] BTN_START = { WIDTH/2f - 100, HEIGHT/2f - 10, 200, 36 };
    private static final float[] BTN_QUIT  = { WIDTH/2f - 100, HEIGHT/2f + 40, 200, 36 };
    private static final float[] BTN_RESUME = { WIDTH/2f - 100, HEIGHT/2f - 10, 200, 36 };
    private static final float[] BTN_SAVEQUIT = { WIDTH/2f - 100, HEIGHT/2f + 40, 200, 36 };

    private static boolean hasSave = false;
    private static ChunkManager worldRef;
    private static FontRenderer font;
    private static FontRenderer fontSmall;

    public static void main(String[] args) {
        GLFWErrorCallback errorCallback = GLFWErrorCallback.createPrint(System.err);
        errorCallback.set();
        if (!glfwInit()) throw new IllegalStateException("Unable to initialize GLFW");

        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 2);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 1);

        long window = glfwCreateWindow(WIDTH, HEIGHT, "Voxel Terraria", NULL, NULL);
        if (window == NULL) throw new RuntimeException("Failed to create the GLFW window");

        GLFWVidMode vidmode = glfwGetVideoMode(glfwGetPrimaryMonitor());
        glfwSetWindowPos(window, (vidmode.width() - WIDTH) / 2, (vidmode.height() - HEIGHT) / 2);

        glfwMakeContextCurrent(window);
        glfwSwapInterval(1);
        glfwShowWindow(window);
        GL.createCapabilities();
        font = new FontRenderer(28);
        fontSmall = new FontRenderer(18);

        glEnable(GL_DEPTH_TEST);
        glClearColor(0.4f, 0.6f, 0.9f, 1.0f);

        Camera camera = new Camera(WIDTH, HEIGHT);
        camera.setEye(0f, 12f, 30.0f);
        glfwSetScrollCallback(window, camera);

        ChunkManager world = new ChunkManager(1337L);
        worldRef = world;
        world.update(0f, 0f);
        long t0 = System.nanoTime();
        while (world.getPendingCount() > 0 && (System.nanoTime() - t0) / 1_000_000_000.0 < 5.0) {
            try { Thread.sleep(10); } catch (InterruptedException ie) {}
        }
        for (int i = 0; i < 30; i++) world.update(0f, 0f);

        Raycaster.Hit hit = new Raycaster.Hit();
        Player player = spawnPlayer(world, 0, 0);
        CraftingGrid crafting = new CraftingGrid();

        // 输入回调
        glfwSetCursorPosCallback(window, (w, x, y) -> { mouseX = (float) x; mouseY = (float) y; });
        glfwSetMouseButtonCallback(window, (w, button, action, mods) -> {
            if (action == GLFW_PRESS) {
                if (button == GLFW_MOUSE_BUTTON_1) { leftClickEdge = true; leftHeld = true; }
                if (button == GLFW_MOUSE_BUTTON_2) rightClickEdge = true;
            } else if (action == GLFW_RELEASE) {
                if (button == GLFW_MOUSE_BUTTON_1) leftHeld = false;
            }
        });
        glfwSetKeyCallback(window, (w, key, scancode, action, mods) -> {
            if (action == GLFW_PRESS) {
                if (key >= GLFW_KEY_1 && key <= GLFW_KEY_9) player.inventory().select(key - GLFW_KEY_1);
                if (key == GLFW_KEY_E) {
                    if (gstate == GState.PLAYING) eEdge = true;
                }
                if (key == GLFW_KEY_ESCAPE) {
                    if (inventoryOpen) { inventoryOpen = false; }
                    else if (gstate == GState.PLAYING) { gstate = GState.PAUSED; SoundManager.play(SoundManager.Sound.CLICK); }
                    else if (gstate == GState.PAUSED) { gstate = GState.PLAYING; SoundManager.play(SoundManager.Sound.CLICK); }
                    else glfwSetWindowShouldClose(w, true);
                }
            }
        });

        List<Enemy> enemies = new ArrayList<>();
        List<ItemEntity> items = new ArrayList<>();
        int[] killCount = {0};
        float spawnTimer = 2.0f;
        final int MAX_ENEMIES = 3;
        final float SPAWN_INTERVAL = 5.0f;

        float dayTime = 6000f;
        ParticleSystem particles = new ParticleSystem();

        // 检测存档
        SaveManager.SaveData saveData = SaveManager.load();
        hasSave = saveData.hasSave;
        if (hasSave) world.loadEdits(saveData.blocks);

        long last = System.nanoTime();
        long fpsAccum = 0;
        int fpsCount = 0;
        int printTick = 0;

        // 破坏进度
        float breakProgress = 0f;
        int breakTargetX = Integer.MIN_VALUE, breakTargetY = -1, breakTargetZ = Integer.MIN_VALUE;
        int lastDayPhase = 0; // 用于昼夜切换音效

        while (!glfwWindowShouldClose(window)) {
            long now = System.nanoTime();
            float dt = Math.min((now - last) / 1_000_000_000.0f, 0.05f);
            last = now;

            // 菜单状态：不推进游戏
            if (gstate == GState.MENU || gstate == GState.PAUSED) {
                glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
                drawMenu(gstate == GState.MENU);
                // 菜单点击
                if (leftClickEdge) {
                    leftClickEdge = false;
                    handleMenuClick(gstate == GState.MENU, player, saveData);
                }
                glfwSwapBuffers(window);
                glfwPollEvents();
                continue;
            }

            // 昼夜
            dayTime += dt * 100f;
            if (dayTime >= 24000f) dayTime -= 24000f;
            float dayFactor = computeDayFactor(dayTime);
            float[] sky = computeSkyColor(dayTime);
            glClearColor(sky[0], sky[1], sky[2], 1.0f);
            // 昼夜切换音效（相位 0=夜 1=晨 2=日 3=昏）
            int phase = (dayTime < 5000) ? 0 : (dayTime < 7000) ? 1 : (dayTime < 17000) ? 2 : (dayTime < 19000) ? 3 : 0;
            if (phase != lastDayPhase) {
                if (phase == 2) SoundManager.play(SoundManager.Sound.DAY); // 进入白天
                lastDayPhase = phase;
            }

            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
            particles.update(dt);

            // E 键切换背包
            if (eEdge) { eEdge = false; inventoryOpen = !inventoryOpen; }

            // 游戏输入（背包打开时暂停移动/攻击）
            int moveX = 0;
            boolean jump = false, attack = false;
            if (!inventoryOpen) {
                if (glfwGetKey(window, GLFW_KEY_A) == GLFW_PRESS) moveX -= 1;
                if (glfwGetKey(window, GLFW_KEY_D) == GLFW_PRESS) moveX += 1;
                jump = glfwGetKey(window, GLFW_KEY_SPACE) == GLFW_PRESS;
                attack = glfwGetKey(window, GLFW_KEY_J) == GLFW_PRESS;
            }

            // 跳跃音效
            if (jump && player.isOnGround()) SoundManager.play(SoundManager.Sound.JUMP);
            player.update(world, moveX, jump, attack, dt);

            if (player.isDead()) {
                player.respawn();
                enemies.clear();
            } else {
                // 受伤音效：检查 hp 是否下降
            }

            // 敌怪 + 接触伤害
            Iterator<Enemy> eit = enemies.iterator();
            while (eit.hasNext()) {
                Enemy e = eit.next();
                e.updateAI(world, player, dt);
                if (e.intersectsEntity(player)) {
                    if (player.hurt(10, 0.5f)) {
                        float dir = (player.getX() - e.getX()) >= 0 ? 1f : -1f;
                        player.knockback(dir, 4.0f, 3.0f);
                        SoundManager.play(SoundManager.Sound.HURT);
                    }
                }
                if (e.isDead()) eit.remove();
            }

            // 玩家攻击命中
            if (player.getAttackAnim() > 0) {
                float dir = player.getFacingX();
                for (Enemy e : enemies) {
                    if (e.isDead()) continue;
                    if (CombatUtil.inMeleeArc(player, dir, player.getAttackRange(), player.getAttackArc(), e)) {
                        if (e.hurt(player.getAttackDamage(), 0.3f)) {
                            CombatUtil.applyKnockback(e, dir, player.getKnockbackSpeed(), 2.0f);
                            SoundManager.play(SoundManager.Sound.HIT);
                            // 用剑攻击扣耐久
                            if (player.inventory().heldToolType() == ItemRegistry.ToolType.SWORD) {
                                player.inventory().damageHeldTool();
                            }
                            if (e.isDead()) {
                                killCount[0]++;
                                // 凝胶掉落入包（满则掉地）
                                int left = player.inventory().add(ItemRegistry.GEL, 2);
                                if (left > 0) items.add(new ItemEntity("Gel", e.getX(), e.getY(), e.getZ()));
                            }
                        }
                    }
                }
            }

            // 掉落物更新
            Iterator<ItemEntity> iit = items.iterator();
            while (iit.hasNext()) {
                ItemEntity it = iit.next();
                it.update(world, player, dt);
                if (it.isDead()) { iit.remove(); SoundManager.play(SoundManager.Sound.PICKUP); }
            }

            // 生成器
            spawnTimer -= dt;
            if (spawnTimer <= 0 && enemies.size() < MAX_ENEMIES) {
                spawnSlime(world, player, enemies);
                spawnTimer = SPAWN_INTERVAL;
            }

            world.update(player.getX(), player.getZ());

            // 相机
            float camX = lerp(camera.getX(), player.getX(), 0.1f);
            float camY = lerp(camera.getY(), player.getY(), 0.1f);
            camera.setEye(camX, camY, 30.0f);
            camera.apply();

            // 渲染世界
            glColor3f(dayFactor, dayFactor, dayFactor);
            world.render();
            glColor3f(1f, 1f, 1f);

            for (Enemy e : enemies) e.render();
            for (ItemEntity it : items) it.render();
            particles.render();
            player.render();
            player.renderHeldItem();
            drawAttackArc(player);

            // 射线检测（用于破坏/放置）
            float[] wp = camera.screenToWorld(mouseX, mouseY);
            Raycaster.castOrtho(wp[0], wp[1], camera.getZ(), world, hit);

            // 背包打开 → 鼠标交互 UI；否则 → 游戏破坏/放置
            if (inventoryOpen) {
                handleInventoryClicks(player.inventory(), crafting, window);
            } else {
                // 破坏：按住左键累积进度
                if (leftHeld && hit.hit) {
                    byte bid = world.getBlock(hit.bx, hit.by, hit.bz);
                    if (bid != 0) {
                        // 目标切换则重置进度
                        if (hit.bx != breakTargetX || hit.by != breakTargetY || hit.bz != breakTargetZ) {
                            breakTargetX = hit.bx; breakTargetY = hit.by; breakTargetZ = hit.bz;
                            breakProgress = 0f;
                        }
                        float speed = miningSpeed(player.inventory(), bid);
                        breakProgress += dt * speed;
                        if (breakProgress >= 1f) {
                            // 粒子飞溅
                            particles.burstBlock(hit.bx + 0.5f, hit.by + 0.5f, hit.bz + 0.5f, bid, 12);
                            SoundManager.play(SoundManager.Sound.BREAK);
                            // 破坏：掉落物入包，火把/基岩不掉落
                            byte dropped = blockDrop(bid);
                            if (dropped != 0) {
                                int left = player.inventory().add(dropped, 1);
                                if (left > 0) items.add(new ItemEntity(ItemRegistry.name(dropped), hit.bx + 0.5f, hit.by + 0.5f, hit.bz + 0.5f));
                            }
                            // 镐挖矿扣耐久
                            if (player.inventory().heldToolType() == ItemRegistry.ToolType.PICKAXE) {
                                player.inventory().damageHeldTool();
                            }
                            world.setBlock(hit.bx, hit.by, hit.bz, (byte) 0);
                            breakProgress = 0f;
                        }
                    }
                } else {
                    breakProgress = 0f;
                }

                // 放置：右键单次
                if (rightClickEdge) {
                    rightClickEdge = false;
                    if (hit.hit) {
                        ItemStack held = player.inventory().heldItem();
                        if (!held.isEmpty() && isPlaceableBlock(held.id)) {
                            int px = hit.bx + hit.nx, py = hit.by + hit.ny, pz = hit.bz + hit.nz;
                            boolean blocked = player.overlapsBlock(px, py, pz);
                            for (Enemy e : enemies) if (e.overlapsBlock(px, py, pz)) blocked = true;
                            if (!blocked && world.getBlock(px, py, pz) == 0) {
                                world.setBlock(px, py, pz, (byte) held.id);
                                held.count--;
                                SoundManager.play(SoundManager.Sound.PLACE);
                                if (held.count <= 0) held.clear();
                            }
                        }
                    }
                }
                if (leftClickEdge) leftClickEdge = false; // 消费边沿（游戏内左键不做单次动作）
            }

            printTick++;
            if (printTick >= 30) {
                printTick = 0;
                ItemStack held = player.inventory().heldItem();
                String heldName = held.isEmpty() ? "empty" : ItemRegistry.name(held.id);
                System.out.printf("player (%.1f,%.1f) hp=%d ground=%s enemies=%d kills=%d held=%s invOpen=%s%n",
                        player.getX(), player.getY(), player.getHp(), player.isOnGround(),
                        enemies.size(), killCount[0], heldName, inventoryOpen);
            }

            glfwSwapBuffers(window);
            glfwPollEvents();

            // UI 层
            drawHotbar(player.inventory());
            drawHud(player, killCount[0]);
            if (inventoryOpen) drawInventory(player.inventory(), crafting);

            fpsAccum += dt;
            fpsCount++;
            if (fpsAccum >= 1.0f) {
                System.out.println("FPS: " + fpsCount + " loaded=" + world.getLoadedCount());
                fpsAccum = 0; fpsCount = 0;
            }
        }

        world.shutdown();
        SoundManager.shutdown();
        if (font != null) font.dispose();
        if (fontSmall != null) fontSmall.dispose();
        glfwDestroyWindow(window);
        glfwTerminate();
        errorCallback.free();
    }

    // —— 破坏/放置辅助 ——
    private static float miningSpeed(Inventory inv, int blockId) {
        float base = 1f;
        ItemRegistry.ItemDef d = inv.heldDef();
        if (d != null && d.toolType == ItemRegistry.ToolType.PICKAXE) return d.mineSpeed;
        return base;
    }

    private static byte blockDrop(byte blockId) {
        // 基岩不掉落；火把掉火把；其他方块掉自身
        if (blockId == 5) return 0; // bedrock
        return blockId;
    }

    private static boolean isPlaceableBlock(int itemId) {
        return itemId >= 1 && itemId <= 9; // 方块类物品可放置
    }

    // —— 背包 UI 鼠标交互 ——
    private static void handleInventoryClicks(Inventory inv, CraftingGrid craft, long window) {
        // 边沿点击处理
        if (!leftClickEdge && !rightClickEdge) return;
        // 计算槽位几何（与 drawInventory 一致）
        int slotSize = 32, gap = 4;
        // 合成 3×3：居中偏上
        int craftW = 3 * slotSize + 2 * gap;
        int craftStartX = (WIDTH - craftW - slotSize - gap - 40) / 2;
        int craftStartY = 80;
        int outX = craftStartX + craftW + 40, outY = craftStartY + slotSize;
        // 主背包 5×4：20 格
        int mainCols = 5, mainRows = 4;
        int mainW = mainCols * slotSize + (mainCols - 1) * gap;
        int mainStartX = (WIDTH - mainW) / 2;
        int mainStartY = craftStartY + 4 * slotSize + 24;
        // 快捷栏 9 格
        int hotW = 9 * slotSize + 8 * gap;
        int hotStartX = (WIDTH - hotW) / 2;
        int hotStartY = mainStartY + 4 * slotSize + 24;

        int slot = slotAt(mouseX, mouseY, slotSize, gap,
                craftStartX, craftStartY, outX, outY,
                mainStartX, mainStartY, mainCols, mainRows,
                hotStartX, hotStartY);
        if (slot < -2) return; // -2 = output, -1..others 见下方

        // 命中 output 格
        if (slot == -10) {
            if (!leftClickEdge) return;
            leftClickEdge = false;
            if (cursorStack.isEmpty()) {
                ItemStack out = craft.takeOutput();
                if (!out.isEmpty()) { cursorStack = out; SoundManager.play(SoundManager.Sound.CRAFT); }
            }
            return;
        }

        // 命中合成格 0..8
        if (slot >= 0 && slot < 9) {
            boolean left = leftClickEdge; leftClickEdge = false; rightClickEdge = false;
            interactSlot(craft.get(slot), left);
            craft.recheck();
            return;
        }
        // 命中背包格 9..28（offset +9）
        if (slot >= 100 && slot < 129) {
            boolean left = leftClickEdge; leftClickEdge = false; rightClickEdge = false;
            interactSlot(inv.get(slot - 100 + 0), left);
            return;
        }
        leftClickEdge = false; rightClickEdge = false;
    }

    /** 单槽交互：左键拿起/放下整组，右键拿一半。 */
    private static void interactSlot(ItemStack slot, boolean left) {
        if (left) {
            // cursor 与 slot 交换或合并
            if (cursorStack.isEmpty()) {
                if (!slot.isEmpty()) { cursorStack = slot.copy(); slot.clear(); }
            } else if (slot.isEmpty()) {
                slot.id = cursorStack.id; slot.count = cursorStack.count; slot.durability = cursorStack.durability;
                cursorStack.clear();
            } else if (slot.id == cursorStack.id && !ItemRegistry.isTool(slot.id)) {
                int take = Math.min(cursorStack.count, slot.room());
                slot.count += take; cursorStack.count -= take;
                if (cursorStack.count <= 0) cursorStack.clear();
            } else {
                // 不同物：交换
                ItemStack tmp = slot.copy(); slot.id = cursorStack.id; slot.count = cursorStack.count; slot.durability = cursorStack.durability;
                cursorStack = tmp;
            }
        } else {
            // 右键拿一半 / 放一个
            if (cursorStack.isEmpty()) {
                if (!slot.isEmpty()) {
                    int half = slot.count / 2;
                    if (half > 0) { cursorStack = slot.split(half); }
                }
            } else {
                if (slot.isEmpty()) {
                    slot.id = cursorStack.id; slot.durability = cursorStack.durability; slot.count = 1;
                    cursorStack.count--; if (cursorStack.count <= 0) cursorStack.clear();
                } else if (slot.id == cursorStack.id && !ItemRegistry.isTool(slot.id) && slot.room() > 0) {
                    slot.count++; cursorStack.count--; if (cursorStack.count <= 0) cursorStack.clear();
                }
            }
        }
    }

    /** 返回鼠标所在槽位 id：合成格 0..8，背包 100..128，输出格 -10，无 -1。 */
    private static int slotAt(float mx, float my, int size, int gap,
                               int csx, int csy, int ox, int oy,
                               int msx, int msy, int mcols, int mrows,
                               int hsx, int hsy) {
        // 合成 3×3
        for (int i = 0; i < 9; i++) {
            int r = i / 3, c = i % 3;
            int x = csx + c * (size + gap), y = csy + r * (size + gap);
            if (mx >= x && mx < x + size && my >= y && my < y + size) return i;
        }
        // 输出格
        if (mx >= ox && mx < ox + size && my >= oy && my < oy + size) return -10;
        // 主背包
        for (int i = 0; i < mcols * mrows; i++) {
            int r = i / mcols, c = i % mcols;
            int x = msx + c * (size + gap), y = msy + r * (size + gap);
            if (mx >= x && mx < x + size && my >= y && my < y + size) return 100 + i;
        }
        return -1;
    }

    // —— UI 绘制 ——
    private static void drawHotbar(Inventory inv) {
        glMatrixMode(GL_PROJECTION);
        glPushMatrix();
        glLoadIdentity();
        glOrtho(0, WIDTH, 0, HEIGHT, -1, 1);
        glMatrixMode(GL_MODELVIEW);
        glPushMatrix();
        glLoadIdentity();
        glDisable(GL_DEPTH_TEST);
        int slots = Inventory.HOTBAR_SIZE;
        float slotSize = 36f, gap = 4f;
        float totalW = slots * slotSize + (slots - 1) * gap;
        float startX = (WIDTH - totalW) * 0.5f;
        float y = 12f;
        for (int i = 0; i < slots; i++) {
            float x = startX + i * (slotSize + gap);
            ItemStack s = inv.get(i);
            boolean sel = (i == inv.selected());
            // 背景
            float br = sel ? 1f : 0.3f, bg = sel ? 1f : 0.3f, bb = sel ? 1f : 0.3f;
            if (!s.isEmpty()) {
                ItemRegistry.ItemDef d = ItemRegistry.get(s.id);
                glColor3f(d.r, d.g, d.b);
                glBegin(GL_QUADS);
                glVertex2f(x + 5, y + 5); glVertex2f(x + slotSize - 5, y + 5);
                glVertex2f(x + slotSize - 5, y + slotSize - 5); glVertex2f(x + 5, y + slotSize - 5);
                glEnd();
                // 工具用更亮边框
                if (ItemRegistry.isTool(s.id)) { br = 1f; bg = 0.85f; bb = 0.3f; }
            }
            glColor3f(br, bg, bb);
            glBegin(GL_LINE_LOOP);
            glVertex2f(x, y); glVertex2f(x + slotSize, y);
            glVertex2f(x + slotSize, y + slotSize); glVertex2f(x, y + slotSize);
            glEnd();
            // 数量（用小方块点表示，>1 时画 count 个点）
            if (!s.isEmpty() && s.count > 1) {
                glColor3f(1f, 1f, 1f);
                int dots = Math.min(s.count, 8);
                for (int k = 0; k < dots; k++) {
                    float dx = x + 4 + k * 3f, dy = y + slotSize - 6;
                    glBegin(GL_QUADS);
                    glVertex2f(dx, dy); glVertex2f(dx + 2, dy);
                    glVertex2f(dx + 2, dy + 2); glVertex2f(dx, dy + 2);
                    glEnd();
                }
            }
        }
        glEnable(GL_DEPTH_TEST);
        glPopMatrix();
        glMatrixMode(GL_PROJECTION);
        glPopMatrix();
        glMatrixMode(GL_MODELVIEW);
    }

    private static void drawInventory(Inventory inv, CraftingGrid craft) {
        glMatrixMode(GL_PROJECTION);
        glPushMatrix();
        glLoadIdentity();
        glOrtho(0, WIDTH, 0, HEIGHT, -1, 1);
        glMatrixMode(GL_MODELVIEW);
        glPushMatrix();
        glLoadIdentity();
        glDisable(GL_DEPTH_TEST);

        // 半透明背景
        glColor4f(0.1f, 0.1f, 0.15f, 0.8f);
        glBegin(GL_QUADS);
        glVertex2f(0, 0); glVertex2f(WIDTH, 0);
        glVertex2f(WIDTH, HEIGHT); glVertex2f(0, HEIGHT);
        glEnd();

        int slotSize = 32, gap = 4;
        int craftW = 3 * slotSize + 2 * gap;
        int csx = (WIDTH - craftW - slotSize - gap - 40) / 2;
        int csy = 80;
        int ox = csx + craftW + 40, oy = csy + slotSize;
        // 合成格
        for (int i = 0; i < 9; i++) {
            int r = i / 3, c = i % 3;
            int x = csx + c * (slotSize + gap), y = csy + r * (slotSize + gap);
            drawSlot(x, y, slotSize, craft.get(i));
        }
        // 输出格
        drawSlot(ox, oy, slotSize, craft.output());

        // 主背包 5×4
        int mcols = 5, mrows = 4;
        int mainW = mcols * slotSize + (mcols - 1) * gap;
        int msx = (WIDTH - mainW) / 2;
        int msy = csy + 4 * slotSize + 24;
        for (int i = 0; i < mcols * mrows; i++) {
            int r = i / mcols, c = i % mcols;
            int x = msx + c * (slotSize + gap), y = msy + r * (slotSize + gap);
            drawSlot(x, y, slotSize, inv.get(Inventory.HOTBAR_SIZE + i));
        }

        // 快捷栏
        int hotW = 9 * slotSize + 8 * gap;
        int hsx = (WIDTH - hotW) / 2;
        int hsy = msy + 4 * slotSize + 24;
        for (int i = 0; i < Inventory.HOTBAR_SIZE; i++) {
            int x = hsx + i * (slotSize + gap), y = hsy;
            boolean sel = (i == inv.selected());
            drawSlot(x, y, slotSize, inv.get(i));
            if (sel) {
                glColor3f(1f, 1f, 0f);
                glBegin(GL_LINE_LOOP);
                glVertex2f(x - 1, y - 1); glVertex2f(x + slotSize + 1, y - 1);
                glVertex2f(x + slotSize + 1, y + slotSize + 1); glVertex2f(x - 1, y + slotSize + 1);
                glEnd();
            }
        }

        // cursor 跟随
        if (!cursorStack.isEmpty()) {
            drawSlot((int) mouseX - slotSize / 2, (int) (HEIGHT - mouseY) - slotSize / 2, slotSize, cursorStack);
        }

        glEnable(GL_DEPTH_TEST);
        glPopMatrix();
        glMatrixMode(GL_PROJECTION);
        glPopMatrix();
        glMatrixMode(GL_MODELVIEW);
    }

    private static void drawSlot(int x, int y, int size, ItemStack s) {
        // 空槽框
        glColor3f(0.4f, 0.4f, 0.45f);
        glBegin(GL_QUADS);
        glVertex2f(x, y); glVertex2f(x + size, y);
        glVertex2f(x + size, y + size); glVertex2f(x, y + size);
        glEnd();
        glColor3f(0.7f, 0.7f, 0.75f);
        glBegin(GL_LINE_LOOP);
        glVertex2f(x, y); glVertex2f(x + size, y);
        glVertex2f(x + size, y + size); glVertex2f(x, y + size);
        glEnd();
        if (s.isEmpty()) return;
        ItemRegistry.ItemDef d = ItemRegistry.get(s.id);
        // 物品色块
        glColor3f(d.r, d.g, d.b);
        glBegin(GL_QUADS);
        glVertex2f(x + 4, y + 4); glVertex2f(x + size - 4, y + 4);
        glVertex2f(x + size - 4, y + size - 4); glVertex2f(x + 4, y + size - 4);
        glEnd();
        // 工具用斜线标识
        if (ItemRegistry.isTool(s.id)) {
            glColor3f(0.1f, 0.1f, 0.1f);
            glBegin(GL_LINES);
            glVertex2f(x + 6, y + size - 6); glVertex2f(x + size - 6, y + 6);
            glEnd();
            // 耐久条
            float frac = (float) s.durability / d.maxDurability;
            glColor3f(0.2f, 0.8f, 0.2f);
            glBegin(GL_QUADS);
            glVertex2f(x + 2, y + size - 3); glVertex2f(x + 2 + (size - 4) * frac, y + size - 3);
            glVertex2f(x + 2 + (size - 4) * frac, y + size - 1); glVertex2f(x + 2, y + size - 1);
            glEnd();
        }
        // 数量点
        if (s.count > 1) {
            glColor3f(1f, 1f, 1f);
            int dots = Math.min(s.count, 10);
            for (int k = 0; k < dots; k++) {
                float dx = x + 4 + k * 2.5f, dy = y + 2;
                glBegin(GL_QUADS);
                glVertex2f(dx, dy); glVertex2f(dx + 1.5f, dy);
                glVertex2f(dx + 1.5f, dy + 1.5f); glVertex2f(dx, dy + 1.5f);
                glEnd();
            }
        }
    }

    private static Player spawnPlayer(BlockAccess world, int wx, int wz) {
        int groundY = -1;
        for (int y = 14; y >= 0; y--) {
            if (world.getBlock(wx, y, wz) != 0) { groundY = y; break; }
        }
        Player p = new Player(wx + 0.5f, 20f, wz + 0.5f);
        if (groundY >= 0) {
            p.setPos(wx + 0.5f, groundY + 1 + Player.HEIGHT / 2f, wz + 0.5f);
        }
        return p;
    }

    private static void spawnSlime(BlockAccess world, Player player, List<Enemy> enemies) {
        long seed = System.nanoTime();
        for (int attempt = 0; attempt < 12; attempt++) {
            seed = (seed * 6364136223846793005L + 1442695040888963407L);
            int r = (int) ((seed >>> 32) & 0xFFFF);
            int dist = 5 + ((r >>> 8) % 8);
            float dir = ((r >>> 15) & 1) == 0 ? 1f : -1f;
            float cx = player.getX() + dir * dist;
            int bz = (int) Math.floor(player.getZ());
            int bx = (int) Math.floor(cx);
            int groundY = -1;
            for (int y = 14; y >= 0; y--) {
                if (world.getBlock(bx, y, bz) != 0) { groundY = y; break; }
            }
            if (groundY < 0) continue;
            float spawnY = groundY + 1 + Slime.H / 2f;
            boolean overlap = false;
            for (Enemy e : enemies) {
                float dx = e.getX() - cx, dy = e.getY() - spawnY, dz = e.getZ() - (bz + 0.5f);
                if (dx*dx + dy*dy + dz*dz < 2.0f) { overlap = true; break; }
            }
            if (overlap) continue;
            enemies.add(new Slime(cx, spawnY, bz + 0.5f));
            System.out.println("Spawned slime at (" + cx + "," + spawnY + "," + (bz + 0.5f) + ")");
            return;
        }
    }

    private static void drawAttackArc(Player player) {
        float anim = player.getAttackAnim();
        if (anim <= 0) return;
        float cx = player.getX();
        float cy = player.getY();
        float cz = player.getZ() + player.depth() * 0.5f + 0.02f;
        float range = player.getAttackRange();
        float facing = player.getFacingX();
        float arc = player.getAttackArc();
        float prog = 1f - (anim / 0.1f);
        float startA = facing > 0 ? -arc * 0.5f : (float) (Math.PI - arc * 0.5f);
        float swept = arc * prog;
        glColor3f(1f, 1f, 1f);
        glBegin(GL_LINE_STRIP);
        int segs = 12;
        for (int i = 0; i <= segs; i++) {
            float t = (float) i / segs;
            float a = startA + swept * t;
            glVertex3f(cx + (float) Math.cos(a) * range, cy + (float) Math.sin(a) * range, cz);
        }
        glEnd();
    }

    private static float computeDayFactor(float t) {
        if (t < 5000) return 0.15f;
        if (t < 7000) return lerp(0.15f, 1.0f, (t - 5000) / 2000f);
        if (t < 17000) return 1.0f;
        if (t < 19000) return lerp(1.0f, 0.15f, (t - 17000) / 2000f);
        return 0.15f;
    }

    private static float[] computeSkyColor(float t) {
        float[] night = {0.02f, 0.02f, 0.08f};
        float[] dawn  = {0.9f, 0.5f, 0.2f};
        float[] day  = {0.4f, 0.6f, 0.9f};
        float[] dusk = {0.9f, 0.45f, 0.2f};
        if (t < 5000) return night;
        if (t < 6500) return lerp3(night, dawn, (t - 5000) / 1500f);
        if (t < 8000) return lerp3(dawn, day, (t - 6500) / 1500f);
        if (t < 16000) return day;
        if (t < 18000) return lerp3(day, dusk, (t - 16000) / 2000f);
        if (t < 19500) return lerp3(dusk, night, (t - 18000) / 1500f);
        return night;
    }

    private static float[] lerp3(float[] a, float[] b, float t) {
        return new float[] { lerp(a[0], b[0], t), lerp(a[1], b[1], t), lerp(a[2], b[2], t) };
    }

    private static void drawHud(Player player, int kills) {
        glMatrixMode(GL_PROJECTION);
        glPushMatrix();
        glLoadIdentity();
        glOrtho(0, WIDTH, 0, HEIGHT, -1, 1);
        glMatrixMode(GL_MODELVIEW);
        glPushMatrix();
        glLoadIdentity();
        glDisable(GL_DEPTH_TEST);
        float barW = 100f, barH = 10f, bx = 12f, by = HEIGHT - 22f;
        glColor3f(0.2f, 0.2f, 0.2f);
        glBegin(GL_QUADS);
        glVertex2f(bx, by); glVertex2f(bx + barW, by);
        glVertex2f(bx + barW, by + barH); glVertex2f(bx, by + barH);
        glEnd();
        float hpFrac = Math.max(0f, (float) player.getHp() / Player.MAX_HP);
        glColor3f(0.8f, 0.1f, 0.1f);
        glBegin(GL_QUADS);
        glVertex2f(bx, by); glVertex2f(bx + barW * hpFrac, by);
        glVertex2f(bx + barW * hpFrac, by + barH); glVertex2f(bx, by + barH);
        glEnd();
        glColor3f(1f, 1f, 1f);
        glBegin(GL_LINE_LOOP);
        glVertex2f(bx, by); glVertex2f(bx + barW, by);
        glVertex2f(bx + barW, by + barH); glVertex2f(bx, by + barH);
        glEnd();
        // 击杀数
        float ky = HEIGHT - 22f;
        glColor3f(0.2f, 0.2f, 0.2f);
        glBegin(GL_QUADS);
        glVertex2f(WIDTH - 12f - 48f, ky); glVertex2f(WIDTH - 12f, ky);
        glVertex2f(WIDTH - 12f, ky + 18f); glVertex2f(WIDTH - 12f - 48f, ky + 18f);
        glEnd();
        glColor3f(1f, 1f, 0f);
        int cols = 8;
        for (int i = 0; i < kills && i < 40; i++) {
            int r = i / cols, c = i % cols;
            float px = WIDTH - 44f + c * 5f;
            float py = ky + 14f - r * 5f;
            glBegin(GL_QUADS);
            glVertex2f(px, py); glVertex2f(px + 3f, py);
            glVertex2f(px + 3f, py + 3f); glVertex2f(px, py + 3f);
            glEnd();
        }
        glColor3f(1f, 1f, 1f);
        glBegin(GL_LINE_LOOP);
        glVertex2f(WIDTH - 12f - 48f, ky); glVertex2f(WIDTH - 12f, ky);
        glVertex2f(WIDTH - 12f, ky + 18f); glVertex2f(WIDTH - 12f - 48f, ky + 18f);
        glEnd();
        glEnable(GL_DEPTH_TEST);
        glPopMatrix();
        glMatrixMode(GL_PROJECTION);
        glPopMatrix();
        glMatrixMode(GL_MODELVIEW);
    }

    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }

    // —— 菜单 ——
    private static void drawMenu(boolean mainMenu) {
        glMatrixMode(GL_PROJECTION);
        glPushMatrix();
        glLoadIdentity();
        glOrtho(0, WIDTH, 0, HEIGHT, -1, 1);
        glMatrixMode(GL_MODELVIEW);
        glPushMatrix();
        glLoadIdentity();
        glDisable(GL_DEPTH_TEST);
        // 背景：深色渐变感（简化为单色）
        glColor3f(0.12f, 0.13f, 0.18f);
        glBegin(GL_QUADS);
        glVertex2f(0, 0); glVertex2f(WIDTH, 0);
        glVertex2f(WIDTH, HEIGHT); glVertex2f(0, HEIGHT);
        glEnd();
        // 标题底板
        float ty = HEIGHT - 140;
        glColor3f(0.2f, 0.18f, 0.1f);
        glBegin(GL_QUADS);
        glVertex2f(WIDTH/2f - 220, ty); glVertex2f(WIDTH/2f + 220, ty);
        glVertex2f(WIDTH/2f + 220, ty + 60); glVertex2f(WIDTH/2f - 220, ty + 60);
        glEnd();
        glColor3f(0.9f, 0.75f, 0.2f);
        glBegin(GL_LINE_LOOP);
        glVertex2f(WIDTH/2f - 220, ty); glVertex2f(WIDTH/2f + 220, ty);
        glVertex2f(WIDTH/2f + 220, ty + 60); glVertex2f(WIDTH/2f - 220, ty + 60);
        glEnd();
        // 标题文字（中文）
        font.drawTextCenter("体素泰拉瑞亚", WIDTH / 2, (int) (ty + 16), 0.95f, 0.8f, 0.25f);

        // 按钮
        float[] b1 = mainMenu ? BTN_START : BTN_RESUME;
        float[] b2 = mainMenu ? BTN_QUIT : BTN_SAVEQUIT;
        boolean h1 = inBtn(b1), h2 = inBtn(b2);
        drawButton(b1, h1, mainMenu ? "开始游戏" : "继续游戏");
        drawButton(b2, h2, mainMenu ? "退出游戏" : "保存并退出");

        // 副标题/提示
        String hint = mainMenu
                ? (hasSave ? "检测到存档，点击开始继续上次进度" : "无存档，将开始新世界")
                : "ESC 继续  E 打开背包  J 攻击  数字键 1-9 切换物品";
        fontSmall.drawTextCenter(hint, WIDTH / 2, 48, 0.6f, 0.6f, 0.65f);

        glEnable(GL_DEPTH_TEST);
        glPopMatrix();
        glMatrixMode(GL_PROJECTION);
        glPopMatrix();
        glMatrixMode(GL_MODELVIEW);
    }

    private static boolean inBtn(float[] b) {
        return mouseX >= b[0] && mouseX < b[0] + b[2] && (HEIGHT - mouseY) >= b[1] && (HEIGHT - mouseY) < b[1] + b[3];
    }

    private static void drawButton(float[] b, boolean hover, String label) {
        // 按钮底
        float k = hover ? 0.42f : 0.28f;
        glColor3f(k, k, k + 0.06f);
        glBegin(GL_QUADS);
        glVertex2f(b[0], b[1]); glVertex2f(b[0] + b[2], b[1]);
        glVertex2f(b[0] + b[2], b[1] + b[3]); glVertex2f(b[0], b[1] + b[3]);
        glEnd();
        // 边框（悬停高亮）
        float er = hover ? 1f : 0.6f, eg = hover ? 0.9f : 0.6f, eb = hover ? 0.3f : 0.6f;
        glColor3f(er, eg, eb);
        glBegin(GL_LINE_LOOP);
        glVertex2f(b[0], b[1]); glVertex2f(b[0] + b[2], b[1]);
        glVertex2f(b[0] + b[2], b[1] + b[3]); glVertex2f(b[0], b[1] + b[3]);
        glEnd();
        // 中文文字（白色，悬停略亮）
        float tr = hover ? 1f : 0.85f, tg = hover ? 1f : 0.85f, tb = hover ? 0.9f : 0.8f;
        font.drawTextCenter(label, (int) (b[0] + b[2] / 2), (int) (b[1] + b[3] / 2), tr, tg, tb);
    }

    private static void handleMenuClick(boolean mainMenu, Player player, SaveManager.SaveData saveData) {
        float[] b1 = mainMenu ? BTN_START : BTN_RESUME;
        float[] b2 = mainMenu ? BTN_QUIT : BTN_SAVEQUIT;
        if (inBtn(b1)) {
            SoundManager.play(SoundManager.Sound.CLICK);
            if (mainMenu) {
                // 开始游戏：若有存档恢复玩家数据
                if (saveData.hasSave) {
                    player.setPos(saveData.player.x, saveData.player.y, saveData.player.z);
                    player.setHp(saveData.player.hp);
                    SaveManager.applyInventory(saveData, player.inventory());
                }
            }
            gstate = GState.PLAYING;
        } else if (inBtn(b2)) {
            SoundManager.play(SoundManager.Sound.CLICK);
            if (!mainMenu) saveAndQuit(player);
            else glfwSetWindowShouldClose(glfwGetCurrentContext(), true);
        }
    }

    /** 保存并退出。 */
    private static void saveAndQuit(Player player) {
        SaveManager.SaveData d = new SaveManager.SaveData();
        d.player.x = player.getX(); d.player.y = player.getY(); d.player.z = player.getZ();
        d.player.hp = player.getHp();
        SaveManager.collectInventory(player.inventory(), d);
        d.blocks = worldRef.exportEdits();
        SaveManager.save(d);
        System.out.println("Game saved to " + SaveManager.SAVE_FILE);
    }
}
