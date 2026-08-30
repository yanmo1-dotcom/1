package tailai;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 局域网房间发现：主机通过 UDP 广播公告自身（主机名 + 端口），
 * 客户端广播查询并收集回复，得到可加入的房间列表。
 * 无第三方依赖，纯 JDK。
 */
public final class LanDiscovery {

    public static final int DISCOVERY_PORT = 24777;
    private static final String MAGIC = "TALR";
    private static final String QUERY = "TALR_QUERY";
    private static final long ANNOUNCE_INTERVAL = 2000L;

    private static volatile DatagramSocket announceSock;
    private static volatile boolean announcing;
    private static Thread announceThread;

    private LanDiscovery() {
    }

    /** 主机侧：后台循环广播自身房间，并响应客户端 QUERY（单播回复）。 */
    public static void startAnnounce(String name, int port) {
        stopAnnounce();
        DatagramSocket sock;
        try {
            sock = new DatagramSocket(DISCOVERY_PORT);
            sock.setBroadcast(true);
        } catch (SocketException e) {
            return;
        }
        announceSock = sock;
        announcing = true;
        announceThread = new Thread(() -> {
            byte[] payload = (MAGIC + "|" + name + "|" + port).getBytes(StandardCharsets.UTF_8);
            byte[] buf = new byte[256];
            long last = 0;
            while (announcing) {
                long now = System.currentTimeMillis();
                if (now - last >= ANNOUNCE_INTERVAL) {
                    try {
                        DatagramPacket p = new DatagramPacket(payload, payload.length,
                                InetAddress.getByName("255.255.255.255"), DISCOVERY_PORT);
                        sock.send(p);
                    } catch (IOException ignored) {
                    }
                    last = now;
                }
                try {
                    sock.setSoTimeout(150);
                    DatagramPacket r = new DatagramPacket(buf, buf.length);
                    sock.receive(r);
                    String msg = new String(r.getData(), r.getOffset(), r.getLength(), StandardCharsets.UTF_8).trim();
                    if (QUERY.equals(msg)) {
                        DatagramPacket reply = new DatagramPacket(payload, payload.length,
                                r.getAddress(), r.getPort());
                        sock.send(reply);
                    }
                } catch (SocketTimeoutException ignored) {
                } catch (IOException ignored) {
                }
            }
        }, "lan-announce");
        announceThread.setDaemon(true);
        announceThread.start();
    }

    public static void stopAnnounce() {
        announcing = false;
        if (announceThread != null) {
            announceThread.interrupt();
            announceThread = null;
        }
        DatagramSocket s = announceSock;
        announceSock = null;
        if (s != null) {
            s.close();
        }
    }

    /** 一个可加入的房间。 */
    public static class Room {
        public final String name;
        public final String ip;
        public final int port;

        Room(String name, String ip, int port) {
            this.name = name;
            this.ip = ip;
            this.port = port;
        }

        @Override
        public String toString() {
            return name + "  (" + ip + ":" + port + ")";
        }
    }

    /** 客户端侧：广播查询并收集回复，timeoutMs 内返回发现的房间。 */
    public static List<Room> discover(int timeoutMs) {
        List<Room> rooms = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        DatagramSocket sock = null;
        try {
            sock = new DatagramSocket();
            sock.setBroadcast(true);
            sock.setSoTimeout(100);
            byte[] q = QUERY.getBytes(StandardCharsets.UTF_8);
            // 广播 + 本机回环，兼容局域网与同机测试
            InetAddress bc = InetAddress.getByName("255.255.255.255");
            sock.send(new DatagramPacket(q, q.length, bc, DISCOVERY_PORT));
            try {
                InetAddress lo = InetAddress.getByName("127.0.0.1");
                sock.send(new DatagramPacket(q, q.length, lo, DISCOVERY_PORT));
            } catch (IOException ignored) {
            }
            long deadline = System.currentTimeMillis() + timeoutMs;
            byte[] buf = new byte[512];
            while (System.currentTimeMillis() < deadline) {
                try {
                    DatagramPacket p = new DatagramPacket(buf, buf.length);
                    sock.receive(p);
                    String msg = new String(p.getData(), p.getOffset(), p.getLength(), StandardCharsets.UTF_8);
                    String[] parts = msg.split("\\|");
                    if (parts.length >= 3 && MAGIC.equals(parts[0])) {
                        try {
                            int port = Integer.parseInt(parts[2]);
                            String key = p.getAddress().getHostAddress() + ":" + port;
                            if (seen.add(key)) {
                                rooms.add(new Room(parts[1], p.getAddress().getHostAddress(), port));
                            }
                        } catch (NumberFormatException ignored) {
                        }
                    }
                } catch (SocketTimeoutException ignored) {
                } catch (IOException ignored) {
                }
            }
        } catch (IOException ignored) {
        } finally {
            if (sock != null) {
                sock.close();
            }
        }
        return rooms;
    }
}
