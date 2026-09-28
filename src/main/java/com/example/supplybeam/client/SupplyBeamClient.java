package com.example.supplybeam.client;

import com.example.supplybeam.SupplyBeamMod;
import com.example.supplybeam.entity.SupplyBeamEntities;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@EventBusSubscriber(modid = SupplyBeamMod.MODID, value = Dist.CLIENT)
public final class SupplyBeamClient {
    private SupplyBeamClient() {}

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(SupplyBeamEntities.SUPPLY_CRATE.get(), SupplyCrateRenderer::new);
    }

    /** 注册图形配置界面：模组列表 → "配置"按钮。 */
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        ModList.get().getModContainerById(SupplyBeamMod.MODID).ifPresent(container ->
            container.registerExtensionPoint(IConfigScreenFactory.class,
                (java.util.function.Supplier<IConfigScreenFactory>)
                    () -> (modContainer, parent) -> new SupplyBeamConfigScreen(parent)));
    }
}
