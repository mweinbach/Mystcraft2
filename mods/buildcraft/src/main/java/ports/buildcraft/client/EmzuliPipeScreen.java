package ports.buildcraft.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import javax.annotation.Nullable;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.DyeColor;
import ports.buildcraft.BuildCraft;
import ports.buildcraft.statement.ColourText;
import ports.buildcraft.transport.EmzuliPipeMenu;
import ports.buildcraft.transport.behaviour.EmzuliBehaviour.SlotIndex;

/**
 * Upstream {@code GuiEmzuliPipe_BC8}: the four filters, each with a paint button showing the paintbrush of its
 * colour (left click: next colour, right click: previous, middle click: none), and beside each filter that a gate
 * has activated a lock, or a tick for the one in use.
 */
public final class EmzuliPipeScreen extends BCScreen<EmzuliPipeMenu> {
    private static final int[][] BUTTONS = {{49, 19}, {49, 47}, {106, 19}, {106, 47}};
    private static final int BUTTON_SIZE = 20;
    private static final ResourceLocation TRIGGER_TRUE = BuildCraft.id("textures/triggers/trigger_true.png");
    private static final ResourceLocation LOCK = BuildCraft.id("textures/gui/lock.png");

    private int lastMouseX, lastMouseY;

    public EmzuliPipeScreen(EmzuliPipeMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, Component.translatable("gui.pipes.emzuli.title"), BuildCraft.id("textures/gui/pipe_emzuli.png"), 176, 166);
        titleLabelY = 6;
    }

    @Nullable
    private SlotIndex buttonAt(double mouseX, double mouseY) {
        for (SlotIndex index : SlotIndex.VALUES) {
            int[] at = BUTTONS[index.ordinal()];
            double x = mouseX - leftPos - at[0], y = mouseY - topPos - at[1];
            if (x >= 0 && y >= 0 && x < BUTTON_SIZE && y < BUTTON_SIZE) return index;
        }
        return null;
    }

    @Override
    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTicks) {
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        super.render(poseStack, mouseX, mouseY, partialTicks);
    }

    @Override
    protected void renderExtras(PoseStack poseStack, float partialTicks) {
        SlotIndex hovered = buttonAt(lastMouseX, lastMouseY);
        SlotIndex current = menu.currentSlot();
        for (SlotIndex index : SlotIndex.VALUES) {
            int x = leftPos + BUTTONS[index.ordinal()][0], y = topPos + BUTTONS[index.ordinal()][1];
            RenderSystem.setShaderTexture(0, texture);
            blit(poseStack, x, y, 176, index == hovered ? 20 : 0, BUTTON_SIZE, BUTTON_SIZE);
            DyeColor colour = menu.slotColour(index);
            if (colour == null) {
                blit(poseStack, x + 2, y + 2, 176, 40, 16, 16);
            } else {
                RenderSystem.setShaderTexture(0, BuildCraft.id("textures/item/paintbrush/" + colour.getName() + ".png"));
                blit(poseStack, x + 2, y + 2, 0, 0, 16, 16, 16, 16);
            }
            if (menu.isActive(index)) {
                RenderSystem.setShaderTexture(0, index == current ? TRIGGER_TRUE : LOCK);
                blit(poseStack, leftPos + (index.ordinal() < 2 ? 4 : 155), topPos + (index.ordinal() % 2 == 0 ? 21 : 49), 0, 0, 16, 16, 16, 16);
            }
        }
    }

    @Override
    protected void renderTooltip(PoseStack poseStack, int mouseX, int mouseY) {
        super.renderTooltip(poseStack, mouseX, mouseY);
        SlotIndex index = buttonAt(mouseX, mouseY);
        if (index == null) return;
        DyeColor colour = menu.slotColour(index);
        Component line = colour == null ? Component.translatable("gui.pipes.emzuli.nopaint")
                : Component.translatable("gui.pipes.emzuli.paint", ColourText.of(colour));
        renderTooltip(poseStack, line, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        SlotIndex index = buttonAt(mouseX, mouseY);
        if (index != null && button >= 0 && button <= 2 && minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, index.ordinal() * 3 + button);
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
