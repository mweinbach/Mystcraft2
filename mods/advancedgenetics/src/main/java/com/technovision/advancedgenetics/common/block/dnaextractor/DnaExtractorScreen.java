package com.technovision.advancedgenetics.common.block.dnaextractor;

import com.technovision.advancedgenetics.AdvancedGenetics;
import com.technovision.advancedgenetics.api.screen.AbstractGeneticsScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public class DnaExtractorScreen extends AbstractGeneticsScreen<DnaExtractorScreenHandler> {

    private static final ResourceLocation TEXTURE = new ResourceLocation(AdvancedGenetics.MOD_ID, "textures/gui/dna_extractor_gui.png");

    public DnaExtractorScreen(DnaExtractorScreenHandler handler, Inventory inventory, Component title) {
        super(handler, inventory, title, TEXTURE);
    }
}
