package com.example.supplybeam.compat;

import net.neoforged.fml.ModList;

/**
 * 猎人游戏 Manhunt 可选联动桥。
 * Manhunt 未安装时 {@link #shouldHoldSpawns()} 恒为 false，本模组作为独立玩法运行；
 * 安装后进入对局的逃脱窗口期（猎人被定身的 60 秒）暂停刷新光柱，
 * 追逐阶段与空闲状态照常刷新。
 */
public final class ManhuntBridge {
    private ManhuntBridge() {}

    private static final boolean LOADED = ModList.get().isLoaded("manhunt");

    public static boolean loaded() {
        return LOADED;
    }

    /** 是否应暂停生成（true = 本周期跳过）。 */
    public static boolean shouldHoldSpawns() {
        if (!LOADED) {
            return false;
        }
        return ManhuntHook.inEscapeWindow();
    }
}
