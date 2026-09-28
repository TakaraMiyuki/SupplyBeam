package com.example.supplybeam.entity;

import com.example.supplybeam.SupplyBeamConfig;
import com.example.supplybeam.compat.ManhuntBridge;
import com.example.supplybeam.loot.SupplyRarity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 补给光柱生成调度：周期性在主世界随机在线玩家的"远方向"刷新光柱——
 * 默认 96–384 格，落在视野地平线与已探索区域之间，玩家看到光柱后需要主动前往，
 * 而不是光柱送到脚边。选址要求：地表足够高、落点为干燥实心地面且上方有 3 格净空。
 */
public final class BeamManager {
    private BeamManager() {}

    private static final RandomSource RNG = RandomSource.create();
    private static final Set<UUID> ACTIVE = new HashSet<>();

    private static int nextSpawnTick = 300;

    /** 光柱实体在服务端创建/移除时登记（见 SupplyCrateEntity 构造与 onRemovedFromLevel）。 */
    public static void add(SupplyCrateEntity entity) {
        ACTIVE.add(entity.getUUID());
    }

    public static void remove(SupplyCrateEntity entity) {
        ACTIVE.remove(entity.getUUID());
    }

    public static int activeCount() {
        return ACTIVE.size();
    }

    /** 调试：距下次自然刷新尝试的秒数。 */
    public static int nextSpawnSeconds() {
        return Math.max(0, (nextSpawnTick + 19) / 20);
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        ServerLevel level = server.overworld();
        if (level == null) {
            return;
        }
        if (--nextSpawnTick > 0) {
            return;
        }
        scheduleNext();
        trySpawn(level);
    }

    private static void scheduleNext() {
        int min = Math.max(1, SupplyBeamConfig.SPAWN_INTERVAL_MIN_SECONDS.get()) * 20;
        int max = Math.max(min + 20, SupplyBeamConfig.SPAWN_INTERVAL_MAX_SECONDS.get() * 20);
        nextSpawnTick = min + RNG.nextInt(max - min);
    }

    private static void trySpawn(ServerLevel level) {
        if (ACTIVE.size() >= SupplyBeamConfig.MAX_ACTIVE.get()) {
            debugLog("跳过刷新：活跃光柱已达上限（" + ACTIVE.size() + "）");
            return;
        }
        // Manhunt 联动：逃脱窗口期（猎人被定身）暂停刷新
        if (ManhuntBridge.shouldHoldSpawns()) {
            debugLog("跳过刷新：Manhunt 逃脱窗口期");
            return;
        }
        List<ServerPlayer> players = level.players();
        if (players.isEmpty()) {
            debugLog("跳过刷新：主世界没有玩家");
            return;
        }
        ServerPlayer anchor = players.get(RNG.nextInt(players.size()));

        double angle = RNG.nextDouble() * Math.PI * 2;
        int distMin = Math.max(32, SupplyBeamConfig.SPAWN_RADIUS_MIN.get());
        int distMax = Math.max(distMin + 16, SupplyBeamConfig.SPAWN_RADIUS_MAX.get());
        double dist = distMin + RNG.nextDouble() * (distMax - distMin);
        int bx = (int) Math.round(anchor.getX() + Math.cos(angle) * dist);
        int bz = (int) Math.round(anchor.getZ() + Math.sin(angle) * dist);
        SupplyRarity rarity = SupplyRarity.weighted(RNG);
        if (spawnAt(level, bx, bz, rarity)) {
            debugBroadcast(level, rarity, bx, bz);
        }
    }

    /** 调试：控制台记录选址/跳过原因（仅调试模式）。 */
    private static void debugLog(String message) {
        if (SupplyBeamConfig.debugMode()) {
            com.example.supplybeam.SupplyBeamMod.LOGGER.info("[SupplyBeam 调试] {}", message);
        }
    }

