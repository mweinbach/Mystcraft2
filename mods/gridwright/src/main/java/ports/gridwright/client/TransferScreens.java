package ports.gridwright.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import ports.gridwright.grid.GridBlockEntity;
import ports.gridwright.transfer.FilterMenu;
import ports.gridwright.transfer.NodeMenu;
import ports.gridwright.transfer.TransferRegistry;

import java.util.Locale;

/** The node and filter screens, drawn from flat rectangles like the machine screen. */
public final class TransferScreens {

    private static final int PANEL = 0xFFC6C6C6;
    private static final int EDGE_LIGHT = 0xFFFFFFFF;
    private static final int EDGE_DARK = 0xFF555555;
    private static final int WELL = 0xFF8B8B8B;
    private static final int GHOST = 0xFF9DB4C8;
    private static final int TEXT = 0x404040;

    private TransferScreens() { }

    public static void register() {
        MenuScreens.register(TransferRegistry.NODE_MENU.get(), NodeScreen::new);
        MenuScreens.register(TransferRegistry.FILTER_MENU.get(), FilterScreen::new);
    }

    private abstract static class FlatScreen<M extends AbstractContainerMenu> extends AbstractContainerScreen<M> {
        FlatScreen(M menu, Inventory inventory, Component title) {
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
            for (int i = 0; i < menu.slots.size(); i++) {
                Slot slot = menu.slots.get(i);
                int sx = x + slot.x - 1;
                int sy = y + slot.y - 1;
                fill(pose, sx, sy, sx + 18, sy + 18, EDGE_DARK);
                fill(pose, sx + 1, sy + 1, sx + 18, sy + 18, EDGE_LIGHT);
                fill(pose, sx + 1, sy + 1, sx + 17, sy + 17, isGhost(i) ? GHOST : WELL);
            }
        }

        boolean isGhost(int slot) {
            return false;
        }
    }

    static final class NodeScreen extends FlatScreen<NodeMenu> {
        NodeScreen(NodeMenu menu, Inventory inventory, Component title) {
            super(menu, inventory, title);
        }

        @Override
        protected void renderLabels(PoseStack pose, int mouseX, int mouseY) {
            super.renderLabels(pose, mouseX, mouseY);
            font.draw(pose, Component.translatable("screen.gridwright.node.filter"), NodeMenu.FILTER_X - 2, NodeMenu.SLOT_Y - 10, TEXT);
            font.draw(pose, Component.translatable("screen.gridwright.node.upgrades",
                    GridBlockEntity.format(menu.cost()),
                    Component.translatable("screen.gridwright.node.power." + menu.power().name().toLowerCase(Locale.ROOT))), 8, 48, TEXT);
            font.draw(pose, Component.translatable("screen.gridwright.node.targets", menu.targets()), 8, 60, TEXT);
        }
    }

    static final class FilterScreen extends FlatScreen<FilterMenu> {
        private Button mode;
        private Button nbt;

        FilterScreen(FilterMenu menu, Inventory inventory, Component title) {
            super(menu, inventory, title);
        }

        @Override
        protected void init() {
            super.init();
            mode = addRenderableWidget(new Button(leftPos + 8, topPos + 44, 78, 20, Component.empty(), button -> press(FilterMenu.BUTTON_BLACKLIST)));
            nbt = addRenderableWidget(new Button(leftPos + 90, topPos + 44, 78, 20, Component.empty(), button -> press(FilterMenu.BUTTON_MATCH_NBT)));
        }

        private void press(int id) {
            if (minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
        }

        @Override
        public void render(PoseStack pose, int mouseX, int mouseY, float partialTick) {
            mode.setMessage(Component.translatable(menu.blacklist() ? "screen.gridwright.filter.blacklist" : "screen.gridwright.filter.whitelist"));
            nbt.setMessage(Component.translatable(menu.matchNbt() ? "screen.gridwright.filter.nbt_on" : "screen.gridwright.filter.nbt_off"));
            super.render(pose, mouseX, mouseY, partialTick);
        }

        @Override
        boolean isGhost(int slot) {
            return slot < 9;
        }
    }
}
