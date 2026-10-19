package com.technovision.advancedgenetics.common.block.plasmidinjector;

import com.technovision.advancedgenetics.AdvancedGenetics;
import com.technovision.advancedgenetics.api.screen.AbstractGeneticsScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public class PlasmidInjectorScreen extends AbstractGeneticsScreen<PlasmidInjectorScreenHandler> {

    private static final ResourceLocation TEXTURE = new ResourceLocation(AdvancedGenetics.MOD_ID, "textures/gui/plasmid_injector_gui.png");

    public PlasmidInjectorScreen(PlasmidInjectorScreenHandler handler, Inventory inventory, Component title) {
        super(handler, inventory, title, TEXTURE);
    }
}
