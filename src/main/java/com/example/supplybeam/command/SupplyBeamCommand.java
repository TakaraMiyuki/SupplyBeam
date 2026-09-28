package com.example.supplybeam.command;

import com.example.supplybeam.SupplyBeamConfig;
import com.example.supplybeam.entity.BeamManager;
import com.example.supplybeam.entity.SupplyBeamEntities;
import com.example.supplybeam.entity.SupplyCrateEntity;
import com.example.supplybeam.loot.SupplyRarity;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * 指令（权限等级 2）：
 *
 * <pre>
 * /supplybeam spawn &lt;rarity&gt;          在准星所指位置生成光柱
 * /supplybeam clear                   清除当前维度全部光柱
 * /supplybeam info                    查看运行状态（活跃数/下次刷新/调试开关）
 * /supplybeam debug &lt;on|off&gt;          开关调试模式（写入配置文件）
 * /supplybeam config list             列出全部配置项与当前值
 * /supplybeam config set &lt;key&gt; &lt;value&gt;  热更新配置并写入文件
 * /supplybeam config reset &lt;key&gt;      恢复某项默认值
 * —— 以下需调试模式 ——
 * /supplybeam spawnhere &lt;rarity&gt;      在脚下生成光柱
 * /supplybeam list                    列出当前维度的光柱明细
 * /supplybeam fastforward             准星光柱直接跳到下一阶段
 * /supplybeam timer &lt;seconds&gt;         设置准星光柱的消散倒计时
 * </pre>
 */
