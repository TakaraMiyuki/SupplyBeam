package com.example.supplybeam.compat;

import com.example.manhunt.game.ManhuntGame;

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
}
