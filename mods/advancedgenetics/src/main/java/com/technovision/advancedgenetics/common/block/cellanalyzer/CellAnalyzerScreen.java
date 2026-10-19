package com.technovision.advancedgenetics.common.block.cellanalyzer;

import com.technovision.advancedgenetics.AdvancedGenetics;
import com.technovision.advancedgenetics.api.screen.AbstractGeneticsScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public class CellAnalyzerScreen extends AbstractGeneticsScreen<CellAnalyzerScreenHandler> {

    private static final ResourceLocation TEXTURE = new ResourceLocation(AdvancedGenetics.MOD_ID, "textures/gui/cell_analyzer_gui.png");

    public CellAnalyzerScreen(CellAnalyzerScreenHandler handler, Inventory inventory, Component title) {
        super(handler, inventory, title, TEXTURE);
    }
}
