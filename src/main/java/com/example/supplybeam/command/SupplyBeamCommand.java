package com.example.supplybeam.command;

import com.example.supplybeam.entity.BeamManager;
import com.example.supplybeam.entity.SupplyBeamEntities;
import com.example.supplybeam.entity.SupplyCrateEntity;
import com.example.supplybeam.loot.SupplyRarity;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * 管理员指令（等级 2）：
 * <ul>
 *   <li>/supplybeam spawn &lt;rarity&gt; — 在准星所指位置强制生成一根光柱（调试/演出）。</li>
 *   <li>/supplybeam clear — 清除当前维度的全部补给光柱。</li>
 * </ul>
 */
public final class SupplyBeamCommand {
    private SupplyBeamCommand() {}

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("supplybeam")
            .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
            .then(Commands.literal("spawn")
                .then(Commands.argument("rarity", StringArgumentType.word())
                    .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(rarityIds(), builder))
                    .executes(ctx -> spawn(ctx, StringArgumentType.getString(ctx, "rarity")))))
            .then(Commands.literal("clear")
                .executes(SupplyBeamCommand::clear)));
    }

    private static List<String> rarityIds() {
        List<String> ids = new ArrayList<>();
        for (SupplyRarity r : SupplyRarity.VALUES) {
            ids.add(r.id());
        }
        return ids;
    }

    private static int spawn(CommandContext<CommandSourceStack> ctx, String rarityId) throws CommandSyntaxException {
        SupplyRarity rarity = SupplyRarity.byId(rarityId);
        if (rarity == null) {
            ctx.getSource().sendFailure(Component.translatable("supplybeam.command.unknown_rarity", rarityId));
            return 0;
        }
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ServerLevel level = ctx.getSource().getLevel();
        Vec3 aim = player.getEyePosition().add(player.getViewVector(1.0f).scale(24.0));
        int bx = BlockPos.containing(aim).getX();
        int bz = BlockPos.containing(aim).getZ();
        if (BeamManager.spawnAt(level, bx, bz, rarity)) {
            ctx.getSource().sendSuccess(() -> Component.translatable("supplybeam.command.spawned",
                rarityName(rarity)), true);
            return 1;
        }
        ctx.getSource().sendFailure(Component.translatable("supplybeam.command.bad_spot"));
        return 0;
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

    private static MutableComponent rarityName(SupplyRarity rarity) {
        return Component.translatable(rarity.translationKey()).withStyle(rarity.formatting());
    }
}
