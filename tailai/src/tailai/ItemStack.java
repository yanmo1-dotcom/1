package tailai;

/** 物品堆叠：一种物品 + 数量 + 修饰语。 */
public class ItemStack {
    public Item item;
    public int count;
    /** 修饰语（重铸获得），null 表示无修饰语。 */
    public Modifier modifier;

    public ItemStack(Item item, int count) {
        this.item = item;
        this.count = count;
        this.modifier = null;
    }

    /** 装备修饰语：影响武器伤害、护甲防御、饰品效果等。 */
    public enum Modifier {
        // 武器修饰语
        SHARP("锋利", 0, 0.15f, 0, 0),      // +15%伤害
        QUICK("快速", 0, 0, 0.10f, 0),       // +10%攻速（丐版用伤害代替显示）
        DEADLY("致命", 0, 0.20f, 0, 0),      // +20%伤害
        GODLY("神级", 0, 0.30f, 0, 0),        // +30%伤害
        // 护甲修饰语
        HARD("坚固", 2, 0, 0, 0),             // +2防御
        GUARDED("护佑", 4, 0, 0, 0),          // +4防御
        // 饰品修饰语
        WARDING("护佑", 3, 0, 0, 0),          // +3防御
        SWIFT("迅捷", 0, 0, 0, 0.10f),        // +10%速度
        ANGRY("愤怒", 0, 0.10f, 0, 0);        // +10%伤害

        public final String name;
        public final int defense;
        public final float damageMul;
        public final float speedMul;
        public final float attackSpeedMul;

        Modifier(String name, int defense, float damageMul, float attackSpeedMul, float speedMul) {
            this.name = name;
            this.defense = defense;
            this.damageMul = damageMul;
            this.attackSpeedMul = attackSpeedMul;
            this.speedMul = speedMul;
        }

        /** 获取适用于武器的修饰语列表。 */
        public static Modifier[] weaponModifiers() {
            return new Modifier[]{SHARP, QUICK, DEADLY, GODLY};
        }

        /** 获取适用于护甲的修饰语列表。 */
        public static Modifier[] armorModifiers() {
            return new Modifier[]{HARD, GUARDED};
        }

        /** 获取适用于饰品的修饰语列表。 */
        public static Modifier[] accessoryModifiers() {
            return new Modifier[]{WARDING, SWIFT, ANGRY};
        }

        /** 判断修饰语是否适用于某物品。 */
        public boolean appliesTo(Item item) {
            if (item.isWeapon() || item.isMagic() || item.ranged) {
                return this == SHARP || this == QUICK || this == DEADLY || this == GODLY;
            }
            if (item.defense > 0 && !item.isAccessory()) {
                return this == HARD || this == GUARDED;
            }
            if (item.isAccessory()) {
                return this == WARDING || this == SWIFT || this == ANGRY;
            }
            return false;
        }
    }

    /** 获取该物品修饰语后的实际伤害倍率。 */
    public float damageMul() {
        return modifier != null ? 1f + modifier.damageMul : 1f;
    }

    /** 获取该物品修饰语后的额外防御。 */
    public int bonusDefense() {
        return modifier != null ? modifier.defense : 0;
    }

    /** 获取修饰语显示名称（带颜色前缀）。 */
    public String modifierName() {
        return modifier != null ? modifier.name : "";
    }
}
