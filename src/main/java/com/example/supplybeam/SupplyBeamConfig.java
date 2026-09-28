package com.example.supplybeam;

import com.example.supplybeam.loot.SupplyRarity;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * 全部可调数值：config/supplybeam-common.toml（客户端与服务端各自加载同一份默认）。
 * 光柱颜色由服务端同步到实体（客户端渲染始终使用服务端下发的颜色）。
 */
public final class SupplyBeamConfig {
    private SupplyBeamConfig() {}

    // ==================== 不入配置的结构常量 ====================
    /** 补给箱八面体：水平半径 / 垂直半高（格）。总高 3 格。 */
    public static final float CRATE_RADIUS = 1.3f;
    public static final float CRATE_HALF_HEIGHT = 1.5f;
    /** 光柱内芯/外晕半径（格）。 */
    public static final float BEAM_INNER_RADIUS = 0.30f;
    public static final float BEAM_OUTER_RADIUS = 1.05f;
    /** 消散动画时长（刻）：2 秒，固定值保证客户端动画对齐。 */
    public static final int VANISH_TICKS = 40;

    // ==================== 配置项 ====================
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.IntValue SPAWN_INTERVAL_MIN_SECONDS;
    public static final ModConfigSpec.IntValue SPAWN_INTERVAL_MAX_SECONDS;
    public static final ModConfigSpec.IntValue MAX_ACTIVE;
    public static final ModConfigSpec.IntValue SPAWN_RADIUS_MIN;
    public static final ModConfigSpec.IntValue SPAWN_RADIUS_MAX;
    public static final ModConfigSpec.IntValue MIN_SURFACE_Y;

    public static final ModConfigSpec.IntValue BEAM_HEIGHT_MIN;
    public static final ModConfigSpec.IntValue BEAM_HEIGHT_MAX;
    public static final ModConfigSpec.IntValue BEAM_GROWTH_SECONDS;

    public static final ModConfigSpec.DoubleValue DESCEND_SPEED_BLOCKS_PER_SECOND;
    public static final ModConfigSpec.IntValue GROUND_LIFETIME_SECONDS;

    public static final ModConfigSpec.IntValue WEIGHT_COMMON;
    public static final ModConfigSpec.IntValue WEIGHT_UNCOMMON;
    public static final ModConfigSpec.IntValue WEIGHT_RARE;
    public static final ModConfigSpec.IntValue WEIGHT_EPIC;
    public static final ModConfigSpec.IntValue WEIGHT_MYTHIC;

    public static final ModConfigSpec.ConfigValue<String> COLOR_COMMON;
    public static final ModConfigSpec.ConfigValue<String> COLOR_UNCOMMON;
    public static final ModConfigSpec.ConfigValue<String> COLOR_RARE;
    public static final ModConfigSpec.ConfigValue<String> COLOR_EPIC;
    public static final ModConfigSpec.ConfigValue<String> COLOR_MYTHIC;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.comment("补给光柱的刷新行为").push("spawn");
        SPAWN_INTERVAL_MIN_SECONDS = builder
            .comment("两次刷新尝试的最小间隔（秒）")
            .defineInRange("spawnIntervalMinSeconds", 120, 5, 86400);
        SPAWN_INTERVAL_MAX_SECONDS = builder
            .comment("两次刷新尝试的最大间隔（秒）")
            .defineInRange("spawnIntervalMaxSeconds", 240, 10, 86400);
        MAX_ACTIVE = builder
            .comment("同一世界同时存在的补给光柱数量上限")
            .defineInRange("maxActive", 3, 1, 64);
        SPAWN_RADIUS_MIN = builder
            .comment("生成点距随机玩家的最小水平距离（格）——刻意拉开，让玩家看到光柱后主动前往")
            .defineInRange("spawnRadiusMin", 96, 16, 4096);
        SPAWN_RADIUS_MAX = builder
            .comment("生成点距随机玩家的最大水平距离（格），超出部分进入'已探索区域'范畴")
            .defineInRange("spawnRadiusMax", 384, 32, 8192);
        MIN_SURFACE_Y = builder
            .comment("地表高度低于此值不生成（排除海底/深湖）")
            .defineInRange("minSurfaceY", 63, -64, 320);
        builder.pop();

