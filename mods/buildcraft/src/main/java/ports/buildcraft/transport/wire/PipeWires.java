package ports.buildcraft.transport.wire;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.Level;
import ports.buildcraft.transport.PipeBlockEntity;

/**
 * Upstream {@code WireManager}: the wire on each corner of one pipe, the lengths drawn between them, and
 * whether each corner is powered. Clients learn which corners are powered from the pipe's client data.
 */
public final class PipeWires {
    private final PipeBlockEntity holder;
    private final Map<WirePart, DyeColor> parts = new EnumMap<>(WirePart.class);
    private final Map<WireBetween, DyeColor> betweens = new EnumMap<>(WireBetween.class);
    private final Set<WirePart> poweredClient = EnumSet.noneOf(WirePart.class);
    private boolean initialised;
    private int sentPowered;

    public PipeWires(PipeBlockEntity holder) {
        this.holder = holder;
    }

    public Map<WirePart, DyeColor> parts() {
        return parts;
    }

    public Map<WireBetween, DyeColor> betweens() {
        return betweens;
    }

    @Nullable
    private WireSystems systems() {
        return holder.getLevel() instanceof ServerLevel level ? WireSystems.get(level) : null;
    }

    private WireSystem.Element element(WirePart part) {
        return WireSystem.Element.wire(holder.getBlockPos(), part);
    }

    /**
     * Upstream {@code tick}: on its first tick the pipe rebuilds the systems through its wires. On the server it
     * also returns true when which corners are powered has changed, so the pipe sends its client data.
     */
    public boolean tick() {
        WireSystems systems = systems();
        if (!initialised) {
            initialised = true;
            if (systems != null) {
                for (WirePart part : parts.keySet()) systems.buildAndAddWireSystem(element(part));
            }
            updateBetweens(false);
        }
        if (systems == null) return false;
        int powered = poweredMask();
        if (powered == sentPowered) return false;
        sentPowered = powered;
        return true;
    }

    /** Upstream {@code addPart}: false when the corner already has a wire. */
    public boolean addPart(WirePart part, DyeColor colour) {
        if (getColourOfPart(part) != null) return false;
        parts.put(part, colour);
        WireSystems systems = systems();
        if (systems != null) systems.buildAndAddWireSystem(element(part));
        updateBetweens(false);
        holder.onWiresChanged();
        return true;
    }

    /** Upstream {@code removePart}: the corner's colour, or null if it had no wire. */
    @Nullable
    public DyeColor removePart(WirePart part) {
        DyeColor colour = parts.remove(part);
        if (colour == null) return null;
        removeFromSystems(List.of(part));
        updateBetweens(false);
        holder.onWiresChanged();
        return colour;
    }

    public void removeParts(Collection<WirePart> toRemove) {
        List<WirePart> removed = new ArrayList<>();
        for (WirePart part : toRemove) {
            if (parts.remove(part) != null) removed.add(part);
        }
        removeFromSystems(removed);
        updateBetweens(false);
        holder.onWiresChanged();
    }

    /** Upstream {@code invalidate} as the pipe is removed: takes every wire off and returns their colours. */
    public List<DyeColor> removeAll() {
        List<DyeColor> colours = new ArrayList<>(parts.values());
        if (!colours.isEmpty()) removeParts(List.copyOf(parts.keySet()));
        return colours;
    }

    /** Upstream {@code removePartsFromSystem}: neighbours rebuild their systems before the old ones go. */
    private void removeFromSystems(Collection<WirePart> removed) {
        WireSystems systems = systems();
        if (systems == null || removed.isEmpty()) return;
        Set<WireSystem.Element> neighbours = new LinkedHashSet<>();
        for (WirePart part : removed) neighbours.addAll(WireSystem.connectedElements(holder, element(part)));
        neighbours.forEach(systems::buildAndAddWireSystem);
        for (WirePart part : removed) systems.getWireSystemsWithElement(element(part)).forEach(systems::removeWireSystem);
    }

    /** Upstream {@code updateBetweens}: which lengths to draw, here and, unless recursive, at the neighbouring pipes. */
    public void updateBetweens(boolean recursive) {
        betweens.clear();
        parts.forEach((part, colour) -> {
            for (WireBetween between : WireBetween.VALUES) {
                WirePart[] ends = between.parts;
                if (between.to == null) {
                    if (ends[0] == part && getColourOfPart(ends[1]) == colour || ends[1] == part && getColourOfPart(ends[0]) == colour) {
                        betweens.put(between, colour);
                    }
                } else if (ends[0] == part && WireSystem.canWireConnect(holder, between.to)
                        && holder.neighbourTile(between.to) instanceof PipeBlockEntity other && other.wires().getColourOfPart(ends[1]) == colour) {
                    betweens.put(between, colour);
                }
            }
        });
        holder.onWireShapeChanged();
        if (recursive) return;
        for (Direction side : Direction.values()) {
            if (holder.neighbourTile(side) instanceof PipeBlockEntity other) other.wires().updateBetweens(true);
        }
    }

    @Nullable
    public DyeColor getColourOfPart(WirePart part) {
        return parts.get(part);
    }

    public boolean hasPartOfColour(DyeColor colour) {
        return parts.containsValue(colour);
    }

    public boolean isPowered(WirePart part) {
        Level level = holder.getLevel();
        if (level == null) return false;
        if (level.isClientSide) return poweredClient.contains(part);
        WireSystems systems = systems();
        return systems != null && systems.isPowered(element(part));
    }

    public boolean isAnyPowered(DyeColor colour) {
        for (Map.Entry<WirePart, DyeColor> entry : parts.entrySet()) {
            if (entry.getValue() == colour && isPowered(entry.getKey())) return true;
        }
        return false;
    }

    private int poweredMask() {
        int mask = 0;
        for (WirePart part : parts.keySet()) {
            if (isPowered(part)) mask |= 1 << part.ordinal();
        }
        return mask;
    }

    // Saving and client data

    /** Upstream {@code writeToNbt}: pairs of corner and colour id. */
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        int[] array = new int[parts.size() * 2];
        int i = 0;
        for (Map.Entry<WirePart, DyeColor> entry : parts.entrySet()) {
            array[i++] = entry.getKey().ordinal();
            array[i++] = entry.getValue().getId();
        }
        tag.putIntArray("parts", array);
        return tag;
    }

    public void load(CompoundTag tag) {
        parts.clear();
        int[] array = tag.getIntArray("parts");
        for (int i = 0; i + 1 < array.length; i += 2) {
            if (array[i] >= 0 && array[i] < WirePart.VALUES.length) parts.put(WirePart.VALUES[array[i]], DyeColor.byId(array[i + 1]));
        }
        initialised = false;
    }

    public CompoundTag writeClient() {
        CompoundTag tag = save();
        tag.putInt("powered", poweredMask());
        return tag;
    }

    public void readClient(CompoundTag tag) {
        load(tag);
        int powered = tag.getInt("powered");
        poweredClient.clear();
        for (WirePart part : parts.keySet()) {
            if ((powered & (1 << part.ordinal())) != 0) poweredClient.add(part);
        }
        initialised = true;
        updateBetweens(false);
    }
}
