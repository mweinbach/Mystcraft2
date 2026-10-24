/*
 * ProjectE 1.19.2 port, adapted from the Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.lib.event.entity;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import ports.projecte.lib.eventbus.api.Event;

/**
 * Registers default attributes for modded living entities, through Fabric's attribute registry.
 */
public class EntityAttributeCreationEvent extends Event {

    public void put(EntityType<? extends LivingEntity> entity, AttributeSupplier map) {
        FabricDefaultAttributeRegistry.register(entity, map);
    }
}
