package com.example.supplybeam.entity;

import com.example.supplybeam.SupplyBeamConfig;
import com.example.supplybeam.compat.ManhuntBridge;
import com.example.supplybeam.loot.SupplyRarity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 补给光柱生成调度：周期性在主世界随机在线玩家附近的地表刷新光柱，
 * 维护全服活跃数量上限。选址要求：地表足够高、落点为实心地面且无液体。
 */
public final class BeamManager {
    private BeamManager() {}

    private static final RandomSource RNG = RandomSource.create();
    private static final Set<UUID> ACTIVE = new HashSet<>();

    private static int nextSpawnTick = SupplyBeamConfig.FIRST_SPAWN_DELAY_TICKS;

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
        int min = SupplyBeamConfig.SPAWN_INTERVAL_MIN_TICKS;
        int max = SupplyBeamConfig.SPAWN_INTERVAL_MAX_TICKS;
        nextSpawnTick = min + RNG.nextInt(Math.max(1, max - min));
    }

    private static void trySpawn(ServerLevel level) {
        if (ACTIVE.size() >= SupplyBeamConfig.MAX_ACTIVE) {
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
        double dist = SupplyBeamConfig.SPAWN_RADIUS_MIN
            + RNG.nextDouble() * (SupplyBeamConfig.SPAWN_RADIUS_MAX - SupplyBeamConfig.SPAWN_RADIUS_MIN);
        int bx = BlockPos.containing(anchor.getX() + Math.cos(angle) * dist, 0, anchor.getZ()).getX();
        int bz = BlockPos.containing(anchor.getX(), 0, anchor.getZ() + Math.sin(angle) * dist).getZ();
        SupplyRarity rarity = SupplyRarity.weighted(RNG);
        spawnAt(level, bx, bz, rarity);
    }

    /**
     * 在指定地表坐标生成一根补给光柱。选址无效（海洋/深谷/悬崖边）时静默放弃本次机会。
     * @return 是否生成成功
     */
    public static boolean spawnAt(ServerLevel level, int bx, int bz, SupplyRarity rarity) {
        int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz);
        if (surface < SupplyBeamConfig.MIN_SURFACE_Y) {
            return false;
        }
        BlockPos below = new BlockPos(bx, surface - 1, bz);
        if (!level.getFluidState(below).isEmpty()
            || !level.getBlockState(below).blocksMotion()) {
            return false; // 落点必须是干燥实心地面（排除海面/湖面/悬崖）
        }
        BlockPos topPos = new BlockPos(bx, surface, bz);
        if (!level.getBlockState(topPos).getCollisionShape(level, topPos).isEmpty()) {
            return false; // 落点被方块占据（树冠/建筑）
        }

        int height = SupplyBeamConfig.BEAM_HEIGHT_MIN
            + RNG.nextInt(Math.max(1, SupplyBeamConfig.BEAM_HEIGHT_MAX - SupplyBeamConfig.BEAM_HEIGHT_MIN));
        float topY = Math.min((float) level.getMaxY() - 6.0f, surface + height);
        if (topY < surface + 16) {
            return false;
        }

        SupplyCrateEntity crate = new SupplyCrateEntity(SupplyBeamEntities.SUPPLY_CRATE.get(), level);
        crate.snapTo(bx + 0.5, surface, bz + 0.5, RNG.nextFloat() * 360.0f, 0.0f);
        crate.setRarity(rarity);
        crate.setGroundY(surface);
        crate.setTopY(topY);
        crate.setCustomName(buildName(rarity));
        crate.setCustomNameVisible(true);
        level.addFreshEntity(crate);

        level.playSound(null, bx, surface, bz, SoundEvents.BEACON_ACTIVATE,
            SoundSource.NEUTRAL, 0.7f, 0.6f);
        announce(level, bx, bz, rarity);
        return true;
    }

    /** 出现提示：128 格内的玩家在快捷栏上方收到稀有度着色的提示。 */
    private static void announce(ServerLevel level, int bx, int bz, SupplyRarity rarity) {
        MutableComponent rarityPart = Component.translatable(rarity.translationKey())
            .withStyle(rarity.formatting());
        Component message = Component.translatable("supplybeam.notice.spawn", rarityPart)
            .withStyle(ChatFormatting.GRAY);
        for (ServerPlayer player : level.players()) {
            double dx = player.getX() - bx - 0.5;
            double dz = player.getZ() - bz - 0.5;
            if (dx * dx + dz * dz <= 128.0 * 128.0) {
                player.sendOverlayMessage(message);
            }
        }
    }

    /** 名牌：如 "史诗 · 空投补给箱"。 */
    private static Component buildName(SupplyRarity rarity) {
        MutableComponent rarityPart = Component.translatable(rarity.translationKey())
            .withStyle(rarity.formatting());
        return Component.translatable("supplybeam.crate.name", rarityPart);
    }
}
