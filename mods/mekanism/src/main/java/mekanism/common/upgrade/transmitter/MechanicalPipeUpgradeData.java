package mekanism.common.upgrade.transmitter;

import mekanism.common.lib.transmitter.ConnectionType;
import ports.mekanism.lib.fluids.FluidStack;

public class MechanicalPipeUpgradeData extends TransmitterUpgradeData {

    public final FluidStack contents;

    public MechanicalPipeUpgradeData(boolean redstoneReactive, ConnectionType[] connectionTypes, FluidStack contents) {
        super(redstoneReactive, connectionTypes);
        this.contents = contents;
    }
}