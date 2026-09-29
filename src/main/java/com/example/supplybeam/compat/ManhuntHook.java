package com.example.supplybeam.compat;

import java.util.ArrayList;
import java.util.List;

import com.example.manhunt.game.ManhuntGame;
import com.example.manhunt.loot.PendingRewardManager;
import com.example.manhunt.loot.RewardPools;
import com.example.manhunt.net.LootRollPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Manhunt 钩子：只有在 Manhunt 真正存在（ModList 判定通过）时才会被类加载，
 * 因此这里可以安全地直接引用 Manhunt 的类。
 */
final class ManhuntHook {
    private ManhuntHook() {}

    /**
     * 四档稀有度对应的抽奖配方：{奖池档位组合, 件数, 抽奖类型}。
     * 稀有 = 一+二档 ×6；罕见 = 二+三档 ×6；史诗 = 二三四档 ×6；传奇 = 二三四档 ×8（超级）。
     */
    private static final int[][] LOTTERY_TIERS = {{0, 1}, {1, 2}, {1, 2, 3}, {1, 2, 3}};
    private static final int[] LOTTERY_COUNTS = {6, 6, 6, 8};
    private static final int[] LOTTERY_TYPES = {
        LootRollPayload.TYPE_RESOURCE, LootRollPayload.TYPE_RESOURCE,
        LootRollPayload.TYPE_RESOURCE, LootRollPayload.TYPE_SUPER
    };

    /** 猎人游戏的逃脱窗口期（猎人被定身、全员热身）——期间暂停刷新光柱。 */
    static boolean inEscapeWindow() {
        return ManhuntGame.phase() == ManhuntGame.Phase.ESCAPE;
    }

    /**
     * 按光柱稀有度触发一次资源抽奖：合并对应档位奖池后抽取指定件数，
     * 抽奖 UI 边框/品级色使用光柱自身的颜色（ARGB）。
     */
    static boolean rollLottery(ServerPlayer player, int rarityOrdinal, int accentColor) {
        int idx = Math.floorMod(rarityOrdinal, LOTTERY_TIERS.length);
        List<RewardPools.Entry> pool = new ArrayList<>();
        for (int tier : LOTTERY_TIERS[idx]) {
            pool.addAll(RewardPools.pool(tier));
        }
        if (pool.isEmpty()) {
            return false;
        }
        int count = LOTTERY_COUNTS[idx];
        List<ItemStack> items = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            items.add(RewardPools.weightedPick(pool).roll(player.registryAccess()));
        }
        int type = LOTTERY_TYPES[idx];
        try {
            PendingRewardManager.start(player, type, items, accentColor);
        } catch (NoSuchMethodError fallback) {
            // 旧版 Manhunt（无强调色重载）：退回默认边框色
            PendingRewardManager.start(player, type, items);
        }
        return true;
    }
}
