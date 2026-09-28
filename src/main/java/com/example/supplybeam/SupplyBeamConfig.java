package com.example.supplybeam;

/**
 * 全部可调数值。平衡性调整只改这一个文件。
 * 刻：20 刻 = 1 秒。
 */
public final class SupplyBeamConfig {
    private SupplyBeamConfig() {}

    // ==================== 生成 ====================
    /** 光柱生成间隔下限（刻）：2 分钟。 */
    public static final int SPAWN_INTERVAL_MIN_TICKS = 2400;
    /** 光柱生成间隔上限（刻）：4 分钟。 */
    public static final int SPAWN_INTERVAL_MAX_TICKS = 4800;
    /** 世界启动后首次尝试生成的延迟（刻）。 */
    public static final int FIRST_SPAWN_DELAY_TICKS = 600;
    /** 同一世界同时存在的补给光柱数量上限。 */
    public static final int MAX_ACTIVE = 3;
    /** 生成点距随机玩家水平距离下限/上限（格）。 */
    public static final double SPAWN_RADIUS_MIN = 24.0;
    public static final double SPAWN_RADIUS_MAX = 64.0;
    /** 地表高度低于此值不生成（避免海底/深湖）。 */
    public static final int MIN_SURFACE_Y = 63;
    /** 光柱顶端距地表的高度区间（格）。 */
    public static final int BEAM_HEIGHT_MIN = 42;
    public static final int BEAM_HEIGHT_MAX = 56;

    // ==================== 阶段 ====================
    /** 光柱从地面"生长"到完全显现的时长（刻）：20 秒。 */
    public static final int BEAM_GROWTH_TICKS = 400;
    /** 补给箱下降速度（格/刻）：0.03 × 20 = 0.6 格/秒。 */
    public static final double DESCEND_SPEED = 0.03;
    /** 补给箱着陆后无人拾取的自毁倒计时（刻）：5 分钟。 */
    public static final int GROUND_LIFETIME_TICKS = 6000;
    /** 消散动画时长（刻）：2 秒。 */
    public static final int VANISH_TICKS = 40;

    // ==================== 渲染（客户端仅参考，数值与服务端无关） ====================
    /** 补给箱八面体：水平半径 / 垂直半高（格）。 */
    public static final float CRATE_RADIUS = 0.62f;
    public static final float CRATE_HALF_HEIGHT = 0.78f;
    /** 光柱内芯/外晕半径（格）。 */
    public static final float BEAM_INNER_RADIUS = 0.24f;
    public static final float BEAM_OUTER_RADIUS = 0.88f;
}
