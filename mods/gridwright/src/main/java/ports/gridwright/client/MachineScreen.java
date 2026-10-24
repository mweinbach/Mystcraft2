package ports.gridwright.client;

import com.mojang.blaze3d.vertex.PoseStack;
import ports.gridwright.grid.GridBlockEntity;
import ports.gridwright.machine.MachineLayout;
import ports.gridwright.machine.MachineMenu;
import ports.gridwright.machine.ResonatorBlockEntity;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

import java.util.Locale;

/**
 * The generator and resonator screen. It is drawn from flat rectangles rather than a texture, so the
 * mod ships no GUI art: a panel, slot wells, a progress bar and an energy gauge for generators.
 */
public class MachineScreen extends AbstractContainerScreen<MachineMenu> {

    private static final int PANEL = 0xFFC6C6C6;
    private static final int EDGE_LIGHT = 0xFFFFFFFF;
    private static final int EDGE_DARK = 0xFF555555;
    private static final int WELL = 0xFF8B8B8B;
    private static final int BAR_BACK = 0xFF373737;
    private static final int ENERGY = 0xFFD8312A;
    private static final int PROGRESS = 0xFF3FA9F5;
    private static final int TEXT = 0x404040;

    public MachineScreen(MachineMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 166;
        inventoryLabelY = imageHeight - 94;
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
        int barX = x + MachineLayout.INPUT_X + 22;
        int barY = y + MachineLayout.SLOT_Y + 5;
        int barWidth = MachineLayout.OUTPUT_X - MachineLayout.INPUT_X - 28;
        fill(pose, barX, barY, barX + barWidth, barY + 6, BAR_BACK);
        int filled = (int) Math.round(barWidth * progress());
        if (filled > 0) fill(pose, barX, barY, barX + filled, barY + 6, PROGRESS);
        if (menu.layout() == MachineLayout.GENERATOR) {
            long capacity = Math.max(1, menu.getWide(2));
            int gaugeX = x + 152;
            int gaugeTop = y + 17;
            int gaugeHeight = 52;
            fill(pose, gaugeX, gaugeTop, gaugeX + 12, gaugeTop + gaugeHeight, BAR_BACK);
            int level = (int) (gaugeHeight * Math.min(1.0, (double) menu.getWide(0) / capacity));
            if (level > 0) fill(pose, gaugeX + 1, gaugeTop + gaugeHeight - level, gaugeX + 11, gaugeTop + gaugeHeight, ENERGY);
        }
    }

    @Override
    protected void renderLabels(PoseStack pose, int mouseX, int mouseY) {
        super.renderLabels(pose, mouseX, mouseY);
        Component line = menu.layout() == MachineLayout.GENERATOR
                ? Component.translatable("screen.gridwright.generator", String.format(Locale.ROOT, "%,d", menu.getWide(0)),
                        String.format(Locale.ROOT, "%,d", menu.getWide(2)), menu.get(5))
                : Component.translatable("screen.gridwright.resonator", GridBlockEntity.format(menu.get(2) / 10.0),
                        Component.translatable("screen.gridwright.status." + status().name().toLowerCase(Locale.ROOT)));
        font.draw(pose, line, 8, 60, TEXT);
    }

    private double progress() {
        if (menu.layout() == MachineLayout.GENERATOR) return menu.get(4) / 1000.0;
        int time = menu.get(1);
        return time <= 0 ? 0 : Math.min(1.0, (double) menu.get(0) / time);
    }

    private ResonatorBlockEntity.Status status() {
        ResonatorBlockEntity.Status[] values = ResonatorBlockEntity.Status.values();
        int index = menu.get(3);
        return index < values.length ? values[index] : ResonatorBlockEntity.Status.IDLE;
    }
}
