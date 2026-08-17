import java.util.prefs.Preferences;

/**
 * 最高分持久化：用 java.util.prefs.Preferences 存到用户注册表/配置树。
 *
 * 无需文件 IO 权限，跨会话保留。setHighScore 仅在新分更高时调用。
 */
public class HighScoreManager {

    private static final String KEY = "highscore";
    private final Preferences prefs;

    public HighScoreManager() {
        prefs = Preferences.userNodeForPackage(HighScoreManager.class);
    }

    public int getHighScore() {
        return prefs.getInt(KEY, 0);
    }

    /** 若 score 超过当前最高分则更新并返回 true。 */
    public boolean submit(int score) {
        if (score > getHighScore()) {
            prefs.putInt(KEY, score);
            return true;
        }
        return false;
    }
}
