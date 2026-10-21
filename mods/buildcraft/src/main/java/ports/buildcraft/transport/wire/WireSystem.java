package ports.buildcraft.transport.wire;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.Level;
import ports.buildcraft.transport.PipeBlockEntity;
import ports.buildcraft.transport.flow.StructureFlow;

/**
 * Upstream {@code WireSystem}: every corner of one colour joined by wire, and the faces of those pipes with a
 * pluggable that can power it.
 */
public final class WireSystem {
    public final List<Element> elements;
    public final DyeColor colour;
    private final int hash;

    public WireSystem(List<Element> elements, DyeColor colour) {
        this.elements = List.copyOf(elements);
        this.colour = colour;
        hash = 31 * this.elements.hashCode() + colour.hashCode();
    }

    public boolean hasElement(Element element) {
        return elements.contains(element);
    }

    public boolean isEmpty() {
        for (Element element : elements) {
            if (element.isWire()) return false;
        }
        return true;
    }

    /** Whether any emitter in the system is emitting its colour. */
    boolean update(WireSystems systems) {
        for (Element element : elements) {
            if (!element.isWire() && systems.isEmitterEmitting(element, colour)) return true;
        }
        return false;
    }

    /**
     * Upstream {@code canWireConnect}: whether a wire could cross {@code side} of {@code holder} to the next pipe,
     * which needs the pipes connected. Structure pipes also pass wire to any pipe their face isn't blocked to;
     * upstream also compared paint, which the port doesn't have.
     */
    public static boolean canWireConnect(PipeBlockEntity holder, Direction side) {
        if (!(holder.neighbourTile(side) instanceof PipeBlockEntity other)) return false;
        if (holder.isConnected(side)) return true;
        if (holder.isBlocked(side) || other.isBlocked(side.getOpposite())) return false;
        return holder.flow() instanceof StructureFlow || other.flow() instanceof StructureFlow;
    }

    /** Upstream {@code getConnectedElementsOfElement}: the corners next to a wire corner it could join. */
    public static List<Element> connectedElements(PipeBlockEntity holder, Element element) {
        WireNode node = new WireNode(element.pos(), element.part());
        List<Element> list = new ArrayList<>();
        for (Direction face : Direction.values()) {
            WireNode next = node.offset(face);
            if (next.pos().equals(node.pos()) || canWireConnect(holder, face)) list.add(Element.wire(next.pos(), next.part()));
        }
        return list;
    }

    public static List<Element> connectedElements(Level level, Element element) {
        if (element.isWire() && level.isLoaded(element.pos()) && level.getBlockEntity(element.pos()) instanceof PipeBlockEntity holder) {
            return connectedElements(holder, element);
        }
        return List.of();
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        for (Element element : elements) list.add(element.save());
        tag.put("elements", list);
        tag.putInt("color", colour.getId());
        return tag;
    }

    public static WireSystem load(CompoundTag tag) {
        List<Element> elements = new ArrayList<>();
        for (Tag element : tag.getList("elements", Tag.TAG_COMPOUND)) elements.add(Element.load((CompoundTag) element));
        return new WireSystem(elements, DyeColor.byId(tag.getInt("color")));
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof WireSystem system && hash == system.hash && colour == system.colour && elements.equals(system.elements);
    }

    @Override
    public int hashCode() {
        return hash;
    }

    /** Upstream {@code WireElement}: a wire corner of a pipe, or a face of a pipe where a pluggable may emit. */
    public record Element(BlockPos pos, @Nullable WirePart part, @Nullable Direction emitterSide) {
        public static Element wire(BlockPos pos, WirePart part) {
            return new Element(pos.immutable(), part, null);
        }

        public static Element emitter(BlockPos pos, Direction side) {
            return new Element(pos.immutable(), null, side);
        }

        public boolean isWire() {
            return part != null;
        }

        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putInt("type", isWire() ? 0 : 1);
            tag.put("blockPos", NbtUtils.writeBlockPos(pos));
            if (part != null) tag.putInt("wirePart", part.ordinal());
            else if (emitterSide != null) tag.putInt("emitterSide", emitterSide.get3DDataValue());
            return tag;
        }

        static Element load(CompoundTag tag) {
            BlockPos pos = NbtUtils.readBlockPos(tag.getCompound("blockPos"));
            if (tag.getInt("type") == 0) return wire(pos, WirePart.VALUES[Math.floorMod(tag.getInt("wirePart"), WirePart.VALUES.length)]);
            return emitter(pos, Direction.from3DDataValue(tag.getInt("emitterSide")));
        }
    }
}
