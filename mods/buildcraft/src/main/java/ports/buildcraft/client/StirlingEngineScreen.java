package ports.buildcraft.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import ports.buildcraft.BuildCraft;
import ports.buildcraft.energy.StirlingEngineMenu;

/** Upstream {@code GuiEngineStone_BC8}: the flame shrinks as the current fuel item burns down. */
public final class StirlingEngineScreen extends BCScreen<StirlingEngineMenu> {
    private static final int FLAME_X = 81, FLAME_Y = 25, FLAME_SIZE = 14;

    public StirlingEngineScreen(StirlingEngineMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, BuildCraft.id("textures/gui/stirling_engine.png"), 176, 166);
    }

    @Override
    protected void renderExtras(PoseStack poseStack, float partialTicks) {
        int total = menu.totalBurnTime();
        if (total <= 0 || menu.burnTime() <= 0) return;
        int height = (int) Math.ceil((double) menu.burnTime() / total * FLAME_SIZE);
        blit(poseStack, leftPos + FLAME_X, topPos + FLAME_Y + FLAME_SIZE - height, 176, FLAME_SIZE - height, FLAME_SIZE, height + 2);
    }
}
