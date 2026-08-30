package tailai;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 联机服务器（主机侧传输层）：监听端口、接收客户端连接、分配槽位、回 WELCOME、
 * 维护玩家列表、提供入站消息队列与广播能力。
 * 纯传输层，不依赖 GamePanel；游戏逻辑由主机 GamePanel 每帧 poll 处理。
 */
public class GameServer {

    /** 一条入站消息（来自某个已加入的客户端）。 */
    public static class InMsg {
        public final int slot;
        public final byte type;
        public final byte[] payload;

        public InMsg(int slot, byte type, byte[] payload) {
            this.slot = slot;
            this.type = type;
            this.payload = payload;
        }
    }

    private static final int MAX_SLOTS = 5;

    private static class Conn {
        final Socket sock;
        final int slot;
        final String name;
        final DataInputStream in;
        final DataOutputStream out;

        Conn(Socket sock, int slot, String name, DataInputStream in, DataOutputStream out) {
            this.sock = sock;
            this.slot = slot;
            this.name = name;
            this.in = in;
            this.out = out;
        }
    }

    private final Queue<InMsg> inbox = new ConcurrentLinkedQueue<>();
    private final List<Conn> conns = new CopyOnWriteArrayList<>();
    private final Map<Integer, String> names = new ConcurrentHashMap<>();
    private ServerSocket ss;
    private volatile boolean running;
    private final long worldSeed;
    private int nextSlot = 1;

    public GameServer(long worldSeed) {
        this.worldSeed = worldSeed;
        names.put(0, "主机");
    }

    public void start(int port) throws IOException {
        ss = new ServerSocket(port);
        running = true;
        Thread t = new Thread(this::acceptLoop, "server-accept");
        t.setDaemon(true);
        t.start();
    }

    private void acceptLoop() {
        while (running) {
            try {
                Socket s = ss.accept();
                handleNew(s);
            } catch (IOException e) {
                break;
            }
        }
    }

    private void handleNew(Socket sock) {
        Thread t = new Thread(() -> {
            try {
                sock.setTcpNoDelay(true);
                DataInputStream in = new DataInputStream(new BufferedInputStream(sock.getInputStream()));
                DataOutputStream out = new DataOutputStream(new BufferedOutputStream(sock.getOutputStream()));

                Object[] m = NetMessages.readMsg(in);
                if ((Byte) m[0] != NetMessages.JOIN) {
                    sock.close();
                    return;
                }
                String name = new String((byte[]) m[1], StandardCharsets.UTF_8);
                if (name.isEmpty()) {
                    name = "玩家";
                }
                int slot;
                synchronized (this) {
                    if (conns.size() >= MAX_SLOTS) {
                        sock.close();
                        return;
                    }
                    slot = nextSlot++;
                }
                // 回复 WELCOME（种子 + 槽位）
                NetMessages.writeMsg(out, NetMessages.WELCOME, NetMessages.encodeWelcome(worldSeed, slot));

                Conn conn = new Conn(sock, slot, name, in, out);
                conns.add(conn);
                names.put(slot, name);
                broadcastList();
                readLoop(conn);
            } catch (IOException e) {
                // 连接异常断开
            }
        }, "server-conn");
        t.setDaemon(true);
        t.start();
    }

    private void readLoop(Conn c) {
        try {
            while (running) {
                Object[] m = NetMessages.readMsg(c.in);
                byte type = (Byte) m[0];
                byte[] payload = (byte[]) m[1];
                if (type == NetMessages.LEAVE) {
                    break;
                }
                inbox.add(new InMsg(c.slot, type, payload));
            }
        } catch (IOException ignored) {
        } finally {
            disconnect(c);
        }
    }

    private void disconnect(Conn c) {
        if (conns.remove(c)) {
            names.remove(c.slot);
            inbox.add(new InMsg(c.slot, NetMessages.LEAVE, new byte[0]));
            broadcastList();
            try {
                c.sock.close();
            } catch (IOException ignored) {
            }
        }
    }

    private void broadcastList() {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        try (DataOutputStream d = new DataOutputStream(bo)) {
            d.writeInt(names.size());
            for (Map.Entry<Integer, String> e : names.entrySet()) {
                d.writeInt(e.getKey());
                d.writeUTF(e.getValue());
            }
        } catch (IOException ignored) {
        }
        broadcast(NetMessages.LIST, bo.toByteArray());
    }

    /** 广播给所有已连接客户端。 */
    public void broadcast(byte type, byte[] payload) {
        for (Conn c : conns) {
            try {
                NetMessages.writeMsg(c.out, type, payload);
            } catch (IOException e) {
                disconnect(c);
            }
        }
    }

    /** 发送给指定槽位客户端。 */
    public void sendTo(int slot, byte type, byte[] payload) {
        for (Conn c : conns) {
            if (c.slot == slot) {
                try {
                    NetMessages.writeMsg(c.out, type, payload);
                } catch (IOException e) {
                    disconnect(c);
                }
                return;
            }
        }
    }

    public Queue<InMsg> inbox() {
        return inbox;
    }

    /** 槽位 -> 玩家名（含 0=主机）。 */
    public Map<Integer, String> names() {
        return names;
    }

    public boolean hasClients() {
        return !conns.isEmpty();
    }

    public int clientCount() {
        return conns.size();
    }

    public void stop() {
        running = false;
        try {
            if (ss != null) {
                ss.close();
            }
        } catch (IOException ignored) {
        }
        for (Conn c : conns) {
            try {
                c.sock.close();
            } catch (IOException ignored) {
            }
        }
        conns.clear();
    }
}
