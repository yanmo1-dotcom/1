package tailai;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * 联机客户端（传输层）：连接主机、发 JOIN、接收入站消息入队、提供发送方法。
 * 纯传输层，不依赖 GamePanel；游戏逻辑由客户端 GamePanel 每帧 poll 处理。
 */
public class GameClient {

    /** 一条入站消息。 */
    public static class InMsg {
        public final byte type;
        public final byte[] payload;

        public InMsg(byte type, byte[] payload) {
            this.type = type;
            this.payload = payload;
        }
    }

    private final Queue<InMsg> inbox = new ConcurrentLinkedQueue<>();
    private Socket sock;
    private DataInputStream in;
    private DataOutputStream out;
    private volatile boolean running;
    private volatile boolean connected;

    public void connect(String host, int port, String name) throws IOException {
        sock = new Socket();
        sock.connect(new InetSocketAddress(host, port), 4000);
        sock.setTcpNoDelay(true);
        out = new DataOutputStream(new BufferedOutputStream(sock.getOutputStream()));
        in = new DataInputStream(new BufferedInputStream(sock.getInputStream()));
        running = true;
        connected = true;
        send(NetMessages.JOIN, name.getBytes(StandardCharsets.UTF_8));
        Thread t = new Thread(this::readLoop, "client-read");
        t.setDaemon(true);
        t.start();
    }

    private void readLoop() {
        try {
            while (running) {
                Object[] m = NetMessages.readMsg(in);
                inbox.add(new InMsg((Byte) m[0], (byte[]) m[1]));
            }
        } catch (IOException ignored) {
        } finally {
            connected = false;
        }
    }

    public void send(byte type, byte[] payload) {
        if (!connected) {
            return;
        }
        try {
            synchronized (out) {
                NetMessages.writeMsg(out, type, payload);
            }
        } catch (IOException e) {
            connected = false;
        }
    }

    public Queue<InMsg> inbox() {
        return inbox;
    }

    public boolean isConnected() {
        return connected;
    }

    public void close() {
        running = false;
        try {
            if (sock != null) {
                sock.close();
            }
        } catch (IOException ignored) {
        }
    }
}
