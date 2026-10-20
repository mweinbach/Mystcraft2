package ports.buildcraft.builders;

import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import ports.buildcraft.builders.snapshot.EnumSnapshotType;
import ports.buildcraft.builders.snapshot.Snapshot;
import ports.buildcraft.builders.snapshot.SnapshotHash;

/**
 * Upstream {@code ItemSnapshot}: blank and recorded templates and blueprints, which were metadata variants of
 * one item before the flattening. A recorded one carries the header of the snapshot it names.
 */
public class SnapshotItem extends Item {
    public final EnumSnapshotType snapshotType;
    public final boolean used;

    public SnapshotItem(EnumSnapshotType snapshotType, boolean used, Properties properties) {
        super(properties.stacksTo(used ? 1 : 16));
        this.snapshotType = snapshotType;
        this.used = used;
    }

    public static ItemStack getClean(EnumSnapshotType snapshotType) {
        return new ItemStack(snapshotType == EnumSnapshotType.TEMPLATE ? BCBuilders.TEMPLATE_CLEAN.get() : BCBuilders.BLUEPRINT_CLEAN.get());
    }

    public static ItemStack getUsed(EnumSnapshotType snapshotType, Snapshot.Header header) {
        ItemStack stack = new ItemStack(snapshotType == EnumSnapshotType.TEMPLATE ? BCBuilders.TEMPLATE_USED.get() : BCBuilders.BLUEPRINT_USED.get());
        CompoundTag tag = new CompoundTag();
        tag.put("header", header.serializeNBT());
        stack.setTag(tag);
        return stack;
    }

    @Nullable
    public static Snapshot.Header getHeader(ItemStack stack) {
        if (stack.getItem() instanceof SnapshotItem item && item.used) {
            CompoundTag tag = stack.getTag();
            if (tag != null && tag.contains("header", Tag.TAG_COMPOUND)) return new Snapshot.Header(tag.getCompound("header"));
        }
        return null;
    }

    @Override
    public Optional<TooltipComponent> getTooltipImage(ItemStack stack) {
        Snapshot.Header header = getHeader(stack);
        return header == null ? Optional.empty() : Optional.of(SnapshotTooltip.stored(header.key));
    }

    /** The author's name is shown only while they are in the same level, as upstream looked them up there. */
    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        Snapshot.Header header = getHeader(stack);
        if (header == null) {
            tooltip.add(Component.translatable("item.buildcraft.snapshot.blank"));
            return;
        }
        tooltip.add(Component.literal(header.name));
        Player owner = level == null ? null : level.getPlayerByUUID(header.owner);
        if (owner != null) tooltip.add(Component.translatable("item.buildcraft.snapshot.author").append(" ").append(owner.getName()));
        if (flag.isAdvanced()) {
            tooltip.add(Component.literal("Hash: " + SnapshotHash.toHex(header.key.hash)));
            tooltip.add(Component.literal("Date: " + header.created));
            tooltip.add(Component.literal("Owner UUID: " + header.owner));
        }
    }
}
