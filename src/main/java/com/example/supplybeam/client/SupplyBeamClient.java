package com.example.supplybeam.client;

import com.example.supplybeam.SupplyBeamMod;
import com.example.supplybeam.entity.SupplyBeamEntities;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid = SupplyBeamMod.MODID, value = Dist.CLIENT)
public final class SupplyBeamClient {
    private SupplyBeamClient() {}

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(SupplyBeamEntities.SUPPLY_CRATE.get(), SupplyCrateRenderer::new);
    }
}
