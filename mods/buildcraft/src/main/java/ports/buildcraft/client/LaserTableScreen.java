package ports.buildcraft.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import ports.buildcraft.silicon.LaserTableMenu;

/** The laser tables' GUIs, which share upstream's progress bar towards the table's power target. */
abstract class LaserTableScreen<M extends LaserTableMenu> extends BCScreen<M> {
    LaserTableScreen(M menu, Inventory inventory, Component title, ResourceLocation texture, int width, int height) {
        super(menu, inventory, title, texture, width, height);
    }

    /**
     * Upstream's 4x70 bar at {@code (176, v)}, rising as the table's power nears its target. Like upstream's
     * {@code drawCutInside}, the visible part is the top of the bar's texture.
     */
    protected void drawProgress(PoseStack poseStack, int x, int y, int v) {
        long target = menu.target();
        if (target == 0) return;
        double progress = (double) menu.power() / target;
        int offset = (int) (70 * Math.max(1 - progress, 0));
        int height = (int) Math.ceil(70 * Math.min(progress, 1));
        blit(poseStack, leftPos + x, topPos + y + offset, 176, v, 4, height);
    }

    protected void drawTitle(PoseStack poseStack, int y) {
        font.draw(poseStack, title, (imageWidth - font.width(title)) / 2F, y, 0x404040);
    }
}
