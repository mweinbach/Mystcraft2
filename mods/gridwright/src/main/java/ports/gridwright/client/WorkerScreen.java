package ports.gridwright.client;

import com.mojang.blaze3d.vertex.PoseStack;
import ports.gridwright.grid.GridBlockEntity;
import ports.gridwright.worker.WorkerLayout;
import ports.gridwright.worker.WorkerMenu;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

import java.util.Locale;

/**
 * The quarry and mechanical block screen, drawn from flat rectangles like {@link MachineScreen}: slot
 * wells, a progress bar, the status, and the Grid Power draw or (for the ender quarry) stored energy.
 */
public class WorkerScreen extends AbstractContainerScreen<WorkerMenu> {

    private static final int PANEL = 0xFFC6C6C6;
    private static final int EDGE_LIGHT = 0xFFFFFFFF;
    private static final int EDGE_DARK = 0xFF555555;
    private static final int WELL = 0xFF8B8B8B;
    private static final int BAR_BACK = 0xFF373737;
    private static final int PROGRESS = 0xFF3FA9F5;
    private static final int TEXT = 0x404040;

    public WorkerScreen(WorkerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = WorkerLayout.IMAGE_HEIGHT;
        inventoryLabelY = WorkerLayout.INVENTORY_Y - 11;
    }

    @Override
    public void render(PoseStack pose, int mouseX, int mouseY, float partialTick) {
        renderBackground(pose);
        super.render(pose, mouseX, mouseY, partialTick);
        renderTooltip(pose, mouseX, mouseY);
    }

    @Override
    protected void renderBg(PoseStack pose, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        fill(pose, x, y, x + imageWidth, y + imageHeight, PANEL);
        fill(pose, x, y, x + imageWidth - 1, y + 1, EDGE_LIGHT);
        fill(pose, x, y, x + 1, y + imageHeight - 1, EDGE_LIGHT);
        fill(pose, x + 1, y + imageHeight - 1, x + imageWidth, y + imageHeight, EDGE_DARK);
        fill(pose, x + imageWidth - 1, y + 1, x + imageWidth, y + imageHeight, EDGE_DARK);
        for (Slot slot : menu.slots) {
            int sx = x + slot.x - 1;
            int sy = y + slot.y - 1;
            fill(pose, sx, sy, sx + 18, sy + 18, EDGE_DARK);
            fill(pose, sx + 1, sy + 1, sx + 18, sy + 18, EDGE_LIGHT);
            fill(pose, sx + 1, sy + 1, sx + 17, sy + 17, WELL);
        }
        int barX = x + 8;
        int barY = y + 76;
        int barWidth = 160;
        fill(pose, barX, barY, barX + barWidth, barY + 4, BAR_BACK);
        int filled = barWidth * Math.min(1000, menu.get(2)) / 1000;
        if (filled > 0) fill(pose, barX, barY, barX + filled, barY + 4, PROGRESS);
    }

    @Override
    protected void renderLabels(PoseStack pose, int mouseX, int mouseY) {
        super.renderLabels(pose, mouseX, mouseY);
        Component status = Component.translatable("screen.gridwright.worker.status."
                + menu.status().name().toLowerCase(Locale.ROOT));
        font.draw(pose, status, 8, 83, TEXT);
        Component detail = menu.layout() == WorkerLayout.ENDER_QUARRY
                ? Component.translatable("screen.gridwright.worker.energy", String.format(Locale.ROOT, "%,d", menu.getWide(3)),
                        String.format(Locale.ROOT, "%,d", menu.getWide(5)), menu.get(7))
                : Component.translatable("screen.gridwright.worker.power", GridBlockEntity.format(menu.get(1) / 10.0), menu.get(7));
        font.draw(pose, detail, 8, 93, TEXT);
    }
}
