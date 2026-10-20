package ports.buildcraft.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import ports.buildcraft.BuildCraft;
import ports.buildcraft.silicon.IntegrationTableMenu;

/** Upstream {@code GuiIntegrationTable}: the ring of slots, the recipe's output, the result and a progress bar. */
public final class IntegrationTableScreen extends LaserTableScreen<IntegrationTableMenu> {
    public IntegrationTableScreen(IntegrationTableMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, BuildCraft.id("textures/gui/integration_table.png"), 176, 191);
    }

    @Override
    protected void renderExtras(PoseStack poseStack, float partialTicks) {
        drawProgress(poseStack, 164, 22, 0);
    }

    @Override
    protected void renderLabels(PoseStack poseStack, int mouseX, int mouseY) {
        drawTitle(poseStack, 10);
    }
}
