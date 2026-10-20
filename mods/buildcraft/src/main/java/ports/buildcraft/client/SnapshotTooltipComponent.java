package ports.buildcraft.client;

import com.mojang.blaze3d.vertex.PoseStack;
import javax.annotation.Nullable;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.renderer.entity.ItemRenderer;
import ports.buildcraft.builders.SnapshotTooltip;
import ports.buildcraft.builders.snapshot.Snapshot;

/**
 * Upstream {@code BCBuildersEventDist.onRenderTooltipPostText} drew the snapshot in a 100 by 100 box of its own
 * under the tooltip; here the box is part of the tooltip, and takes no room until the server has sent the snapshot.
 */
public final class SnapshotTooltipComponent implements ClientTooltipComponent {
    private static final int SIZE = 100, MARGIN = 4;
    private final SnapshotTooltip tooltip;

    public SnapshotTooltipComponent(SnapshotTooltip tooltip) {
        this.tooltip = tooltip;
    }

    @Nullable
    private Snapshot snapshot() {
        if (tooltip.snapshot() != null) return tooltip.snapshot();
        return tooltip.key() == null ? null : ClientSnapshots.get(tooltip.key());
    }

    @Override
    public int getHeight() {
        return snapshot() == null ? 0 : SIZE + MARGIN;
    }

    @Override
    public int getWidth(Font font) {
        return snapshot() == null ? 0 : SIZE;
    }

    @Override
    public void renderImage(Font font, int x, int y, PoseStack poseStack, ItemRenderer itemRenderer, int blitOffset) {
        Snapshot snapshot = snapshot();
        if (snapshot != null) SnapshotPreviewRenderer.render(snapshot, x, y + MARGIN, SIZE, SIZE);
    }
}
