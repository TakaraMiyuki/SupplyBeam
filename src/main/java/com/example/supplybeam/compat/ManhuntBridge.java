package com.example.supplybeam.compat;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;

/**
 * 猎人游戏 Manhunt 可选联动桥。
 * Manhunt 未安装时本模组作为独立玩法运行（开箱直接掉落自身奖池）；
 * 安装后：
 * <ul>
 *   <li>逃脱窗口期（猎人被定身的 60 秒）暂停刷新光柱；</li>
 *   <li>右键开启补给箱改为触发对应档位的资源抽奖（四档稀有度 = 四档里程奖池），
 *       复用 Manhunt 的老虎机动画与待领取流程。</li>
 * </ul>
 */
public final class ManhuntBridge {
    private ManhuntBridge() {}

    private static final boolean LOADED = ModList.get().isLoaded("manhunt");

    public static boolean loaded() {
        return LOADED;
    }

    /** Manhunt 资源抽奖联动是否可用（模组在场即可用）。 */
    public static boolean lotteryAvailable() {
        return LOADED;
    }

    /**
     * 按光柱稀有度触发一次资源抽奖（四档稀有度各有奖池组合/件数配方，
     * 抽奖 UI 边框色 = 光柱自身颜色）。
     * @param rarityOrdinal SupplyRarity 的 ordinal
     * @param accentColor   抽奖 UI 强调色（ARGB，含 alpha）
     * @return true = 已开启抽奖流程；false = 联动不可用或奖池为空
     */
    public static boolean rollLottery(ServerPlayer player, int rarityOrdinal, int accentColor) {
        if (!LOADED) {
            return false;
        }
        return ManhuntHook.rollLottery(player, rarityOrdinal, accentColor);
    }

    /** 是否应暂停生成（true = 本周期跳过）。 */
    public static boolean shouldHoldSpawns() {
        if (!LOADED) {
            return false;
        }
        return ManhuntHook.inEscapeWindow();
    }
}