    /** 调试：自然刷新成功时向全服广播坐标（仅调试模式）。 */
    private static void debugBroadcast(ServerLevel level, SupplyRarity rarity, int bx, int bz) {
        if (!SupplyBeamConfig.debugMode()) {
            return;
        }
        com.example.supplybeam.SupplyBeamMod.LOGGER.info("[SupplyBeam 调试] {} 光柱已刷新于 x={}, z={}",
            rarity.id(), bx, bz);
        net.minecraft.network.chat.MutableComponent rarityPart =
            net.minecraft.network.chat.Component.translatable(rarity.translationKey())
                .withStyle(rarity.formatting());
        net.minecraft.network.chat.Component message =
            net.minecraft.network.chat.Component.translatable("supplybeam.debug.spawned",
                rarityPart, bx, bz).withStyle(net.minecraft.ChatFormatting.GRAY);
        for (ServerPlayer player : level.players()) {
            player.sendSystemMessage(message);
        }
    }

    /**
     * 在指定地表坐标生成一根补给光柱。选址无效（海洋/深谷/悬崖/净空不足）时静默放弃本次机会。
     * @return 是否生成成功
     */
    public static boolean spawnAt(ServerLevel level, int bx, int bz, SupplyRarity rarity) {
        WorldBorder border = level.getWorldBorder();
        bx = (int) Math.max(border.getMinX() + 8, Math.min(bx, border.getMaxX() - 8));
        bz = (int) Math.max(border.getMinZ() + 8, Math.min(bz, border.getMaxZ() - 8));

        // 26.2 的 Level.getHeight 不再加载区块（未加载时直接返回世界最低层），
        // 远距选址必须先同步生成目标区块
        level.getChunk(SectionPos.blockToSectionCoord(bx), SectionPos.blockToSectionCoord(bz),
            ChunkStatus.FULL, true);

        int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz);
        if (surface < SupplyBeamConfig.MIN_SURFACE_Y.get()) {
            debugLog("选址放弃 (" + bx + "," + bz + ")：地表高度 " + surface + " 低于下限");
            return false;
        }
        BlockPos below = new BlockPos(bx, surface - 1, bz);
        if (!level.getFluidState(below).isEmpty()
            || !level.getBlockState(below).blocksMotion()) {
            debugLog("选址放弃 (" + bx + "," + bz + ")：落点不是干燥实心地面");
            return false; // 落点必须是干燥实心地面（排除海面/湖面/悬崖）
        }
        // 3 格高的补给箱需要上方三格净空
        for (int dy = 0; dy < 3; dy++) {
            BlockPos pos = new BlockPos(bx, surface + dy, bz);
            if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) {
                debugLog("选址放弃 (" + bx + "," + bz + ")：上方净空不足");
                return false;
            }
        }

        int heightMin = Math.max(48, SupplyBeamConfig.BEAM_HEIGHT_MIN.get());
        int heightMax = Math.max(heightMin + 8, SupplyBeamConfig.BEAM_HEIGHT_MAX.get());
        int height = heightMin + RNG.nextInt(heightMax - heightMin + 1);
        float topY = Math.min((float) level.getMaxY() - 6.0f, surface + height);
        if (topY - surface < 48.0f) {
            debugLog("选址放弃 (" + bx + "," + bz + ")：地表过高，光柱放不下（地表 y=" + surface + "）");
            return false; // 高山地形放不下完整光柱，放弃本次
        }

        SupplyCrateEntity crate = new SupplyCrateEntity(SupplyBeamEntities.SUPPLY_CRATE.get(), level);
        crate.snapTo(bx + 0.5, surface, bz + 0.5, RNG.nextFloat() * 360.0f, 0.0f);
        crate.setRarity(rarity);
        crate.setColor(SupplyBeamConfig.colorOf(rarity));
        crate.setGrowthTicks(Math.max(1, SupplyBeamConfig.BEAM_GROWTH_SECONDS.get() * 20));
        crate.setGroundY(surface);
        crate.setTopY(topY);
        level.addFreshEntity(crate);
        return true;
    }
}
