package ports.buildcraft.builders;

import javax.annotation.Nullable;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import ports.buildcraft.builders.snapshot.Snapshot;

/**
 * What upstream's {@code BCBuildersEventDist.onRenderTooltipPostText} drew under an item's tooltip: a recorded
 * template or blueprint names the snapshot to fetch from the server by its key, and a used single schematic
 * carries its one-block snapshot.
 */
public record SnapshotTooltip(@Nullable Snapshot.Key key, @Nullable Snapshot snapshot) implements TooltipComponent {
    public static SnapshotTooltip stored(Snapshot.Key key) {
        return new SnapshotTooltip(key, null);
    }

    public static SnapshotTooltip of(Snapshot snapshot) {
        return new SnapshotTooltip(null, snapshot);
    }
}
