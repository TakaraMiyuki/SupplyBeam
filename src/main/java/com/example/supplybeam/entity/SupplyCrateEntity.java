package com.example.supplybeam.entity;

import com.example.supplybeam.SupplyBeamConfig;
import com.example.supplybeam.loot.SupplyLoot;
import com.example.supplybeam.loot.SupplyRarity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * 补给光柱实体：一根光柱 + 一枚菱形八面体补给箱，共四个阶段。
 * 实体原点始终是"地面锚点"（光柱落点），补给箱渲染在原点上方 1.5 格处。
 *
 * <ul>
 *   <li>PHASE_GATHERING（0）：光柱自地面缓缓生长至顶端（补给箱未出现）。</li>
 *   <li>PHASE_DESCENDING（1）：补给箱出现在光柱正中间，沿光柱缓慢降落。</li>
 *   <li>PHASE_LANDED（2）：补给箱着陆，玩家右键拾取；名牌显示剩余时间，超时进入消散。</li>
 *   <li>PHASE_VANISHING（3）：补给箱与光柱一同缩小消散。</li>
 * </ul>
 *
 * 实体随存档持久化：远离玩家导致区块卸载后进度冻结，回到加载范围后从原进度继续。
 * 名牌仅在着陆后出现（降落途中保持隐匿），并实时显示消散倒计时。
 */
public class SupplyCrateEntity extends Entity {
    public static final byte PHASE_GATHERING = 0;
    public static final byte PHASE_DESCENDING = 1;
    public static final byte PHASE_LANDED = 2;
    public static final byte PHASE_VANISHING = 3;

    /** 补给箱中心相对实体原点的高度（= 垂直半高，落地时下顶点恰好触地）。 */
    public static final float REST_CENTER_HEIGHT = SupplyBeamConfig.CRATE_HALF_HEIGHT + 0.02f;

    public static final EntityDataAccessor<Byte> DATA_RARITY =
        SynchedEntityData.defineId(SupplyCrateEntity.class, EntityDataSerializers.BYTE);
    public static final EntityDataAccessor<Byte> DATA_PHASE =
        SynchedEntityData.defineId(SupplyCrateEntity.class, EntityDataSerializers.BYTE);
    public static final EntityDataAccessor<Float> DATA_GROUND_Y =
        SynchedEntityData.defineId(SupplyCrateEntity.class, EntityDataSerializers.FLOAT);
    public static final EntityDataAccessor<Float> DATA_TOP_Y =
        SynchedEntityData.defineId(SupplyCrateEntity.class, EntityDataSerializers.FLOAT);
    /** 进入当前阶段时的 ageTicks（持久化），客户端据此插值"生长/消散"进度。 */
    public static final EntityDataAccessor<Integer> DATA_PHASE_TICK =
        SynchedEntityData.defineId(SupplyCrateEntity.class, EntityDataSerializers.INT);
    /** 光柱颜色（0xRRGGBB，来自服务端配置并同步）。 */
    public static final EntityDataAccessor<Integer> DATA_COLOR =
        SynchedEntityData.defineId(SupplyCrateEntity.class, EntityDataSerializers.INT);
    /** 光柱生长时长（刻，来自服务端配置并同步）。 */
    public static final EntityDataAccessor<Integer> DATA_GROWTH_TICKS =
        SynchedEntityData.defineId(SupplyCrateEntity.class, EntityDataSerializers.INT);

    private static final RandomSource RNG = RandomSource.create();

    /** 实体总存活刻数（持久化；tickCount 在重载后会归零，不能用于阶段推进）。 */
    private int ageTicks;

