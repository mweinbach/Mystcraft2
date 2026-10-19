package com.technovision.advancedgenetics.common.block.plasmidinjector;

import com.technovision.advancedgenetics.api.blockentity.AbstractProcessingBlockEntity;
import com.technovision.advancedgenetics.api.screen.AbstractGeneticsScreenHandler;
import com.technovision.advancedgenetics.api.screen.slot.PlasmidSlot;
import com.technovision.advancedgenetics.api.screen.slot.SyringeSlot;
import com.technovision.advancedgenetics.registry.ScreenRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;

public class PlasmidInjectorScreenHandler extends AbstractGeneticsScreenHandler {

    public PlasmidInjectorScreenHandler(int syncId, Inventory playerInventory, FriendlyByteBuf buffer) {
        this(syncId, playerInventory, new SimpleContainer(PlasmidInjectorBlockEntity.SLOT_COUNT), new SimpleContainerData(AbstractProcessingBlockEntity.DATA_COUNT));
    }

    public PlasmidInjectorScreenHandler(int syncId, Inventory playerInventory, Container inventory, ContainerData delegate) {
        super(ScreenRegistry.PLASMID_INJECTOR_SCREEN_HANDLER.get(), syncId, playerInventory, inventory, delegate, 1, 1);
        addSlots(PlasmidSlot::new, inventory, 0, 1, 63, 36);
        addSlots(SyringeSlot::new, inventory, 1, 1, 110, 36);
    }
}
