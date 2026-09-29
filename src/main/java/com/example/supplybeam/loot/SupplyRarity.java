package com.example.supplybeam.loot;

import net.minecraft.ChatFormatting;
import net.minecraft.util.RandomSource;

/**
 * 补给稀有度，四档（由低到高）：稀有（蓝）→ 罕见（绿）→ 史诗（紫）→ 传奇（红）。
 * 刷新权重与光柱颜色由配置文件控制（SupplyBeamConfig），此处保留
 * 默认颜色（配置解析失败时回退）、名称、着色与独立模式开箱条数。
 * 与 Manhunt 联动时按 ordinal 选择抽奖组合（见 ManhuntHook.LOTTERY_TIERS）。
 */
public enum SupplyRarity {
    RARE(0x3AB8FF, ChatFormatting.AQUA, 4, "rare"),
    UNCOMMON(0x3AE86B, ChatFormatting.GREEN, 6, "uncommon"),
    EPIC(0xB45BFF, ChatFormatting.LIGHT_PURPLE, 8, "epic"),
    LEGENDARY(0xFF4A5E, ChatFormatting.RED, 10, "legendary");

    public static final SupplyRarity[] VALUES = values();

    /** 配置缺失/非法时的回退色（0xRRGGBB）。 */
    private final int defaultColor;
    private final ChatFormatting formatting;
    /** 独立模式开箱结算条数（Manhunt 联动时由其奖池流程决定）。 */
    private final int rolls;
    private final String id;

    SupplyRarity(int defaultColor, ChatFormatting formatting, int rolls, String id) {
        this.defaultColor = defaultColor;
        this.formatting = formatting;
        this.rolls = rolls;
        this.id = id;
    }

    public int defaultColor() {
        return this.defaultColor;
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

    /** 按配置权重随机抽一档稀有度。 */
    public static SupplyRarity weighted(RandomSource random) {
        int[] weights = com.example.supplybeam.SupplyBeamConfig.weights();
        int total = 0;
        for (int w : weights) {
            total += Math.max(0, w);
        }
        if (total <= 0) {
            return RARE;
        }
        int roll = random.nextInt(total);
        for (int i = 0; i < weights.length; i++) {
            roll -= Math.max(0, weights[i]);
            if (roll < 0) {
                return VALUES[i];
            }
        }
        return LEGENDARY;
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
