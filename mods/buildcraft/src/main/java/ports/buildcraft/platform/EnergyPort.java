package ports.buildcraft.platform;

import javax.annotation.Nullable;

/**
 * An energy storage measured in loader energy units: Forge Energy on Forge and Team Reborn Energy
 * on Fabric. Simulated calls never change state.
 *
 * <p>Transactional loaders (Fabric) roll back an executed call with {@link #snapshot()} and
 * {@link #restore(Object)}. A port whose snapshot is null cannot be rolled back; the Fabric adapter
 * then only simulates inside a transaction and executes the accumulated amount once the outermost
 * transaction commits.
 */
public interface EnergyPort {
    long receive(long amount, boolean simulate);

    long extract(long amount, boolean simulate);

    long stored();

    long capacity();

    boolean canReceive();

    boolean canExtract();

    /**
     * Upstream redstone engines only power BuildCraft's redstone-receivable machines (wooden pipes and
     * the pump); the energy APIs have no such marker, so it lives here.
     */
    default boolean acceptsRedstoneEngine() {
        return false;
    }

    @Nullable
    default Object snapshot() {
        return null;
    }

    default void restore(Object snapshot) {}

    /** Called once after a transaction that changed this storage commits. */
    default void onCommitted() {}
}
