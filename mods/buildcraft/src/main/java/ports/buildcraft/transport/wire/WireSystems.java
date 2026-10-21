package ports.buildcraft.transport.wire;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.saveddata.SavedData;
import ports.buildcraft.transport.PipeBlockEntity;

/**
 * Upstream {@code WorldSavedDataWireSystems}: a level's wire systems and whether each is powered, worked out
 * again at the end of every tick from what their emitters emit. Upstream also sent the systems to watching
 * players; here each pipe sends its own wires and which of them are powered with the rest of its client data.
 */
public final class WireSystems extends SavedData {
    private static final String DATA_NAME = "buildcraft_wire_systems";

    private final ServerLevel level;
    private final Map<WireSystem, Boolean> systems = new HashMap<>();
    private final Map<WireSystem.Element, List<WireSystem>> index = new HashMap<>();
    /** The last emitter seen at each face, which answers for it while its chunk is unloaded. */
    private final Map<WireSystem.Element, WireEmitter> emitters = new HashMap<>();
    private boolean structureChanged;

    private WireSystems(ServerLevel level) {
        this.level = level;
    }

    public static WireSystems get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(tag -> load(level, tag), () -> new WireSystems(level), DATA_NAME);
    }

    public int size() {
        return systems.size();
    }

    public List<WireSystem> getWireSystemsWithElement(WireSystem.Element element) {
        List<WireSystem> found = index.get(element);
        return found == null ? List.of() : new ArrayList<>(found);
    }

    /** Upstream {@code WireManager.isPowered}: whether any system through this corner is powered. */
    public boolean isPowered(WireSystem.Element element) {
        List<WireSystem> found = index.get(element);
        if (found == null) return false;
        for (WireSystem system : found) {
            if (Boolean.TRUE.equals(systems.get(system))) return true;
        }
        return false;
    }

    public void removeWireSystem(WireSystem system) {
        systems.remove(system);
        for (WireSystem.Element element : system.elements) {
            List<WireSystem> found = index.get(element);
            if (found == null) continue;
            found.remove(system);
            if (found.isEmpty()) {
                index.remove(element);
                emitters.remove(element);
            }
        }
        structureChanged = true;
    }

    private void addWireSystem(WireSystem system, boolean powered) {
        if (systems.put(system, powered) != null) return;
        for (WireSystem.Element element : system.elements) {
            List<WireSystem> found = index.computeIfAbsent(element, unused -> new ArrayList<>());
            if (found.contains(system)) throw new IllegalStateException("Wire system already indexed at " + element);
            found.add(system);
        }
    }

    /** Upstream {@code buildAndAddWireSystem}: floods out from one corner, replacing the systems it takes over. */
    public void buildAndAddWireSystem(WireSystem.Element start) {
        WireSystem system = build(start);
        if (system != null && !system.isEmpty()) {
            addWireSystem(system, false);
            systems.put(system, system.update(this));
        }
        structureChanged = true;
    }

    /** Upstream's flood fill in {@code new WireSystem(WorldSavedDataWireSystems, WireElement)}; it stops at unloaded chunks. */
    @Nullable
    private WireSystem build(WireSystem.Element start) {
        Map<BlockPos, PipeBlockEntity> holders = new HashMap<>();
        Set<WireSystem.Element> walked = new HashSet<>();
        Queue<WireSystem.Element> queue = new ArrayDeque<>();
        queue.add(start);
        DyeColor colour = null;
        List<WireSystem.Element> elements = new ArrayList<>();
        while (!queue.isEmpty()) {
            WireSystem.Element element = queue.remove();
            if (!walked.add(element)) continue;
            PipeBlockEntity holder = holders.computeIfAbsent(element.pos(), this::pipeAt);
            if (holder == null) continue;
            if (element.isWire()) {
                DyeColor partColour = holder.wires().getColourOfPart(element.part());
                if (colour == null) colour = partColour;
                if (colour != null && partColour == colour) {
                    for (WireSystem other : getWireSystemsWithElement(element)) {
                        if (other.colour == colour) removeWireSystem(other);
                    }
                    elements.add(element);
                    queue.addAll(WireSystem.connectedElements(holder, element));
                    for (Direction side : Direction.values()) queue.add(WireSystem.Element.emitter(element.pos(), side));
                }
            } else if (holder.getPluggable(element.emitterSide()) instanceof WireEmitter) {
                elements.add(element);
            }
        }
        return colour == null ? null : new WireSystem(elements, colour);
    }

    @Nullable
    private PipeBlockEntity pipeAt(BlockPos pos) {
        return level.isLoaded(pos) && level.getBlockEntity(pos) instanceof PipeBlockEntity pipe ? pipe : null;
    }

    /**
     * Upstream {@code rebuildWireSystemsAround}: called when a pluggable that might emit comes or goes, or the pipe's
     * connections change. Unlike upstream it also starts from the holder's own wires, so a lone corner takes in a new gate.
     */
    public void rebuildWireSystemsAround(PipeBlockEntity holder) {
        Set<WireSystem.Element> starts = new HashSet<>();
        for (WirePart part : WirePart.VALUES) {
            WireSystem.Element element = WireSystem.Element.wire(holder.getBlockPos(), part);
            if (holder.wires().getColourOfPart(part) != null) starts.add(element);
            starts.addAll(WireSystem.connectedElements(holder, element));
        }
        starts.forEach(this::buildAndAddWireSystem);
    }

    boolean isEmitterEmitting(WireSystem.Element element, DyeColor colour) {
        if (!level.isLoaded(element.pos())) {
            WireEmitter last = emitters.get(element);
            return last != null && last.isEmitting(colour);
        }
        PipeBlockEntity holder = pipeAt(element.pos());
        if (holder != null && holder.getPluggable(element.emitterSide()) instanceof WireEmitter emitter) {
            emitters.put(element, emitter);
            return emitter.isEmitting(colour);
        }
        emitters.remove(element);
        return false;
    }

    /** Upstream {@code tick}, which ran every tick because nothing cleared its {@code gatesChanged} flag. */
    public void tick() {
        boolean changed = false;
        for (Map.Entry<WireSystem, Boolean> entry : systems.entrySet()) {
            boolean powered = entry.getKey().update(this);
            if (powered != entry.getValue()) {
                entry.setValue(powered);
                changed = true;
            }
        }
        if (changed || structureChanged) setDirty();
        structureChanged = false;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag entries = new ListTag();
        systems.forEach((system, powered) -> {
            CompoundTag entry = new CompoundTag();
            entry.put("wireSystem", system.save());
            entry.putBoolean("powered", powered);
            entries.add(entry);
        });
        tag.put("entries", entries);
        return tag;
    }

    private static WireSystems load(ServerLevel level, CompoundTag tag) {
        WireSystems data = new WireSystems(level);
        for (Tag element : tag.getList("entries", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) element;
            data.addWireSystem(WireSystem.load(entry.getCompound("wireSystem")), entry.getBoolean("powered"));
        }
        return data;
    }
}
