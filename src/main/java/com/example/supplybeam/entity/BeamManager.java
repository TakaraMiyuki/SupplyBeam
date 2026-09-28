package com.example.supplybeam.entity;

import com.example.supplybeam.SupplyBeamConfig;
import com.example.supplybeam.compat.ManhuntBridge;
import com.example.supplybeam.loot.SupplyRarity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.border.WorldBorder;
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
            return;
        }
        // Manhunt 联动：逃脱窗口期（猎人被定身）暂停刷新
        if (ManhuntBridge.shouldHoldSpawns()) {
            return;
        }
        List<ServerPlayer> players = level.players();
        if (players.isEmpty()) {
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
        spawnAt(level, bx, bz, rarity);
    }

    /**
     * 在指定地表坐标生成一根补给光柱。选址无效（海洋/深谷/悬崖/净空不足）时静默放弃本次机会。
     * @return 是否生成成功
     */
    public static boolean spawnAt(ServerLevel level, int bx, int bz, SupplyRarity rarity) {
        WorldBorder border = level.getWorldBorder();
        bx = (int) Math.max(border.getMinX() + 8, Math.min(bx, border.getMaxX() - 8));
        bz = (int) Math.max(border.getMinZ() + 8, Math.min(bz, border.getMaxZ() - 8));

        int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz);
        if (surface < SupplyBeamConfig.MIN_SURFACE_Y.get()) {
            return false;
        }
        BlockPos below = new BlockPos(bx, surface - 1, bz);
        if (!level.getFluidState(below).isEmpty()
            || !level.getBlockState(below).blocksMotion()) {
            return false; // 落点必须是干燥实心地面（排除海面/湖面/悬崖）
        }
        // 3 格高的补给箱需要上方三格净空
        for (int dy = 0; dy < 3; dy++) {
            BlockPos pos = new BlockPos(bx, surface + dy, bz);
            if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) {
                return false;
            }
        }

        int heightMin = Math.max(48, SupplyBeamConfig.BEAM_HEIGHT_MIN.get());
        int heightMax = Math.max(heightMin + 8, SupplyBeamConfig.BEAM_HEIGHT_MAX.get());
        int height = heightMin + RNG.nextInt(heightMax - heightMin + 1);
        float topY = Math.min((float) level.getMaxY() - 6.0f, surface + height);
        if (topY - surface < 48.0f) {
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
