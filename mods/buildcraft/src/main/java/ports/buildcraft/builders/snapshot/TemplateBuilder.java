package ports.buildcraft.builders.snapshot;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.DirectionalPlaceContext;
import ports.buildcraft.builders.filler.FilledTemplate;

/**
 * Upstream {@code TemplateBuilder}: fills every set block of a template with whatever block item comes
 * first in the host's resources, and clears the rest.
 */
public final class TemplateBuilder extends SnapshotBuilder<TemplateBuilder.Host> {
    /** Upstream {@code ITileForTemplateBuilder}. */
    public interface Host extends SnapshotHost {
        @Nullable
        BuildingInfo templateBuildingInfo();

        @Nullable
        FilledTemplate template();

        /** One item from the first non-empty resource slot, as upstream {@code extract(null, 1, 1, simulate)}. */
        ItemStack extractResource(boolean simulate);

        /** Puts an item back into the resources; returns what did not fit. */
        ItemStack insertResource(ItemStack stack);

        /** Where items that no longer fit in the resources go. */
        void dropResource(ItemStack stack);
    }

    public TemplateBuilder(Host host) {
        super(host);
    }

    @Nullable
    @Override
    protected BuildingInfo buildingInfo() {
        return host.templateBuildingInfo();
    }

    @Override
    protected boolean isAir(BlockPos pos) {
        BuildingInfo info = buildingInfo();
        FilledTemplate template = host.template();
        return info == null || template == null || !info.box.contains(pos) || !template.get(info.posToIndex(pos));
    }

    @Override
    protected boolean canPlace(BlockPos pos) {
        return host.hostLevel().isEmptyBlock(pos);
    }

    @Override
    protected boolean isReadyToPlace(BlockPos pos) {
        return true;
    }

    @Override
    protected boolean hasEnoughToPlaceItems(BlockPos pos) {
        return !host.extractResource(true).isEmpty();
    }

    @Override
    protected List<ItemStack> getToPlaceItems(BlockPos pos) {
        return List.of(host.extractResource(false));
    }

    /**
     * Upstream {@code TemplateHandlerDefault} used the item on the top face, aimed at the block's centre,
     * as a fake player standing on the builder with its default facing (south). No player is involved
     * here; the place context carries that same face and facing.
     */
    @Override
    protected boolean doPlaceTask(PlaceTask placeTask) {
        ItemStack stack = placeTask.items.get(0).copy();
        if (!(stack.getItem() instanceof BlockItem blockItem)) return false;
        return blockItem.place(new DirectionalPlaceContext(host.hostLevel(), placeTask.pos, Direction.SOUTH, stack, Direction.UP)).consumesAction();
    }

    @Override
    protected void cancelPlaceTask(PlaceTask placeTask) {
        super.cancelPlaceTask(placeTask);
        ItemStack leftover = host.insertResource(placeTask.items.get(0).copy());
        if (!leftover.isEmpty()) host.dropResource(leftover);
    }

    @Override
    protected boolean isBlockCorrect(BlockPos pos) {
        return !isAir(pos) && !host.hostLevel().isEmptyBlock(pos);
    }
}
