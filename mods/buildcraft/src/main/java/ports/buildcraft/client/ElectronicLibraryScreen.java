package ports.buildcraft.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import ports.buildcraft.BuildCraft;
import ports.buildcraft.builders.ElectronicLibraryBlockEntity;
import ports.buildcraft.builders.ElectronicLibraryMenu;
import ports.buildcraft.builders.snapshot.Snapshot;
import ports.buildcraft.builders.snapshot.SnapshotStorage;

/**
 * Upstream {@code GuiElectronicLibrary}: the snapshots in this computer's local library, one per row, the two
 * copy arrows and a button that deletes the selected snapshot from the local library.
 */
public final class ElectronicLibraryScreen extends BCScreen<ElectronicLibraryMenu> {
    private static final ResourceLocation BUTTONS = BuildCraft.id("textures/gui/buttons.png");
    private static final int SIZE_X = 244, SIZE_Y = 220;
    private static final int PROGRESS_U = 234, PROGRESS_DOWN_V = 240, PROGRESS_UP_V = 224, PROGRESS_WIDTH = 22, PROGRESS_HEIGHT = 16;
    private static final int PROGRESS_X = 194, PROGRESS_DOWN_Y = 58, PROGRESS_UP_Y = 79;
    private static final int LIST_X = 8, LIST_Y = 22, ROW_WIDTH = 154, ROW_HEIGHT = 8;
    /** Upstream drew every row, past the bottom of the list's panel; here only the rows that fit on it. */
    private static final int MAX_ROWS = 13;
    private static final int DEL_X = 174, DEL_Y = 109, DEL_WIDTH = 25, DEL_HEIGHT = 15;
    private static final int DEL_U = 150, DEL_DISABLED_V = 175, DEL_ENABLED_V = 190, DEL_HOVERED_V = 205;

    public ElectronicLibraryScreen(ElectronicLibraryMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, BuildCraft.id("textures/gui/electronic_library.png"), SIZE_X, SIZE_Y);
    }

    @Nullable
    private ElectronicLibraryBlockEntity library() {
        return minecraft != null && minecraft.level != null
                && minecraft.level.getBlockEntity(menu.pos()) instanceof ElectronicLibraryBlockEntity library ? library : null;
    }

    private static SnapshotStorage snapshots() {
        return SnapshotStorage.get(SnapshotStorage.Side.CLIENT);
    }

    @Nullable
    private Snapshot.Key selected() {
        ElectronicLibraryBlockEntity library = library();
        return library == null ? null : library.getSelected();
    }

    private boolean delEnabled() {
        Snapshot.Key selected = selected();
        return selected != null && snapshots().getSnapshot(selected) != null;
    }

    private boolean overDel(double mouseX, double mouseY) {
        double x = mouseX - leftPos - DEL_X, y = mouseY - topPos - DEL_Y;
        return x >= 0 && x < DEL_WIDTH && y >= 0 && y < DEL_HEIGHT;
    }

    @Override
    protected void renderBg(PoseStack poseStack, float partialTicks, int mouseX, int mouseY) {
        super.renderBg(poseStack, partialTicks, mouseX, mouseY);
        ElectronicLibraryBlockEntity library = library();
        if (library != null && minecraft != null && minecraft.level != null) {
            long now = minecraft.level.getGameTime();
            double down = Mth.clamp(library.deltaProgressDown.getDynamic(now, partialTicks), 0, 1);
            double up = Mth.clamp(library.deltaProgressUp.getDynamic(now, partialTicks), 0, 1);
            // Upstream's down arrow points left but was filled with a negative width, which drew no arrow; it fills from the right here.
            int downWidth = (int) Math.round(PROGRESS_WIDTH * down);
            blit(poseStack, leftPos + PROGRESS_X + PROGRESS_WIDTH - downWidth, topPos + PROGRESS_DOWN_Y,
                    PROGRESS_U + PROGRESS_WIDTH - downWidth, PROGRESS_DOWN_V, downWidth, PROGRESS_HEIGHT);
            blit(poseStack, leftPos + PROGRESS_X, topPos + PROGRESS_UP_Y, PROGRESS_U, PROGRESS_UP_V,
                    (int) Math.round(PROGRESS_WIDTH * up), PROGRESS_HEIGHT);
        }

        Snapshot.Key selected = selected();
        List<Snapshot.Key> list = snapshots().getList();
        for (int i = 0; i < Math.min(list.size(), MAX_ROWS); i++) {
            Snapshot.Key key = list.get(i);
            int x = leftPos + LIST_X, y = topPos + LIST_Y + i * ROW_HEIGHT;
            boolean isSelected = key.equals(selected);
            if (isSelected) fill(poseStack, x, y, x + ROW_WIDTH, y + ROW_HEIGHT, 0xFF555555);
            font.drawShadow(poseStack, key.header == null ? key.toString() : key.header.name, x, y, isSelected ? 0xffffa0 : 0xe0e0e0);
        }

        boolean enabled = delEnabled(), hovered = enabled && overDel(mouseX, mouseY);
        RenderSystem.setShaderTexture(0, BUTTONS);
        blit(poseStack, leftPos + DEL_X, topPos + DEL_Y, DEL_U, !enabled ? DEL_DISABLED_V : hovered ? DEL_HOVERED_V : DEL_ENABLED_V,
                DEL_WIDTH, DEL_HEIGHT);
        Component del = Component.translatable("gui.buildcraft.del");
        font.drawShadow(poseStack, del, leftPos + DEL_X + (DEL_WIDTH - font.width(del)) / 2F, topPos + DEL_Y + (DEL_HEIGHT - 8) / 2F,
                !enabled ? 0xa0a0a0 : hovered ? 0xffffa0 : 0xe0e0e0);
    }

    /** Upstream's GUI drew no title or inventory label; the texture carries none either. */
    @Override
    protected void renderLabels(PoseStack poseStack, int mouseX, int mouseY) {}

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        double x = mouseX - leftPos - LIST_X, y = mouseY - topPos - LIST_Y;
        List<Snapshot.Key> list = snapshots().getList();
        if (x >= 0 && x < ROW_WIDTH && y >= 0) {
            int row = (int) (y / ROW_HEIGHT);
            if (row < Math.min(list.size(), MAX_ROWS)) {
                select(list.get(row));
                return true;
            }
        }
        if (overDel(mouseX, mouseY) && delEnabled()) {
            Snapshot.Key selected = selected();
            select(null);
            snapshots().removeSnapshot(selected);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void select(@Nullable Snapshot.Key key) {
        ClientPlatform.get().sendToServer(ElectronicLibraryBlockEntity.SELECT_CHANNEL,
                buffer -> ElectronicLibraryBlockEntity.writeSelect(buffer, menu.pos(), key));
    }
}