        builder.comment("光柱形态").push("beam");
        BEAM_HEIGHT_MIN = builder
            .comment("光柱顶端距地表的最小高度（格）")
            .defineInRange("beamHeightMin", 126, 24, 512);
        BEAM_HEIGHT_MAX = builder
            .comment("光柱顶端距地表的最大高度（格）")
            .defineInRange("beamHeightMax", 168, 32, 512);
        BEAM_GROWTH_SECONDS = builder
            .comment("光柱从地面生长到完全显现的时长（秒）")
            .defineInRange("beamGrowthSeconds", 20, 1, 600);
        builder.pop();

        builder.comment("补给箱").push("crate");
        DESCEND_SPEED_BLOCKS_PER_SECOND = builder
            .comment("补给箱沿光柱下降的速度（格/秒）")
            .defineInRange("descendSpeedBlocksPerSecond", 0.6, 0.05, 20.0);
        GROUND_LIFETIME_SECONDS = builder
            .comment("补给箱着陆后无人拾取的自毁倒计时（秒）")
            .defineInRange("groundLifetimeSeconds", 300, 10, 86400);
        builder.pop();

        builder.comment("稀有度：刷新权重与光柱颜色").push("rarity");
        WEIGHT_COMMON = builder.comment("常规补给（绿）刷新权重").defineInRange("weightCommon", 46, 0, 10000);
        WEIGHT_UNCOMMON = builder.comment("稀有补给（蓝）刷新权重").defineInRange("weightUncommon", 27, 0, 10000);
        WEIGHT_RARE = builder.comment("史诗补给（紫）刷新权重").defineInRange("weightRare", 15, 0, 10000);
        WEIGHT_EPIC = builder.comment("传说补给（红）刷新权重").defineInRange("weightEpic", 9, 0, 10000);
        WEIGHT_MYTHIC = builder.comment("神话补给（金）刷新权重").defineInRange("weightMythic", 3, 0, 10000);
        COLOR_COMMON = builder.comment("常规补给光柱颜色（#RRGGBB）").define("colorCommon", "#3AE86B");
        COLOR_UNCOMMON = builder.comment("稀有补给光柱颜色（#RRGGBB）").define("colorUncommon", "#3AB8FF");
        COLOR_RARE = builder.comment("史诗补给光柱颜色（#RRGGBB）").define("colorRare", "#B45BFF");
        COLOR_EPIC = builder.comment("传说补给光柱颜色（#RRGGBB）").define("colorEpic", "#FF4A5E");
        COLOR_MYTHIC = builder.comment("神话补给光柱颜色（#RRGGBB）").define("colorMythic", "#FFC845");
        builder.pop();

        SPEC = builder.build();
    }

    // ==================== 便捷读取 ====================

    /** 各稀有度刷新权重（下标 = ordinal）。 */
    public static int[] weights() {
        return new int[] {
            Math.max(0, WEIGHT_COMMON.get()),
            Math.max(0, WEIGHT_UNCOMMON.get()),
            Math.max(0, WEIGHT_RARE.get()),
            Math.max(0, WEIGHT_EPIC.get()),
            Math.max(0, WEIGHT_MYTHIC.get())
        };
    }

    /** 稀有度主题色（0xRRGGBB），解析失败回退枚举默认色。 */
    public static int colorOf(SupplyRarity rarity) {
        String hex = switch (rarity) {
            case COMMON -> COLOR_COMMON.get();
            case UNCOMMON -> COLOR_UNCOMMON.get();
            case RARE -> COLOR_RARE.get();
            case EPIC -> COLOR_EPIC.get();
            case MYTHIC -> COLOR_MYTHIC.get();
        };
        try {
            String cleaned = hex.trim().replace("#", "");
            if (cleaned.length() == 6) {
                return Integer.parseInt(cleaned, 16) & 0xFFFFFF;
            }
        } catch (NumberFormatException ignored) {
        }
        return rarity.defaultColor();
    }
}
