package com.technovision.advancedgenetics.common.block.plasmidinfuser;

import com.technovision.advancedgenetics.api.blockentity.AbstractProcessingBlockEntity;
import com.technovision.advancedgenetics.api.screen.AbstractGeneticsScreenHandler;
import com.technovision.advancedgenetics.api.screen.slot.DnaSlot;
import com.technovision.advancedgenetics.api.screen.slot.PlasmidSlot;
import com.technovision.advancedgenetics.registry.ScreenRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;

public class PlasmidInfuserScreenHandler extends AbstractGeneticsScreenHandler {

    public PlasmidInfuserScreenHandler(int syncId, Inventory playerInventory, FriendlyByteBuf buffer) {
        this(syncId, playerInventory, new SimpleContainer(PlasmidInfuserBlockEntity.SLOT_COUNT), new SimpleContainerData(AbstractProcessingBlockEntity.DATA_COUNT));
    }

    public PlasmidInfuserScreenHandler(int syncId, Inventory playerInventory, Container inventory, ContainerData delegate) {
        super(ScreenRegistry.PLASMID_INFUSER_SCREEN_HANDLER.get(), syncId, playerInventory, inventory, delegate, 1, 1);
        addSlots(DnaSlot::new, inventory, 0, 1, 63, 36);
        addSlots(PlasmidSlot::new, inventory, 1, 1, 110, 36);
    }
}
