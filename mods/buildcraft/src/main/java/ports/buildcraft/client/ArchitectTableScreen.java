package ports.buildcraft.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import javax.annotation.Nullable;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import ports.buildcraft.BuildCraft;
import ports.buildcraft.builders.ArchitectTableBlockEntity;
import ports.buildcraft.builders.ArchitectTableMenu;

/**
 * Upstream {@code GuiArchitectTable}: the snapshot slots, the scan progress arrow and the name field, which
 * starts focused and renames the table on every key press.
 */
public final class ArchitectTableScreen extends BCScreen<ArchitectTableMenu> {
    private static final int PROGRESS_X = 159, PROGRESS_Y = 34, PROGRESS_U = 0, PROGRESS_V = 166, PROGRESS_WIDTH = 24, PROGRESS_HEIGHT = 17;
    @Nullable private EditBox nameField;

    public ArchitectTableScreen(ArchitectTableMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, BuildCraft.id("textures/gui/architect.png"), 256, 166);
    }

    @Override
    protected void init() {
        super.init();
        nameField = new EditBox(font, leftPos + 90, topPos + 62, 156, 12, Component.empty());
        nameField.setMaxLength(ArchitectTableMenu.MAX_NAME_LENGTH);
        ArchitectTableBlockEntity table = table();
        if (table != null) nameField.setValue(table.name);
        nameField.setResponder(name -> ClientPlatform.get().sendToServer(ArchitectTableMenu.NAME_CHANNEL, buffer -> buffer.writeUtf(name.trim(),
                ArchitectTableMenu.MAX_NAME_LENGTH)));
        addRenderableWidget(nameField);
        setInitialFocus(nameField);
    }

    @Nullable
    private ArchitectTableBlockEntity table() {
        return minecraft != null && minecraft.level != null
                && minecraft.level.getBlockEntity(menu.pos()) instanceof ArchitectTableBlockEntity table ? table : null;
    }

    @Override
    protected void containerTick() {
        if (nameField != null) nameField.tick();
    }

    @Override
    protected void renderExtras(PoseStack poseStack, float partialTicks) {
        ArchitectTableBlockEntity table = table();
        if (table == null || minecraft == null || minecraft.level == null) return;
        double progress = Mth.clamp(table.deltaProgress.getDynamic(minecraft.level.getGameTime(), partialTicks), 0, 1);
        int width = (int) Math.round(PROGRESS_WIDTH * progress);
        if (width <= 0) return;
        RenderSystem.setShaderTexture(0, texture);
        blit(poseStack, leftPos + PROGRESS_X, topPos + PROGRESS_Y, PROGRESS_U, PROGRESS_V, width, PROGRESS_HEIGHT);
    }

    /** Upstream's GUI drew no title or inventory label; the texture carries none either. */
    @Override
    protected void renderLabels(PoseStack poseStack, int mouseX, int mouseY) {}

    /** As the anvil's name field: typing into it must not close the screen on the inventory key. */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == InputConstants.KEY_ESCAPE && minecraft != null && minecraft.player != null) {
            minecraft.player.closeContainer();
            return true;
        }
        if (nameField != null && (nameField.keyPressed(keyCode, scanCode, modifiers) || nameField.canConsumeInput())) return true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
