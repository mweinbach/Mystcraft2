package ports.buildcraft.transport.wire;

import net.minecraft.world.item.DyeColor;

/** Upstream {@code IWireEmitter}: a pluggable that can power the wires of its pipe. */
public interface WireEmitter {
    /** Whether this is emitting {@code colour} this tick. */
    boolean isEmitting(DyeColor colour);

    /** Emits {@code colour} for this tick. */
    void emitWire(DyeColor colour);
}