    public SupplyCrateEntity(EntityType<? extends SupplyCrateEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        if (!level().isClientSide()) {
            BeamManager.add(this);
        }
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_RARITY, (byte) 0);
        builder.define(DATA_PHASE, PHASE_GATHERING);
        builder.define(DATA_GROUND_Y, (float) getY());
        builder.define(DATA_TOP_Y, (float) getY() + 147.0f);
        builder.define(DATA_PHASE_TICK, 0);
        builder.define(DATA_COLOR, SupplyRarity.COMMON.defaultColor());
        builder.define(DATA_GROWTH_TICKS, 400);
    }

    // ==================== 同步数据访问 ====================

    public SupplyRarity rarity() {
        return SupplyRarity.VALUES[Math.floorMod(this.entityData.get(DATA_RARITY), SupplyRarity.VALUES.length)];
    }

    public void setRarity(SupplyRarity rarity) {
        this.entityData.set(DATA_RARITY, (byte) rarity.ordinal());
    }

    /** 光柱颜色（0xRRGGBB）。 */
    public int color() {
        return this.entityData.get(DATA_COLOR);
    }

    public void setColor(int color) {
        this.entityData.set(DATA_COLOR, color & 0xFFFFFF);
    }

    public int growthTicks() {
        return Math.max(1, this.entityData.get(DATA_GROWTH_TICKS));
    }

    public void setGrowthTicks(int ticks) {
        this.entityData.set(DATA_GROWTH_TICKS, Math.max(1, ticks));
    }

    public byte phase() {
        return this.entityData.get(DATA_PHASE);
    }

    public float groundY() {
        return this.entityData.get(DATA_GROUND_Y);
    }

    public void setGroundY(float y) {
        this.entityData.set(DATA_GROUND_Y, y);
    }

    public float topY() {
        return this.entityData.get(DATA_TOP_Y);
    }

    public void setTopY(float y) {
        this.entityData.set(DATA_TOP_Y, y);
    }

    public int phaseTick() {
        return this.entityData.get(DATA_PHASE_TICK);
    }

    private void setPhase(byte phase) {
        this.entityData.set(DATA_PHASE, phase);
        this.entityData.set(DATA_PHASE_TICK, this.ageTicks);
    }

    @Override
    public void onRemovedFromLevel() {
        super.onRemovedFromLevel();
        if (!level().isClientSide()) {
            BeamManager.remove(this);
        }
    }

    // ==================== 行为 ====================

    /** 名牌仅在着陆后显示（降落途中隐匿）。 */
    @Override
    public boolean shouldShowName() {
        return phase() == PHASE_LANDED && this.getCustomName() != null;
    }

    /** 事件实体不可被伤害。 */
    @Override
    public boolean hurtServer(ServerLevel server, DamageSource source, float amount) {
        return false;
    }

    /** 降落与着陆阶段可被准星选中（右键拾取），显现阶段保持不可选。 */
    @Override
    public boolean isPickable() {
        byte p = phase();
        return p == PHASE_DESCENDING || p == PHASE_LANDED;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) {
            return; // 运动全部由服务端驱动，客户端通过插值跟随
        }
        this.ageTicks++;
        switch (phase()) {
            case PHASE_GATHERING -> tickGathering();
            case PHASE_DESCENDING -> tickDescending();
            case PHASE_LANDED -> tickLanded();
            case PHASE_VANISHING -> tickVanishing();
            default -> startVanishing();
        }
    }

    /** 阶段一：光柱自地面生长。完全显现后补给箱出现在光柱正中间。 */
    private void tickGathering() {
        if (this.ageTicks - phaseTick() >= growthTicks()) {
            setPhase(PHASE_DESCENDING);
            double midY = (groundY() + topY()) / 2.0;
            setPos(getX(), midY - REST_CENTER_HEIGHT, getZ());
            level().playSound(null, blockPosition(), SoundEvents.ENDER_EYE_LAUNCH,
                SoundSource.NEUTRAL, 1.0f, 0.8f);
            if (level() instanceof ServerLevel server) {
                server.sendParticles(ParticleTypes.END_ROD,
                    getX(), midY + 1.2, getZ(), 10, 0.3, 0.3, 0.3, 0.02);
            }
            return;
        }
        if (this.ageTicks % 12 == 0) {
            spawnRingDust(3, 1.4);
        }
    }

    /** 阶段二：补给箱沿光柱缓慢降落。 */
    private void tickDescending() {
        double speedPerTick = SupplyBeamConfig.DESCEND_SPEED_BLOCKS_PER_SECOND.get() / 20.0;
        double nextY = getY() - speedPerTick;
        if (nextY <= groundY()) {
            land();
            return;
        }
        setPos(getX(), nextY, getZ());
        if (this.ageTicks % 4 == 0 && level() instanceof ServerLevel server) {
            server.sendParticles(dustOf(), getX(), getY() + REST_CENTER_HEIGHT, getZ(),
                2, 0.4, 0.6, 0.4, 0.01);
        }
    }

    /** 着陆：光柱保持引导，名牌亮出并开始倒计时。 */
    private void land() {
        setPos(getX(), groundY(), getZ());
        setPhase(PHASE_LANDED);
        refreshName();
        level().playSound(null, blockPosition(), SoundEvents.BEACON_ACTIVATE,
            SoundSource.NEUTRAL, 1.0f, 0.55f);
        if (level() instanceof ServerLevel server) {
            DustParticleOptions dust = dustOf();
            for (int i = 0; i < 24; i++) {
                double angle = RNG.nextDouble() * Math.PI * 2;
                double r = 1.0 + RNG.nextDouble() * 1.4;
                server.sendParticles(dust,
                    getX() + Math.cos(angle) * r, getY() + 0.15, getZ() + Math.sin(angle) * r,
                    1, 0.05, 0.05, 0.05, 0.01);
            }
            server.sendParticles(ParticleTypes.CLOUD, getX(), getY() + 0.2, getZ(),
                10, 0.5, 0.05, 0.5, 0.01);
        }
    }

    /** 阶段三：已着陆，等待拾取；名牌实时显示剩余时间，超时进入消散。 */
    private void tickLanded() {
        if (this.ageTicks % 20 == 0) {
            refreshName();
        }
        if (this.ageTicks % 16 == 0) {
            spawnRingDust(4, 1.8);
        }
        if (this.ageTicks - phaseTick() >= lifetimeTicks()) {
            setPhase(PHASE_VANISHING);
        }
    }

    /** 阶段四：无人拾取，自行消散。 */
    private void tickVanishing() {
        if (this.ageTicks - phaseTick() >= SupplyBeamConfig.VANISH_TICKS) {
            if (level() instanceof ServerLevel server) {
                server.sendParticles(ParticleTypes.POOF, getX(), getY() + 1.5, getZ(),
                    14, 0.6, 0.9, 0.6, 0.02);
                server.sendParticles(dustOf(), getX(), getY() + 1.5, getZ(),
                    12, 0.7, 1.0, 0.7, 0.02);
            }
            level().playSound(null, blockPosition(), SoundEvents.BEACON_DEACTIVATE,
                SoundSource.NEUTRAL, 0.8f, 0.9f);
            discard();
        }
    }

    private void startVanishing() {
        setPhase(PHASE_VANISHING);
    }

    /** 玩家右键拾取：结算战利品并撒落在补给箱周围。 */
    @Override
    public InteractionResult interact(Player player, net.minecraft.world.InteractionHand hand, Vec3 location) {
        if (phase() != PHASE_LANDED) {
            return InteractionResult.PASS;
        }
        if (level().isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer serverPlayer && level() instanceof ServerLevel server) {
            collect(server, serverPlayer);
        }
        return InteractionResult.SUCCESS;
    }

    private void collect(ServerLevel server, ServerPlayer player) {
        List<ItemStack> loot = SupplyLoot.rollLoot(rarity(), server.registryAccess());
        for (ItemStack stack : loot) {
            double ox = (RNG.nextDouble() - 0.5) * 2.0;
            double oz = (RNG.nextDouble() - 0.5) * 2.0;
            ItemEntity item = new ItemEntity(server, getX() + ox, getY() + 1.0, getZ() + oz, stack);
            item.setPickUpDelay(8);
            server.addFreshEntity(item);
        }
        level().playSound(null, blockPosition(), SoundEvents.PLAYER_LEVELUP,
            SoundSource.NEUTRAL, 0.9f, 1.0f);
        level().playSound(null, blockPosition(), SoundEvents.ITEM_PICKUP,
            SoundSource.NEUTRAL, 0.8f, 0.7f);
        server.sendParticles(dustOf(), getX(), getY() + 1.5, getZ(), 28, 0.8, 1.0, 0.8, 0.05);
        server.sendParticles(ParticleTypes.END_ROD, getX(), getY() + 1.5, getZ(),
            10, 0.6, 0.9, 0.6, 0.06);
        discard();
    }

    // ==================== 名牌 ====================

    /** 名牌：如 "史诗 · 空投补给箱（4:32后消散）"，着陆后每秒刷新。 */
    private void refreshName() {
        int remainingTicks = Math.max(0, lifetimeTicks() - (this.ageTicks - phaseTick()));
        int seconds = (remainingTicks + 19) / 20;
        String time = seconds / 60 + ":" + String.format("%02d", seconds % 60);
        MutableComponent rarityPart = Component.translatable(rarity().translationKey())
            .withStyle(rarity().formatting());
        MutableComponent timerPart = Component.translatable("supplybeam.crate.timer", time)
            .withStyle(ChatFormatting.GRAY);
        setCustomName(Component.translatable("supplybeam.crate.name_timed", rarityPart, timerPart));
    }

    private int lifetimeTicks() {
        return Math.max(1, SupplyBeamConfig.GROUND_LIFETIME_SECONDS.get() * 20);
    }

    // ==================== 持久化 ====================

    @Override
    protected void readAdditionalSaveData(net.minecraft.world.level.storage.ValueInput input) {
        this.ageTicks = input.getIntOr("AgeTicks", 0);
        this.entityData.set(DATA_RARITY, input.getByteOr("Rarity", (byte) 0));
        this.entityData.set(DATA_PHASE, input.getByteOr("Phase", PHASE_GATHERING));
        this.entityData.set(DATA_PHASE_TICK, input.getIntOr("PhaseTick", 0));
        this.entityData.set(DATA_GROUND_Y, input.getFloatOr("GroundY", (float) getY()));
        this.entityData.set(DATA_TOP_Y, input.getFloatOr("TopY", (float) getY() + 147.0f));
        this.entityData.set(DATA_COLOR, input.getIntOr("Color", rarity().defaultColor()));
        this.entityData.set(DATA_GROWTH_TICKS, input.getIntOr("GrowthTicks", 400));
        if (phase() == PHASE_LANDED) {
            refreshName();
        }
    }

    @Override
    protected void addAdditionalSaveData(net.minecraft.world.level.storage.ValueOutput output) {
        output.putInt("AgeTicks", this.ageTicks);
        output.putByte("Rarity", (byte) rarity().ordinal());
        output.putByte("Phase", phase());
        output.putInt("PhaseTick", phaseTick());
        output.putFloat("GroundY", groundY());
        output.putFloat("TopY", topY());
        output.putInt("Color", color());
        output.putInt("GrowthTicks", growthTicks());
    }

    // ==================== 工具 ====================

    private DustParticleOptions dustOf() {
        return new DustParticleOptions(color(), 1.4f);
    }

    /** 地面环形尘埃：光柱根部与着陆点的呼吸效果。 */
    private void spawnRingDust(int count, double radius) {
        if (!(level() instanceof ServerLevel server)) {
            return;
        }
        DustParticleOptions dust = dustOf();
        for (int i = 0; i < count; i++) {
            double angle = RNG.nextDouble() * Math.PI * 2;
            double r = radius * (0.75 + RNG.nextDouble() * 0.25);
            server.sendParticles(dust,
                getX() + Math.cos(angle) * r, getY() + 0.1, getZ() + Math.sin(angle) * r,
                1, 0.02, 0.03, 0.02, 0.005);
        }
        if (RNG.nextInt(3) == 0) {
            double angle = RNG.nextDouble() * Math.PI * 2;
            server.sendParticles(ParticleTypes.END_ROD,
                getX() + Math.cos(angle) * radius * 0.6, getY() + 0.3, getZ() + Math.sin(angle) * radius * 0.6,
                1, 0.0, 0.04, 0.0, 0.0);
        }
    }
}
