package ports.buildcraft.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import ports.buildcraft.BuildCraft;
import ports.buildcraft.silicon.AdvancedCraftingTableMenu;

/**
 * Upstream {@code GuiAdvancedCraftingTable}: the blueprint, its result, the materials and results, and a progress
 * bar towards the next craft. Upstream's recipe book button and help ledger are not ported.
 */
public final class AdvancedCraftingTableScreen extends LaserTableScreen<AdvancedCraftingTableMenu> {
    public AdvancedCraftingTableScreen(AdvancedCraftingTableMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, BuildCraft.id("textures/gui/advanced_crafting_table.png"), 176, 241);
    }

    @Override
    protected void renderExtras(PoseStack poseStack, float partialTicks) {
        drawProgress(poseStack, 164, 7, 0);
    }

    @Override
    protected void renderLabels(PoseStack poseStack, int mouseX, int mouseY) {
        drawTitle(poseStack, 5);
    }
}
