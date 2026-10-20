/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.base.multiblock;

import net.minecraft.world.level.Level;
import ports.extremereactors.multiblock.cuboid.AbstractCuboidMultiblockController;
import ports.extremereactors.multiblock.variant.IMultiblockDimensionVariant;

/**
 * A cuboid multiblock controller whose size limits come from its dimension variant.
 *
 * <p>Besides the variant plumbing it tracks whether the machine interior can be seen and rate-limits the
 * updates sent to the players that are watching the machine.</p>
 */
public abstract class AbstractMultiblockController<Controller extends AbstractMultiblockController<Controller, V>,
                                                   V extends IMultiblockDimensionVariant>
        extends AbstractCuboidMultiblockController<Controller> {

    /**
     * Ticks between two consecutive client updates sent by {@link #checkAndSendClientUpdates()}.
     */
    public static final int CLIENT_UPDATE_INTERVAL = 5;

    protected AbstractMultiblockController(final Level world) {

        super(world);
        this._interiorInvisible = false;
        this._ticksSinceLastClientUpdate = 0;
    }

    public abstract V getVariant();

    //region interior visibility

    public boolean isInteriorInvisible() {
        return this._interiorInvisible;
    }

    public boolean isInteriorVisible() {
        return !this._interiorInvisible;
    }

    protected void setInteriorInvisible(final boolean invisible) {
        this._interiorInvisible = invisible;
    }

    //endregion
    //region client updates

    /**
     * Send the current machine state to the players watching it.
     */
    protected void sendClientUpdates() {
    }

    /**
     * Call {@link #sendClientUpdates()} once every {@link #CLIENT_UPDATE_INTERVAL} calls. Meant to be called
     * once per server tick.
     */
    protected void checkAndSendClientUpdates() {

        if (++this._ticksSinceLastClientUpdate >= CLIENT_UPDATE_INTERVAL) {

            this._ticksSinceLastClientUpdate = 0;
            this.sendClientUpdates();
        }
    }

    //endregion
    //region AbstractMultiblockController

    @Override
    protected int getMinimumNumberOfPartsForAssembledMachine() {
        return this.getVariant().getMinimumPartsCount();
    }

    @Override
    protected int getMaximumXSize() {
        return this.getVariant().getMaximumXSize();
    }

    @Override
    protected int getMaximumZSize() {
        return this.getVariant().getMaximumZSize();
    }

    @Override
    protected int getMaximumYSize() {
        return this.getVariant().getMaximumYSize();
    }

    /**
     * A machine coming back from the paused state is handled as a freshly assembled one.
     */
    @Override
    protected void onMachineRestored() {
        this.onMachineAssembled();
    }

    /**
     * Pausing (usually a chunk unload) keeps the machine state as it is.
     */
    @Override
    protected void onMachinePaused() {
    }

    @Override
    protected void updateClient() {
    }

    //endregion
    //region internals

    private boolean _interiorInvisible;
    private int _ticksSinceLastClientUpdate;

    //endregion
}
