package com.example.supplybeam.entity;

import com.example.supplybeam.SupplyBeamMod;
import com.example.supplybeam.entity.SupplyCrateEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public final class SupplyBeamEntities {
    private SupplyBeamEntities() {}

    public static final DeferredRegister<EntityType<?>> ENTITIES =
        DeferredRegister.create(Registries.ENTITY_TYPE, SupplyBeamMod.MODID);

    public static final Supplier<EntityType<SupplyCrateEntity>> SUPPLY_CRATE =
        ENTITIES.register("supply_crate", () -> EntityType.Builder.of(SupplyCrateEntity::new, MobCategory.MISC)
            .sized(2.6f, 3.0f)
            .clientTrackingRange(16)
            .updateInterval(1)
            .build(ResourceKey.create(Registries.ENTITY_TYPE,
                Identifier.fromNamespaceAndPath(SupplyBeamMod.MODID, "supply_crate"))));
}
