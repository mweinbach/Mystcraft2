package ports.buildcraft.silicon.plug;

import javax.annotation.Nullable;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import ports.buildcraft.silicon.BCSilicon;
import ports.buildcraft.transport.PipeBlockEntity;
import ports.buildcraft.transport.SideCheck;
import ports.buildcraft.transport.pluggable.PipePluggable;
import ports.buildcraft.transport.pluggable.PluggableDefinition;

/**
 * Upstream {@code PluggableLens}. A lens paints every item crossing its face in its colour, or washes the colour off
 * if clear. A filter only lets in items of its colour or none, and steers items of its colour out through its face,
 * and towards it from the neighbouring pipe ({@code FilterEventHandler}).
 */
public class LensPluggable extends PipePluggable {
    private static final AABB[] BOXES = faceBoxes(0, 2, 3, 13);

    private LensVariant variant;

    public LensPluggable(PluggableDefinition definition, PipeBlockEntity holder, Direction side) {
        this(definition, holder, side, new LensVariant(null, false));
    }

    public LensPluggable(PluggableDefinition definition, PipeBlockEntity holder, Direction side, LensVariant variant) {
        super(definition, holder, side);
        this.variant = variant;
    }

    public LensVariant variant() {
        return variant;
    }

    @Override
    public void save(CompoundTag tag) {
        if (variant.colour() != null) tag.putString("colour", variant.colour().getName());
        tag.putBoolean("f", variant.filter());
    }

    @Override
    public void load(CompoundTag tag) {
        DyeColor colour = tag.contains("colour") ? DyeColor.byName(tag.getString("colour"), null) : null;
        variant = new LensVariant(colour, tag.getBoolean("f"));
    }

    @Override
    public void writeClient(CompoundTag tag) {
        save(tag);
    }

    @Override
    public void readClient(CompoundTag tag) {
        load(tag);
    }

    @Override
    public AABB getBoundingBox() {
        return BOXES[side.ordinal()];
    }

    @Override
    public ItemStack getPickStack() {
        return new ItemStack(BCSilicon.lensItem(variant));
    }

    @Override
    public boolean canItemEnter(@Nullable DyeColor colour) {
        return !variant.filter() || colour == null || colour == variant.colour();
    }

    @Nullable
    @Override
    public DyeColor colourItemThrough(@Nullable DyeColor colour) {
        return variant.filter() ? colour : variant.colour();
    }

    @Override
    public void itemSideCheck(SideCheck check) {
        if (!variant.filter()) return;
        if (check.colour == variant.colour()) check.increasePriority(side);
        else if (check.colour != null) check.disallow(side);
        else check.decreasePriority(side);
    }

    @Override
    public void neighbourItemSideCheck(SideCheck check, @Nullable DyeColor colour) {
        if (!variant.filter()) return;
        Direction towards = side.getOpposite();
        if (colour == variant.colour()) check.increasePriority(towards);
        else if (colour == null) check.decreasePriority(towards);
    }
}
