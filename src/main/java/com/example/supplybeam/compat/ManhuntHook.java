package com.example.supplybeam.compat;

import java.util.ArrayList;
import java.util.List;

import com.example.manhunt.GameConfig;
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

    /** 猎人游戏的逃脱窗口期（猎人被定身、全员热身）——期间暂停刷新光柱。 */
    static boolean inEscapeWindow() {
        return ManhuntGame.phase() == ManhuntGame.Phase.ESCAPE;
    }

    /**
     * 触发一次指定档位（0~3）的资源抽奖：从对应档位奖池抽取
     * {@code GameConfig.ROLL_ITEMS} 种物品，走 Manhunt 的待领取流程
     * （客户端播放老虎机动画，上下方向键领取）。
     */
    static boolean rollLottery(ServerPlayer player, int tier) {
        var pool = RewardPools.pool(Math.max(0, Math.min(3, tier)));
        if (pool.isEmpty()) {
            return false;
        }
        List<ItemStack> items = new ArrayList<>(GameConfig.ROLL_ITEMS);
        for (int i = 0; i < GameConfig.ROLL_ITEMS; i++) {
            items.add(RewardPools.weightedPick(pool).roll(player.registryAccess()));
        }
        PendingRewardManager.start(player, LootRollPayload.TYPE_RESOURCE, items);
        return true;
    }
}
