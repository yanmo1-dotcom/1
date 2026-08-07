import java.awt.Color;

/**
 * 道具类型：决定拾取效果、颜色与显示符号。
 *
 * HEAL：回复 1 命
 * FIRE_UP：火力升级（单发→双发→三发）
 * SHIELD：获得护盾，吸收下一次伤害
 * BOMB：增加一枚清屏炸弹
 */
public enum PowerUpType {
    HEAL(Color.PINK, "+"),
    FIRE_UP(Color.CYAN, "F"),
    SHIELD(new Color(120, 180, 255), "S"),
    BOMB(Color.ORANGE, "B");

    private final Color color;
    private final String symbol;

    PowerUpType(Color color, String symbol) {
        this.color = color;
        this.symbol = symbol;
    }

    public Color getColor() { return color; }
    public String getSymbol() { return symbol; }
}
