package com.technovision.advancedgenetics.common.block.plasmidinfuser;

import com.mojang.blaze3d.vertex.PoseStack;
import com.technovision.advancedgenetics.AdvancedGenetics;
import com.technovision.advancedgenetics.api.screen.AbstractGeneticsScreen;
import com.technovision.advancedgenetics.common.item.PlasmidItem;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

public class PlasmidInfuserScreen extends AbstractGeneticsScreen<PlasmidInfuserScreenHandler> {

    private static final ResourceLocation TEXTURE = new ResourceLocation(AdvancedGenetics.MOD_ID, "textures/gui/plasmid_infuser_gui.png");

    public PlasmidInfuserScreen(PlasmidInfuserScreenHandler handler, Inventory inventory, Component title) {
        super(handler, inventory, title, TEXTURE);
    }

    @Override
    protected void renderLabels(PoseStack matrices, int mouseX, int mouseY) {
        super.renderLabels(matrices, mouseX, mouseY);
        ItemStack plasmid = menu.getClientInventory().getItem(PlasmidInfuserBlockEntity.OUTPUT_SLOT_INDEX);
        CompoundTag tag = plasmid.getTag();
        if (!plasmid.isEmpty() && tag != null) {
            String text = String.format("%d/%d", tag.getInt("count"), PlasmidItem.maxGenes());
            font.draw(matrices, text, imageWidth - font.width(text) - 12, (imageHeight/4.0f)-1, 0x3f3f3f);
        }
    }
}
