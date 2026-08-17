package com.webchat.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.webchat.model.FriendRequest;
import com.webchat.model.RankRule;
import com.webchat.model.User;
import com.webchat.repository.UserJdbcRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
public class UserService {

    // 【可配置】数据目录，默认为服务器绝对路径，开发环境可通过 application.properties 覆盖
    @Value("${webchat.data-dir:/opt/webchat-data/users/}")
    private String dataDir;
    private String backupDir;

    private static final String FILE_NAME = "users.json";
    private static final String REQUESTS_FILE = "friend_requests.json";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private List<User> users = new ArrayList<>();
    private List<FriendRequest> friendRequests = new ArrayList<>();
    private long nextRequestId = 1;

    @Autowired
    private RankRuleService rankRuleService;

    @Autowired
    private UserJdbcRepository userRepository;

    public String getDataDir() { return dataDir; }

    @PostConstruct
    public void init() {
        backupDir = dataDir + "backup/";
        // 配置 Jackson 忽略未知字段，保证未来升级兼容性
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        try {
            File dir = new File(dataDir);
            if (!dir.exists()) dir.mkdirs(); // 确保数据目录存在

            File backupDirFile = new File(backupDir);
            if (!backupDirFile.exists()) backupDirFile.mkdirs(); // 确保备份目录存在

            List<User> databaseUsers = userRepository.findAll();
            if (!databaseUsers.isEmpty()) {
                users = new ArrayList<>(databaseUsers);
                loadRequests();
                System.out.println("Loaded " + users.size() + " users from database");
                return;
            }

            File file = new File(dataDir + FILE_NAME);

            // 【关键修改 2】如果服务器上还没有数据，从 resources 复制初始模板
            if (!file.exists()) {
                InputStream is = getClass().getClassLoader().getResourceAsStream(FILE_NAME);
                if (is != null) {
                    Files.copy(is, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    System.out.println("✅ 已从 resources 初始化数据文件到: " + dataDir);
                } else {
                    // 连模板都没有，创建一个空数组
                    users = new ArrayList<>();
                    saveToFile();
                    return;
                }
            }

            // 读取数据
            String content = new String(Files.readAllBytes(file.toPath()));
            if (!content.trim().isEmpty()) {
                users = objectMapper.readValue(content, new TypeReference<List<User>>() {});
                userRepository.saveAll(users);
                System.out.println("✅ 成功加载用户数据，共 " + users.size() + " 人");
            }

            // 加载好友申请记录
            loadRequests();
        } catch (Exception e) {
            System.err.println("️ 数据初始化失败: " + e.getMessage());
            e.printStackTrace();
            users = new ArrayList<>();
        }
    }

    // --- 用户基础方法 ---
    public boolean register(User newUser) {
        for (User u : users)
            if (u.getUsername().equals(newUser.getUsername())) return false;

        long maxId = users.stream()
            .mapToLong(u -> u.getId() == null ? 0 : u.getId())
            .max().orElse(0);

        newUser.setId(maxId + 1);
        newUser.setFriends(new ArrayList<>());
        newUser.setGroups(new ArrayList<>());
        long now = System.currentTimeMillis();
        newUser.setRegisteredAt(now);
        newUser.setLastLoginAt(now);
        newUser.setRankPoints(rankRuleService.getRules().getInitialPoints());
        if (newUser.getNickname() == null || newUser.getNickname().isEmpty()) {
            newUser.setNickname(newUser.getUsername());
        }
        users.add(newUser);
        saveToFile();
        return true;
    }

    public User login(String username, String password) {
        for (User u : users)
            if (u.getUsername().equals(username) && u.getPassword().equals(password)) {
                u.setLastLoginAt(System.currentTimeMillis());
                saveToFile();
                return u;
            }
        return null;
    }

    /** Credential check without changing login timestamps; used for admin promotion. */
    public User verifyCredentials(String username, String password) {
        if (username == null || password == null) return null;
        return users.stream().filter(u -> username.equals(u.getUsername()) && password.equals(u.getPassword()))
                .findFirst().orElse(null);
    }

    public User findById(Long id) {
        return users.stream()
            .filter(u -> u.getId() != null && u.getId().equals(id))
            .findFirst().orElse(null);
    }

    public List<User> getAllUsers() { return users; }

    /** Direct rank adjustment used by the administrator dashboard. */
    public synchronized boolean setRankPoints(Long uid, int points) {
        User user = findById(uid);
        if (user == null) return false;
        user.setRankPoints(Math.max(0, points));
        saveToFile();
        return true;
    }

    /**
     * 【新增】管理员重置密码功能 (纯业务逻辑，不含权限校验)
     * @param targetUsername 目标用户名
     * @param newPassword 新密码
     * @return true=成功, false=用户不存在
     */
    public boolean adminResetPassword(String targetUsername, String newPassword) {
        // 1. 查找用户
        User target = users.stream()
            .filter(u -> u.getUsername().equals(targetUsername))
            .findFirst()
            .orElse(null);
            
        if (target != null) {
            // 2. 修改密码
            target.setPassword(newPassword);
            
            // 3. 保存到文件 (会自动触发 backupFile 备份逻辑)
            saveToFile(); 
            
            System.out.println("✅ 管理员已重置用户 [" + targetUsername + "] 的密码");
            return true;
        }
        
        System.out.println("⚠️ 管理员重置密码失败：用户 [" + targetUsername + "] 不存在");
        return false;
    }

    // --- 好友方法 ---
    public boolean addFriend(Long myId, Long friendId) {
        User me = findById(myId);
        User friend = findById(friendId);
        if (me != null && friend != null && !me.getFriends().contains(friendId)) {
            me.getFriends().add(friendId);
            if (!friend.getFriends().contains(myId)) friend.getFriends().add(myId);
            saveToFile();
            return true;
        }
        return false;
    }

    public List<User> getMyFriends(Long myId) {
        User me = findById(myId);
        List<User> result = new ArrayList<>();
        if (me == null || me.getFriends() == null) return result;
        for (Long fid : me.getFriends()) {
            User f = findById(fid);
            if (f != null) result.add(f);
        }
        return result;
    }

    // --- 群组方法 ---
    public boolean joinOrCreateGroup(String groupName, Long userId) {
        User user = findById(userId);
        if (user != null) {
            if (user.getGroups() == null) user.setGroups(new ArrayList<>());
            if (!user.getGroups().contains(groupName)) {
                user.getGroups().add(groupName);
                saveToFile();
                return true;
            }
            return true;
        }
        return false;
    }

    public List<String> getMyGroups(Long userId) {
        User user = findById(userId);
        return user != null && user.getGroups() != null ? user.getGroups() : new ArrayList<>();
    }

    // 【关键修改 3】保存数据并自动备份（同步化，防止并发竞态）
    public synchronized void saveToFile() {
        try {
            userRepository.saveAll(users);
            Path filePath = Paths.get(dataDir + FILE_NAME);
            String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(users);
            Files.write(filePath, json.getBytes());

            // 执行自动备份
            backupFile(filePath);
        } catch (IOException e) {
            System.err.println("❌ 保存数据失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 对局结束后更新双方竞技数据（积分/胜负/连胜）并持久化。
     * 由 RoomService 调用，保证与 saveToFile 互斥。
     * @param winnerId 胜者，null 表示平局
     * @param loserId  败者，平局时此参数为另一方
     * @param isDraw   是否平局
     * @param deltaA   playerA(传入方1) 积分变动
     * @param deltaB   playerB(传入方2) 积分变动
     */
    public synchronized void updateMatchStats(Long playerA, Long playerB, boolean isDraw,
                                               int deltaA, int deltaB) {
        User a = findById(playerA);
        User b = findById(playerB);
        RankRule rule = rankRuleService.getRules();
        if (a != null) {
            int np = Math.max(rule.getMinPoints(), a.getRankPoints() + deltaA);
            a.setRankPoints(np);
            if (isDraw) { a.setDraws(a.getDraws() + 1); a.setCurrentStreak(0); }
            else if (deltaA > 0) { a.setWins(a.getWins() + 1); a.setCurrentStreak(a.getCurrentStreak() >= 0 ? a.getCurrentStreak() + 1 : 1); }
            else { a.setLosses(a.getLosses() + 1); a.setCurrentStreak(a.getCurrentStreak() <= 0 ? a.getCurrentStreak() - 1 : -1); }
        }
        if (b != null) {
            int np = Math.max(rule.getMinPoints(), b.getRankPoints() + deltaB);
            b.setRankPoints(np);
            if (isDraw) { b.setDraws(b.getDraws() + 1); b.setCurrentStreak(0); }
            else if (deltaB > 0) { b.setWins(b.getWins() + 1); b.setCurrentStreak(b.getCurrentStreak() >= 0 ? b.getCurrentStreak() + 1 : 1); }
            else { b.setLosses(b.getLosses() + 1); b.setCurrentStreak(b.getCurrentStreak() <= 0 ? b.getCurrentStreak() - 1 : -1); }
        }
        saveToFile();
    }

    /** 设置用户状态：ACTIVE / MUTED / BANNED（管理员封禁/禁言） */
    public synchronized boolean setStatus(Long uid, String status) {
        User u = findById(uid);
        if (u == null) return false;
        u.setStatus(status);
        saveToFile();
        return true;
    }

    /** 按用户名设置状态 */
    public synchronized boolean setStatusByUsername(String username, String status) {
        User u = findByUsername(username);
        if (u == null) return false;
        u.setStatus(status);
        saveToFile();
        return true;
    }

    // 备份辅助方法
    private void backupFile(Path source) {
        try {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            Path backupPath = Paths.get(backupDir + "users_backup_" + timestamp + ".json");
            Files.copy(source, backupPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            // 备份失败不影响主流程，仅打印日志
            System.err.println("⚠️ 数据自动备份失败: " + e.getMessage());
        }
    }

    // ================= 好友申请流程 =================

    private void loadRequests() {
        try {
            File file = new File(dataDir + REQUESTS_FILE);
            if (!file.exists()) {
                friendRequests = new ArrayList<>();
                saveRequestsToFile();
                return;
            }
            String content = new String(Files.readAllBytes(file.toPath()));
            if (!content.trim().isEmpty()) {
                friendRequests = objectMapper.readValue(content, new TypeReference<List<FriendRequest>>() {});
                nextRequestId = friendRequests.stream()
                        .mapToLong(r -> r.getId() == null ? 0 : r.getId()).max().orElse(0) + 1;
            }
        } catch (Exception e) {
            System.err.println("⚠️ 加载好友申请记录失败: " + e.getMessage());
            friendRequests = new ArrayList<>();
        }
    }

    private synchronized void saveRequestsToFile() {
        try {
            Path filePath = Paths.get(dataDir + REQUESTS_FILE);
            String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(friendRequests);
            Files.write(filePath, json.getBytes());
        } catch (Exception e) {
            System.err.println("❌ 保存好友申请记录失败: " + e.getMessage());
        }
    }

    /**
     * 发起好友申请
     * @return 1=成功创建, 0=已是好友, -1=重复申请, -2=目标不存在, -3=不能加自己
     */
    public synchronized int createFriendRequest(Long fromId, Long toId) {
        if (fromId.equals(toId)) return -3;
        User target = findById(toId);
        if (target == null) return -2;
        User me = findById(fromId);
        if (me != null && me.getFriends() != null && me.getFriends().contains(toId)) return 0;
        // 已存在待处理申请则不重复创建
        boolean hasPending = friendRequests.stream()
                .anyMatch(r -> r.getFromId().equals(fromId) && r.getToId().equals(toId)
                        && "PENDING".equals(r.getStatus()));
        if (hasPending) return -1;

        FriendRequest req = new FriendRequest();
        req.setId(nextRequestId++);
        req.setFromId(fromId);
        req.setToId(toId);
        req.setStatus("PENDING");
        req.setCreatedAt(System.currentTimeMillis());
        friendRequests.add(req);
        saveRequestsToFile();
        return 1;
    }

    /**
     * 处理好友申请
     * @return 处理后的申请记录，或 null（不存在/非待处理/无权处理）
     */
    public synchronized FriendRequest handleFriendRequest(Long requestId, Long handlerId, boolean accept) {
        FriendRequest req = friendRequests.stream()
                .filter(r -> r.getId().equals(requestId)).findFirst().orElse(null);
        if (req == null) return null;
        if (!"PENDING".equals(req.getStatus())) return null;
        if (!req.getToId().equals(handlerId)) return null; // 只有接收者能处理

        req.setStatus(accept ? "ACCEPTED" : "REJECTED");
        req.setHandledAt(System.currentTimeMillis());

        if (accept) {
            // 复用现有 addFriend：双向加好友 + 持久化（addFriend 内部会 saveToFile）
            addFriend(req.getFromId(), req.getToId());
        }
        // 无论同意/拒绝都要保存申请记录的新状态
        saveRequestsToFile();
        return req;
    }

    /** 我收到的待处理申请 */
    public List<FriendRequest> getIncomingPending(Long userId) {
        List<FriendRequest> result = new ArrayList<>();
        for (FriendRequest r : friendRequests) {
            if (r.getToId().equals(userId) && "PENDING".equals(r.getStatus())) result.add(r);
        }
        return result;
    }

    /** 我发出的全部申请（含状态，便于前端展示） */
    public List<FriendRequest> getOutgoing(Long userId) {
        List<FriendRequest> result = new ArrayList<>();
        for (FriendRequest r : friendRequests) {
            if (r.getFromId().equals(userId)) result.add(r);
        }
        return result;
    }

    public User findByUsername(String username) {
        return users.stream().filter(u -> u.getUsername().equals(username)).findFirst().orElse(null);
    }
}
