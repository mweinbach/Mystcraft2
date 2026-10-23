package ports.buildcraft.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import ports.buildcraft.BuildCraft;
import ports.buildcraft.transport.DiamondWoodPipeMenu;
import ports.buildcraft.transport.behaviour.WoodDiamondBehaviour.FilterMode;

/**
 * Upstream {@code GuiDiamondWoodPipe}: the nine filters, the whitelist, blacklist and (item pipe only) round robin
 * buttons, and in round robin mode a frame around the current filter.
 */
public final class DiamondWoodPipeScreen extends BCScreen<DiamondWoodPipeMenu> {
    private static final ResourceLocation BUTTONS = BuildCraft.id("textures/gui/pipe_emerald_button.png");
    private static final String[] TOOLTIPS = {"tip.PipeItemsEmerald.whitelist", "tip.PipeItemsEmerald.blacklist", "tip.PipeItemsEmerald.roundrobin"};
    private static final int BUTTON_X = 7, BUTTON_Y = 41, BUTTON_SIZE = 18;

    private double lastMouseX, lastMouseY;

    public DiamondWoodPipeScreen(DiamondWoodPipeMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, Component.translatable("gui.pipes.emerald.title"), BuildCraft.id("textures/gui/pipe_emerald.png"), 175, 161);
        titleLabelY = 6;
    }

    private int buttonCount() {
        return menu.roundRobin ? 3 : 2;
    }

    private int buttonAt(double mouseX, double mouseY) {
        double x = mouseX - leftPos - BUTTON_X, y = mouseY - topPos - BUTTON_Y;
        if (y < 0 || y >= BUTTON_SIZE || x < 0) return -1;
        int index = (int) (x / BUTTON_SIZE);
        return index < buttonCount() ? index : -1;
    }

    /** Upstream {@code GuiImageButton}: the button's state from the top row, then its icon. */
    @Override
    protected void renderExtras(PoseStack poseStack, float partialTicks) {
        RenderSystem.setShaderTexture(0, BUTTONS);
        int active = menu.filterMode().ordinal();
        for (int i = 0; i < buttonCount(); i++) {
            int x = leftPos + BUTTON_X + i * BUTTON_SIZE, y = topPos + BUTTON_Y;
            boolean hovered = buttonAt(lastMouseX, lastMouseY) == i;
            int state = i == active ? (hovered ? 4 : 3) : (hovered ? 2 : 1);
            blit(poseStack, x, y, state * BUTTON_SIZE, 0, BUTTON_SIZE, BUTTON_SIZE);
            blit(poseStack, x + 1, y + 1, 19 + i * 18, 19, BUTTON_SIZE - 2, BUTTON_SIZE - 2);
        }
    }

    @Override
    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTicks) {
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        super.render(poseStack, mouseX, mouseY, partialTicks);
    }

    @Override
    protected void renderLabels(PoseStack poseStack, int mouseX, int mouseY) {
        super.renderLabels(poseStack, mouseX, mouseY);
        if (menu.filterMode() != FilterMode.ROUND_ROBIN) return;
        RenderSystem.setShaderColor(1, 1, 1, 1);
        RenderSystem.setShaderTexture(0, texture);
        boolean valid = menu.filterValid();
        int x = valid ? 18 * menu.currentFilter() : 0;
        blit(poseStack, 6 + x, 16, 176, valid ? 0 : 20, 20, 20);
    }

    @Override
    protected void renderTooltip(PoseStack poseStack, int mouseX, int mouseY) {
        super.renderTooltip(poseStack, mouseX, mouseY);
        int button = buttonAt(mouseX, mouseY);
        if (button >= 0) renderTooltip(poseStack, Component.translatable(TOOLTIPS[button]), mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int index = buttonAt(mouseX, mouseY);
        if (index >= 0 && minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, index);
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
