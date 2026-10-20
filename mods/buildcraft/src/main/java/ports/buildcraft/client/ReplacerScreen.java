package ports.buildcraft.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import javax.annotation.Nullable;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import ports.buildcraft.BuildCraft;
import ports.buildcraft.builders.ReplacerMenu;
import ports.buildcraft.builders.SnapshotItem;
import ports.buildcraft.builders.snapshot.Snapshot;

/**
 * Upstream {@code GuiReplacer}: the blueprint in the slot drawn turning above the slots, and a name field that
 * upstream left unconnected, so whatever is typed into it goes nowhere.
 */
public final class ReplacerScreen extends BCScreen<ReplacerMenu> {
    private static final int PREVIEW_X = 8, PREVIEW_Y = 9, PREVIEW_WIDTH = 160, PREVIEW_HEIGHT = 100;
    @Nullable private EditBox nameField;

    public ReplacerScreen(ReplacerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, BuildCraft.id("textures/gui/replacer.png"), 176, 241);
    }

    @Override
    protected void init() {
        super.init();
        nameField = new EditBox(font, leftPos + 30, topPos + 117, 138, 12, Component.empty());
        addRenderableWidget(nameField);
    }

    @Override
    protected void containerTick() {
        if (nameField != null) nameField.tick();
    }

    @Override
    protected void renderExtras(PoseStack poseStack, float partialTicks) {
        Snapshot.Header header = SnapshotItem.getHeader(menu.snapshot());
        Snapshot snapshot = header == null ? null : ClientSnapshots.get(header.key);
        if (snapshot != null) SnapshotPreviewRenderer.render(snapshot, leftPos + PREVIEW_X, topPos + PREVIEW_Y, PREVIEW_WIDTH, PREVIEW_HEIGHT);
    }

    /** Upstream's GUI drew no title or inventory label; the texture carries none either. */
    @Override
    protected void renderLabels(PoseStack poseStack, int mouseX, int mouseY) {}

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
