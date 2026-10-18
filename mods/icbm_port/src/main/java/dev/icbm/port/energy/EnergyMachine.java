package dev.icbm.port.energy;

/** Implemented by block entities exposing the Fabric energy lookup on their input sides. */
public interface EnergyMachine {
  EnergyBuffer energyBuffer();

  default boolean acceptsEnergy(net.minecraft.util.math.Direction side) {
    return true;
  }
}
