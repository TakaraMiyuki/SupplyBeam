package com.example.supplybeam.loot;

import net.minecraft.ChatFormatting;
import net.minecraft.util.RandomSource;

/**
 * 补给稀有度，灵感来自方舟的空投颜色：绿 → 蓝 → 紫 → 红 → 金。
 * 权重决定生成概率，rolls 决定开箱时结算的物品条数。
 */
public enum SupplyRarity {
    COMMON(46, 0x4ADE80, ChatFormatting.GREEN, 4, "common"),
    UNCOMMON(27, 0x38BDF8, ChatFormatting.AQUA, 6, "uncommon"),
    RARE(15, 0xA855F7, ChatFormatting.LIGHT_PURPLE, 8, "rare"),
    EPIC(9, 0xF43F5E, ChatFormatting.RED, 10, "epic"),
    MYTHIC(3, 0xFBBF24, ChatFormatting.GOLD, 12, "mythic");

    public static final SupplyRarity[] VALUES = values();

    /** 生成权重。 */
    private final int weight;
    /** 主题色（0xRRGGBB），用于粒子与光柱染色。 */
    private final int color;
    private final ChatFormatting formatting;
    /** 开箱结算条数。 */
    private final int rolls;
    private final String id;

    SupplyRarity(int weight, int color, ChatFormatting formatting, int rolls, String id) {
        this.weight = weight;
        this.color = color;
        this.formatting = formatting;
        this.rolls = rolls;
        this.id = id;
    }

    public int weight() {
        return this.weight;
    }

    /** 0xRRGGBB。 */
    public int color() {
        return this.color;
    }

    public ChatFormatting formatting() {
        return this.formatting;
    }

    public int rolls() {
        return this.rolls;
    }

    public String id() {
        return this.id;
    }

    public String translationKey() {
        return "supplybeam.rarity." + this.id;
    }

    /** 按权重随机抽一档稀有度。 */
    public static SupplyRarity weighted(RandomSource random) {
        int total = 0;
        for (SupplyRarity r : VALUES) {
            total += r.weight;
        }
        int roll = random.nextInt(total);
        for (SupplyRarity r : VALUES) {
            roll -= r.weight;
            if (roll < 0) {
                return r;
            }
        }
        return MYTHIC;
    }

    /** 按字符串 id 解析（指令用），不区分大小写。 */
    public static SupplyRarity byId(String id) {
        for (SupplyRarity r : VALUES) {
            if (r.id.equalsIgnoreCase(id)) {
                return r;
            }
        }
        return null;
    }
}
