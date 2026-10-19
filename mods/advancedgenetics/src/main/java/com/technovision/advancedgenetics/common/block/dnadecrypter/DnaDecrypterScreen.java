package com.technovision.advancedgenetics.common.block.dnadecrypter;

import com.technovision.advancedgenetics.AdvancedGenetics;
import com.technovision.advancedgenetics.api.screen.AbstractGeneticsScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public class DnaDecrypterScreen extends AbstractGeneticsScreen<DnaDecrypterScreenHandler> {

    private static final ResourceLocation TEXTURE = new ResourceLocation(AdvancedGenetics.MOD_ID, "textures/gui/dna_decrypter_gui.png");

    public DnaDecrypterScreen(DnaDecrypterScreenHandler handler, Inventory inventory, Component title) {
        super(handler, inventory, title, TEXTURE);
    }
}
