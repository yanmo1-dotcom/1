package tailai;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * 联机消息协议：定长头 + 消息帧。
 * 帧格式：[int 总长度][byte 类型][byte... payload]；payload 用 DataOutput 编解码。
 *
 * 消息类型：
 *  JOIN          C→H  玩家名(UTF-8)                            —— 服务器在处理 JOIN 时直接回复 WELCOME
 *  WELCOME       H→C  [long seed][int slotId]                  —— 客户端据此生成同种子世界
 *  PSTATE        C→H→C [int slot][float x][float y][float vx][float vy][int hp][int facing][int weaponId]
 *  BLOCK         any→all [int gx][int gy][byte tileId]
 *  ENEMY_SYNC    H→C  [int count][ 每项: int id][float x][float y][int hp][byte type ][byte alive] ]
 *  ATTACK        C→H  [int enemyId][int dmg][float dir]        —— 客户端攻击目标敌人
 *  DAMAGE        H→C  [int dmg][float kx][float ky]            —— 主机通知该客户端其玩家受伤
 *  HEAL          H→C  [int amount]
 *  LOOT          H→C  [int itemId][int count]                  —— 击杀奖励入击杀者背包
 *  LEAVE         any→all
 *  LIST          H→C  [int n][ 每项: int slot][String name]    —— 玩家列表
 *  CHAT          any→H→C [int slot][String name][String text] —— 聊天消息
 */
public final class NetMessages {

    public static final byte JOIN = 1;
    public static final byte WELCOME = 2;
    public static final byte PSTATE = 3;
    public static final byte BLOCK = 4;
    public static final byte ENEMY_SYNC = 5;
    public static final byte ATTACK = 6;
    public static final byte DAMAGE = 7;
    public static final byte HEAL = 8;
    public static final byte LOOT = 9;
    public static final byte LEAVE = 10;
    public static final byte LIST = 11;
    public static final byte CHAT = 12;

    private NetMessages() {
    }

    /** 写出一个消息帧。 */
    public static void writeMsg(DataOutputStream out, byte type, byte[] payload) throws IOException {
        out.writeInt(1 + payload.length);
        out.writeByte(type);
        out.write(payload);
        out.flush();
    }

    /** 阻塞读取一个消息帧；返回 [Byte type, byte[] payload]。 */
    public static Object[] readMsg(DataInputStream in) throws IOException {
        int len = in.readInt();
        if (len < 1 || len > 64 * 1024) {
            throw new IOException("非法消息长度: " + len);
        }
        byte type = in.readByte();
        byte[] payload = new byte[len - 1];
        in.readFully(payload);
        return new Object[]{type, payload};
    }

    // ---- 便捷编解码 ----

    public static byte[] encodeJoin(String name) {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        try (DataOutputStream d = new DataOutputStream(bo)) {
            d.writeUTF(name);
        } catch (IOException ignored) {
        }
        return bo.toByteArray();
    }

    public static byte[] encodeWelcome(long seed, int slot) {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        try (DataOutputStream d = new DataOutputStream(bo)) {
            d.writeLong(seed);
            d.writeInt(slot);
        } catch (IOException ignored) {
        }
        return bo.toByteArray();
    }

    public static byte[] encodePState(int slot, float x, float y, float vx, float vy,
                                      int hp, int facing, int weaponId) {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        try (DataOutputStream d = new DataOutputStream(bo)) {
            d.writeInt(slot);
            d.writeFloat(x);
            d.writeFloat(y);
            d.writeFloat(vx);
            d.writeFloat(vy);
            d.writeInt(hp);
            d.writeInt(facing);
            d.writeInt(weaponId);
        } catch (IOException ignored) {
        }
        return bo.toByteArray();
    }

    public static byte[] encodeBlock(int gx, int gy, byte tileId) {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        try (DataOutputStream d = new DataOutputStream(bo)) {
            d.writeInt(gx);
            d.writeInt(gy);
            d.writeByte(tileId);
        } catch (IOException ignored) {
        }
        return bo.toByteArray();
    }

    public static byte[] encodeAttack(int enemyId, int dmg, float dir) {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        try (DataOutputStream d = new DataOutputStream(bo)) {
            d.writeInt(enemyId);
            d.writeInt(dmg);
            d.writeFloat(dir);
        } catch (IOException ignored) {
        }
        return bo.toByteArray();
    }

    public static byte[] encodeDamage(int dmg, float kx, float ky) {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        try (DataOutputStream d = new DataOutputStream(bo)) {
            d.writeInt(dmg);
            d.writeFloat(kx);
            d.writeFloat(ky);
        } catch (IOException ignored) {
        }
        return bo.toByteArray();
    }

    public static byte[] encodeChat(int slot, String name, String text) {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        try (DataOutputStream d = new DataOutputStream(bo)) {
            d.writeInt(slot);
            d.writeUTF(name);
            d.writeUTF(text);
        } catch (IOException ignored) {
        }
        return bo.toByteArray();
    }
}
