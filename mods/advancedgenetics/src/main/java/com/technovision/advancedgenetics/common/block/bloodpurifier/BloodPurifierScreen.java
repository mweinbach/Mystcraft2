package com.technovision.advancedgenetics.common.block.bloodpurifier;

import com.technovision.advancedgenetics.AdvancedGenetics;
import com.technovision.advancedgenetics.api.screen.AbstractGeneticsScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public class BloodPurifierScreen extends AbstractGeneticsScreen<BloodPurifierScreenHandler> {

    private static final ResourceLocation TEXTURE = new ResourceLocation(AdvancedGenetics.MOD_ID, "textures/gui/basic_machine_gui.png");

    public BloodPurifierScreen(BloodPurifierScreenHandler handler, Inventory inventory, Component title) {
        super(handler, inventory, title, TEXTURE);
    }
}
