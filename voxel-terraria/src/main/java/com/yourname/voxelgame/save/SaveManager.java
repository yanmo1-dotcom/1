package com.yourname.voxelgame.save;

import com.yourname.voxelgame.inventory.Inventory;
import com.yourname.voxelgame.inventory.ItemStack;
import com.yourname.voxelgame.inventory.ItemRegistry;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 存档系统：把玩家位置/血量/背包 + 方块修改 diff 序列化到文本文件。
 * 格式（行式，便于调试）：
 *   #PLAYER x y z hp
 *   #INV id count durability   (每行一个非空槽)
 *   #BLOCKS wx wy wz id         (每行一个被修改的方块)
 */
public final class SaveManager {

    public static final String SAVE_FILE = "voxel_save.txt";

    public static class PlayerData {
        public float x, y, z;
        public int hp;
    }

    public static class BlockEdit {
        public int x, y, z;
        public byte id;
        public BlockEdit(int x, int y, int z, byte id) { this.x=x; this.y=y; this.z=z; this.id=id; }
    }

    public static class SaveData {
        public PlayerData player = new PlayerData();
        public List<BlockEdit> blocks = new ArrayList<>();
        // 背包槽：index → (id,count,durability)
        public List<int[]> invSlots = new ArrayList<>();
        public boolean hasSave = false;
    }

    /** 读取存档。无文件返回空 hasSave=false。 */
    public static SaveData load() {
        SaveData d = new SaveData();
        Path p = Path.of(SAVE_FILE);
        if (!Files.exists(p)) return d;
        try {
            List<String> lines = Files.readAllLines(p);
            int mode = 0; // 0=none 1=inv 2=blocks
            for (String line : lines) {
                line = line.trim();
                if (line.isEmpty()) continue;
                String[] t = line.split("\\s+");
                if (t[0].equals("#PLAYER")) {
                    d.player.x = Float.parseFloat(t[1]);
                    d.player.y = Float.parseFloat(t[2]);
                    d.player.z = Float.parseFloat(t[3]);
                    d.player.hp = Integer.parseInt(t[4]);
                    d.hasSave = true;
                    mode = 0;
                } else if (t[0].equals("#INV")) {
                    mode = 1;
                } else if (t[0].equals("#BLOCKS")) {
                    mode = 2;
                } else if (mode == 1) {
                    int idx = Integer.parseInt(t[0]);
                    int id = Integer.parseInt(t[1]);
                    int count = Integer.parseInt(t[2]);
                    int dur = Integer.parseInt(t[3]);
                    d.invSlots.add(new int[]{idx, id, count, dur});
                } else if (mode == 2) {
                    int x = Integer.parseInt(t[0]);
                    int y = Integer.parseInt(t[1]);
                    int z = Integer.parseInt(t[2]);
                    byte id = Byte.parseByte(t[3]);
                    d.blocks.add(new BlockEdit(x, y, z, id));
                }
            }
        } catch (IOException e) {
            System.err.println("Load save failed: " + e.getMessage());
        }
        return d;
    }

    /** 保存。 */
    public static void save(SaveData d) {
        try (PrintWriter w = new PrintWriter(SAVE_FILE)) {
            w.printf("#PLAYER %f %f %f %d%n", d.player.x, d.player.y, d.player.z, d.player.hp);
            w.println("#INV");
            for (int[] s : d.invSlots) {
                w.printf("%d %d %d %d%n", s[0], s[1], s[2], s[3]);
            }
            w.println("#BLOCKS");
            for (BlockEdit e : d.blocks) {
                w.printf("%d %d %d %d%n", e.x, e.y, e.z, e.id);
            }
        } catch (IOException e) {
            System.err.println("Save failed: " + e.getMessage());
        }
    }

    /** 从存档填充背包。 */
    public static void applyInventory(SaveData d, Inventory inv) {
        for (int[] s : d.invSlots) {
            if (s[0] >= 0 && s[0] < Inventory.TOTAL) {
                ItemStack slot = inv.get(s[0]);
                slot.id = s[1]; slot.count = s[2]; slot.durability = s[3];
            }
        }
    }

    /** 收集背包到存档。 */
    public static void collectInventory(Inventory inv, SaveData d) {
        for (int i = 0; i < Inventory.TOTAL; i++) {
            ItemStack s = inv.get(i);
            if (!s.isEmpty()) {
                d.invSlots.add(new int[]{i, s.id, s.count, s.durability});
            }
        }
    }
}
