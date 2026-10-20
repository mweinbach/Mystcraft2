package ports.buildcraft.core.marker.volume;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/**
 * Upstream {@code Lock}: something that holds a volume box, and what it stops players doing to it. The
 * only cause upstream has is a block that must stay in place; the lock lapses once the block is gone.
 */
public final class Lock {
    public final CauseBlock cause;
    public final List<Target> targets;

    public Lock(CauseBlock cause, Target... targets) {
        this.cause = cause;
        this.targets = List.of(targets);
    }

    private Lock(CauseBlock cause, List<Target> targets) {
        this.cause = cause;
        this.targets = List.copyOf(targets);
    }

    /** Upstream {@code Lock.Cause.CauseBlock}. */
    public record CauseBlock(BlockPos pos, Block block) {
        public boolean stillWorks(Level level) {
            return level.getBlockState(pos).is(block);
        }
    }

    /** Upstream {@code Lock.Target}: the parts of a volume box a lock protects. */
    public sealed interface Target permits Remove, Resize, UsedByMachine {}

    /** Upstream {@code TargetRemove}. */
    public record Remove() implements Target {}

    /** Upstream {@code TargetResize}: also stops the connector removing the box. */
    public record Resize() implements Target {}

    /** Upstream {@code TargetUsedByMachine}: the box is drawn with the machine's laser. */
    public record UsedByMachine(MachineUse use) implements Target {}

    /** Upstream {@code TargetUsedByMachine.EnumType}. */
    public enum MachineUse { STRIPES_WRITE, STRIPES_READ }

    private enum TargetType { REMOVE, RESIZE, USED_BY_MACHINE }

    private static TargetType typeOf(Target target) {
        if (target instanceof Remove) return TargetType.REMOVE;
        if (target instanceof Resize) return TargetType.RESIZE;
        return TargetType.USED_BY_MACHINE;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        CompoundTag causeTag = new CompoundTag();
        causeTag.put("pos", NbtUtils.writeBlockPos(cause.pos()));
        causeTag.putString("block", Registry.BLOCK.getKey(cause.block()).toString());
        tag.put("cause", causeTag);
        ListTag targetTags = new ListTag();
        for (Target target : targets) {
            CompoundTag targetTag = new CompoundTag();
            targetTag.putString("type", typeOf(target).name());
            if (target instanceof UsedByMachine machine) targetTag.putString("use", machine.use().name());
            targetTags.add(targetTag);
        }
        tag.put("targets", targetTags);
        return tag;
    }

    @Nullable
    public static Lock load(CompoundTag tag) {
        CompoundTag causeTag = tag.getCompound("cause");
        ResourceLocation blockId = ResourceLocation.tryParse(causeTag.getString("block"));
        if (blockId == null || !Registry.BLOCK.containsKey(blockId)) return null;
        CauseBlock cause = new CauseBlock(NbtUtils.readBlockPos(causeTag.getCompound("pos")), Registry.BLOCK.get(blockId));
        List<Target> targets = new ArrayList<>();
        for (Tag element : tag.getList("targets", Tag.TAG_COMPOUND)) {
            CompoundTag targetTag = (CompoundTag) element;
            switch (targetTag.getString("type")) {
                case "REMOVE" -> targets.add(new Remove());
                case "RESIZE" -> targets.add(new Resize());
                case "USED_BY_MACHINE" -> targets.add(new UsedByMachine(
                        "STRIPES_READ".equals(targetTag.getString("use")) ? MachineUse.STRIPES_READ : MachineUse.STRIPES_WRITE));
                default -> {}
            }
        }
        return new Lock(cause, targets);
    }

    void write(FriendlyByteBuf buffer) {
        buffer.writeBlockPos(cause.pos());
        buffer.writeId(Registry.BLOCK, cause.block());
        buffer.writeVarInt(targets.size());
        for (Target target : targets) {
            buffer.writeEnum(typeOf(target));
            if (target instanceof UsedByMachine machine) buffer.writeEnum(machine.use());
        }
    }

    static Lock read(FriendlyByteBuf buffer) {
        CauseBlock cause = new CauseBlock(buffer.readBlockPos(), buffer.readById(Registry.BLOCK));
        int count = buffer.readVarInt();
        List<Target> targets = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            targets.add(switch (buffer.readEnum(TargetType.class)) {
                case REMOVE -> new Remove();
                case RESIZE -> new Resize();
                case USED_BY_MACHINE -> new UsedByMachine(buffer.readEnum(MachineUse.class));
            });
        }
        return new Lock(cause, targets);
    }
}