public final class SupplyBeamCommand {
    private SupplyBeamCommand() {}

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("supplybeam")
            .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
            .then(Commands.literal("spawn")
                .then(Commands.argument("rarity", StringArgumentType.word())
                    .suggests((ctx, b) -> SharedSuggestionProvider.suggest(rarityIds(), b))
                    .executes(ctx -> spawn(ctx, StringArgumentType.getString(ctx, "rarity")))))
            .then(Commands.literal("clear")
                .executes(SupplyBeamCommand::clear))
            .then(Commands.literal("info")
                .executes(SupplyBeamCommand::info))
            .then(Commands.literal("debug")
                .then(Commands.argument("mode", StringArgumentType.word())
                    .suggests((ctx, b) -> SharedSuggestionProvider.suggest(List.of("on", "off"), b))
                    .executes(ctx -> toggleDebug(ctx, StringArgumentType.getString(ctx, "mode")))))
            .then(Commands.literal("config")
                .then(Commands.literal("list")
                    .executes(SupplyBeamCommand::configList))
                .then(Commands.literal("set")
                    .then(Commands.argument("key", StringArgumentType.word())
                        .suggests((ctx, b) -> SharedSuggestionProvider.suggest(configKeys(), b))
                        .then(Commands.argument("value", StringArgumentType.greedyString())
                            .executes(ctx -> configSet(ctx,
                                StringArgumentType.getString(ctx, "key"),
                                StringArgumentType.getString(ctx, "value"))))))
                .then(Commands.literal("reset")
                    .then(Commands.argument("key", StringArgumentType.word())
                        .suggests((ctx, b) -> SharedSuggestionProvider.suggest(configKeys(), b))
                        .executes(ctx -> configReset(ctx, StringArgumentType.getString(ctx, "key"))))))
            .then(Commands.literal("spawnhere")
                .then(Commands.argument("rarity", StringArgumentType.word())
                    .suggests((ctx, b) -> SharedSuggestionProvider.suggest(rarityIds(), b))
                    .executes(ctx -> spawnHere(ctx, StringArgumentType.getString(ctx, "rarity")))))
            .then(Commands.literal("list")
                .executes(SupplyBeamCommand::listBeams))
            .then(Commands.literal("fastforward")
                .executes(SupplyBeamCommand::fastForward))
            .then(Commands.literal("timer")
                .then(Commands.argument("seconds", IntegerArgumentType.integer(1))
                    .executes(ctx -> setTimer(ctx, IntegerArgumentType.getInteger(ctx, "seconds"))))));
    }

    // ==================== 基础指令 ====================

    private static int spawn(CommandContext<CommandSourceStack> ctx, String rarityId) throws CommandSyntaxException {
        SupplyRarity rarity = SupplyRarity.byId(rarityId);
        if (rarity == null) {
            ctx.getSource().sendFailure(Component.translatable("supplybeam.command.unknown_rarity", rarityId));
            return 0;
        }
        ServerLevel level = ctx.getSource().getLevel();
        Vec3 eye = eyePos(ctx.getSource());
        Vec3 aim = aimDirection(ctx.getSource());
        Vec3 target = eye.add(aim.scale(24.0));
        int bx = BlockPos.containing(target).getX();
        int bz = BlockPos.containing(target).getZ();
        return finishSpawn(ctx, level, bx, bz, rarity);
    }

    private static int clear(CommandContext<CommandSourceStack> ctx) {
        ServerLevel level = ctx.getSource().getLevel();
        List<? extends SupplyCrateEntity> list = level.getEntities(SupplyBeamEntities.SUPPLY_CRATE.get(), e -> true);
        for (SupplyCrateEntity crate : list) {
            crate.discard();
        }
        final int count = list.size();
        ctx.getSource().sendSuccess(() -> Component.translatable("supplybeam.command.cleared", count), true);
        return count;
    }

    private static int info(CommandContext<CommandSourceStack> ctx) {
        ServerLevel level = ctx.getSource().getLevel();
        long inLevel = level.getEntities(SupplyBeamEntities.SUPPLY_CRATE.get(), e -> true).size();
        ctx.getSource().sendSuccess(() -> Component.translatable("supplybeam.command.info",
            BeamManager.activeCount(), inLevel, BeamManager.nextSpawnSeconds(),
            SupplyBeamConfig.debugMode()
                ? Component.translatable("supplybeam.command.debug_on").withStyle(net.minecraft.ChatFormatting.GREEN)
                : Component.translatable("supplybeam.command.debug_off").withStyle(net.minecraft.ChatFormatting.GRAY)), false);
        return 1;
    }

    private static int toggleDebug(CommandContext<CommandSourceStack> ctx, String mode) {
        Boolean on = switch (mode.toLowerCase()) {
            case "on", "true", "1" -> true;
            case "off", "false", "0" -> false;
            default -> null;
        };
        if (on == null) {
            ctx.getSource().sendFailure(Component.translatable("supplybeam.command.debug_usage"));
            return 0;
        }
        SupplyBeamConfig.applyValue(SupplyBeamConfig.entry("debug.debugMode"), on ? "true" : "false");
        final boolean nowOn = on;
        ctx.getSource().sendSuccess(() -> Component.translatable("supplybeam.command.debug_toggled",
            nowOn ? Component.translatable("supplybeam.command.debug_on").withStyle(net.minecraft.ChatFormatting.GREEN)
                : Component.translatable("supplybeam.command.debug_off").withStyle(net.minecraft.ChatFormatting.GRAY)), true);
        return 1;
    }

    // ==================== 配置指令 ====================

    private static int configList(CommandContext<CommandSourceStack> ctx) {
        for (SupplyBeamConfig.ConfigEntry e : SupplyBeamConfig.entries()) {
            ctx.getSource().sendSuccess(() -> Component.translatable("supplybeam.command.config_line",
                Component.literal(e.key()).withStyle(net.minecraft.ChatFormatting.AQUA),
                Component.literal(SupplyBeamConfig.valueAsString(e)).withStyle(net.minecraft.ChatFormatting.YELLOW),
                Component.translatable(e.labelKey())), false);
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("supplybeam.command.config_hint"), false);
        return SupplyBeamConfig.entries().size();
    }

    private static int configSet(CommandContext<CommandSourceStack> ctx, String key, String value) {
        SupplyBeamConfig.ConfigEntry entry = SupplyBeamConfig.entry(key);
        if (entry == null) {
            ctx.getSource().sendFailure(Component.translatable("supplybeam.command.config_unknown_key", key));
            return 0;
        }
        String error = SupplyBeamConfig.applyValue(entry, value);
        if (error != null) {
            ctx.getSource().sendFailure(Component.translatable("supplybeam.command.config_invalid", key, error));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("supplybeam.command.config_set",
            Component.literal(key).withStyle(net.minecraft.ChatFormatting.AQUA),
            Component.literal(SupplyBeamConfig.valueAsString(entry)).withStyle(net.minecraft.ChatFormatting.YELLOW)), true);
        return 1;
    }

    private static int configReset(CommandContext<CommandSourceStack> ctx, String key) {
        SupplyBeamConfig.ConfigEntry entry = SupplyBeamConfig.entry(key);
        if (entry == null) {
            ctx.getSource().sendFailure(Component.translatable("supplybeam.command.config_unknown_key", key));
            return 0;
        }
        SupplyBeamConfig.resetValue(entry);
        ctx.getSource().sendSuccess(() -> Component.translatable("supplybeam.command.config_reset",
            Component.literal(key).withStyle(net.minecraft.ChatFormatting.AQUA),
            Component.literal(SupplyBeamConfig.valueAsString(entry)).withStyle(net.minecraft.ChatFormatting.YELLOW)), true);
        return 1;
    }

    // ==================== 调试指令 ====================

    private static int spawnHere(CommandContext<CommandSourceStack> ctx, String rarityId) throws CommandSyntaxException {
        if (!requireDebug(ctx.getSource())) {
            return 0;
        }
        SupplyRarity rarity = SupplyRarity.byId(rarityId);
        if (rarity == null) {
            ctx.getSource().sendFailure(Component.translatable("supplybeam.command.unknown_rarity", rarityId));
            return 0;
        }
        ServerLevel level = ctx.getSource().getLevel();
        Vec3 pos = ctx.getSource().getPosition();
        int bx = BlockPos.containing(pos).getX();
        int bz = BlockPos.containing(pos).getZ();
        return finishSpawn(ctx, level, bx, bz, rarity);
    }

    private static int listBeams(CommandContext<CommandSourceStack> ctx) {
        if (!requireDebug(ctx.getSource())) {
            return 0;
        }
        ServerLevel level = ctx.getSource().getLevel();
        List<? extends SupplyCrateEntity> crates = level.getEntities(SupplyBeamEntities.SUPPLY_CRATE.get(), e -> true);
        ctx.getSource().sendSuccess(() -> Component.translatable("supplybeam.command.list_header", crates.size()), false);
        int shown = 0;
        for (SupplyCrateEntity crate : crates) {
            if (shown++ >= 20) {
                break;
            }
            MutableComponent rarity = Component.translatable(crate.rarity().translationKey())
                .withStyle(crate.rarity().formatting());
            final int remaining = crate.debugRemainingSeconds();
            final String phase = Component.translatable(crate.phaseKey()).getString();
            final String time = remaining < 0 ? "-" : remaining / 60 + ":" + String.format("%02d", remaining % 60);
            ctx.getSource().sendSuccess(() -> Component.translatable("supplybeam.command.list_line",
                crate.blockPosition().getX(), crate.blockPosition().getZ(), (int) crate.getY(),
                rarity, phase, time), false);
        }
        return crates.size();
    }

    private static int fastForward(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        if (!requireDebug(ctx.getSource())) {
            return 0;
        }
        SupplyCrateEntity crate = aimedCrate(ctx.getSource());
        if (crate == null) {
            ctx.getSource().sendFailure(Component.translatable("supplybeam.command.no_crate"));
            return 0;
        }
        final String before = crate.phaseKey();
        crate.debugSkipPhase();
        final String after = crate.phaseKey();
        ctx.getSource().sendSuccess(() -> Component.translatable("supplybeam.command.fastforward",
            Component.translatable(before), Component.translatable(after)), true);
        return 1;
    }

    private static int setTimer(CommandContext<CommandSourceStack> ctx, int seconds) throws CommandSyntaxException {
        if (!requireDebug(ctx.getSource())) {
            return 0;
        }
        SupplyCrateEntity crate = aimedCrate(ctx.getSource());
        if (crate == null) {
            ctx.getSource().sendFailure(Component.translatable("supplybeam.command.no_crate"));
            return 0;
        }
        if (!crate.debugSetRemainingSeconds(seconds)) {
            ctx.getSource().sendFailure(Component.translatable("supplybeam.command.timer_needs_landed"));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("supplybeam.command.timer_set", seconds), true);
        return 1;
    }

    // ==================== 工具 ====================

    private static int finishSpawn(CommandContext<CommandSourceStack> ctx, ServerLevel level, int bx, int bz,
                                   SupplyRarity rarity) {
        if (BeamManager.spawnAt(level, bx, bz, rarity)) {
            ctx.getSource().sendSuccess(() -> Component.translatable("supplybeam.command.spawned",
                Component.translatable(rarity.translationKey()).withStyle(rarity.formatting())), true);
            return 1;
        }
        ctx.getSource().sendFailure(Component.translatable("supplybeam.command.bad_spot"));
        return 0;
    }

    private static boolean requireDebug(CommandSourceStack source) {
        if (!SupplyBeamConfig.debugMode()) {
            source.sendFailure(Component.translatable("supplybeam.command.debug_required"));
            return false;
        }
        return true;
    }

    /** 眼睛位置：玩家取眼睛，RCON/控制台取源位置。 */
    private static Vec3 eyePos(CommandSourceStack source) {
        return source.getEntity() instanceof ServerPlayer player ? player.getEyePosition() : source.getPosition();
    }

    /** 观察方向：玩家取视线，RCON/控制台取源朝向。 */
    private static Vec3 aimDirection(CommandSourceStack source) {
        Vec3 view = source.getEntity() instanceof ServerPlayer player
            ? player.getViewVector(1.0f)
            : Vec3.directionFromRotation(source.getRotation());
        return view.lengthSqr() < 1.0E-6 ? new Vec3(0, 0, 1) : view.normalize();
    }

    /**
     * 解析"准星光柱"：优先取视线夹角最小且在视锥内的光柱，
     * 找不到时回退为 24 格内最近的光柱。
     */
    private static SupplyCrateEntity aimedCrate(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        List<? extends SupplyCrateEntity> crates = level.getEntities(SupplyBeamEntities.SUPPLY_CRATE.get(), e -> true);
        if (crates.isEmpty()) {
            return null;
        }
        Vec3 eye = eyePos(source);
        Vec3 view = aimDirection(source);
        SupplyCrateEntity best = null;
        double bestPerp = 3.0; // 视线容差（格）
        for (SupplyCrateEntity crate : crates) {
            Vec3 toCenter = new Vec3(crate.getX(), crate.getY() + 1.5, crate.getZ()).subtract(eye);
            double along = toCenter.dot(view);
            if (along <= 0) {
                continue;
            }
            double perp = toCenter.subtract(view.scale(along)).length();
            if (perp < bestPerp) {
                bestPerp = perp;
                best = crate;
            }
        }
        if (best != null) {
            return best;
        }
        SupplyCrateEntity nearest = null;
        double nearestDist = 24.0 * 24.0;
        for (SupplyCrateEntity crate : crates) {
            double d = crate.position().distanceToSqr(eye);
            if (d < nearestDist) {
                nearestDist = d;
                nearest = crate;
            }
        }
        return nearest;
    }

    private static List<String> rarityIds() {
        List<String> ids = new ArrayList<>();
        for (SupplyRarity r : SupplyRarity.VALUES) {
            ids.add(r.id());
        }
        return ids;
    }

    private static List<String> configKeys() {
        List<String> keys = new ArrayList<>();
        for (SupplyBeamConfig.ConfigEntry e : SupplyBeamConfig.entries()) {
            keys.add(e.key());
        }
        return keys;
    }
}
