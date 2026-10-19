package com.technovision.advancedgenetics.common.block.dnaextractor;

import com.technovision.advancedgenetics.api.blockentity.AbstractProcessingBlockEntity;
import com.technovision.advancedgenetics.api.screen.AbstractGeneticsScreenHandler;
import com.technovision.advancedgenetics.api.screen.slot.CellSlot;
import com.technovision.advancedgenetics.api.screen.slot.OutputSlot;
import com.technovision.advancedgenetics.registry.ScreenRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;

public class DnaExtractorScreenHandler extends AbstractGeneticsScreenHandler {

    public DnaExtractorScreenHandler(int syncId, Inventory playerInventory, FriendlyByteBuf buffer) {
        this(syncId, playerInventory, new SimpleContainer(DnaExtractorBlockEntity.SLOT_COUNT), new SimpleContainerData(AbstractProcessingBlockEntity.DATA_COUNT));
    }

    public DnaExtractorScreenHandler(int syncId, Inventory playerInventory, Container inventory, ContainerData delegate) {
        super(ScreenRegistry.DNA_EXTRACTOR_SCREEN_HANDLER.get(), syncId, playerInventory, inventory, delegate, 1, 1);
        addSlots(CellSlot::new, inventory, 0, 1, 63, 36);
        addSlots(OutputSlot::new, inventory, 1, 1, 110, 36);
    }
}
