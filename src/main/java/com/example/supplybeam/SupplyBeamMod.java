package com.example.supplybeam;

import com.example.supplybeam.command.SupplyBeamCommand;
import com.example.supplybeam.entity.BeamManager;
import com.example.supplybeam.entity.SupplyBeamEntities;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 补给光柱（Supply Beam）：灵感来自方舟：生存飞升的空投光柱补给。
 * 主世界随机刷新彩色补给光柱（颜色=稀有度），光柱缓缓显现后，
 * 一枚菱形八面体补给箱沿光柱自天而降，落地可拾取，超时消散。
 * 与猎人游戏 Manhunt 可选联动（逃脱窗口期暂停刷新）。
 */
@Mod(SupplyBeamMod.MODID)
public final class SupplyBeamMod {
    public static final String MODID = "supplybeam";
    public static final Logger LOGGER = LoggerFactory.getLogger("SupplyBeam");

    public SupplyBeamMod(IEventBus modEventBus) {
        SupplyBeamEntities.ENTITIES.register(modEventBus);
        NeoForge.EVENT_BUS.addListener(BeamManager::onServerTick);
        NeoForge.EVENT_BUS.addListener(SupplyBeamCommand::onRegisterCommands);
    }
}
