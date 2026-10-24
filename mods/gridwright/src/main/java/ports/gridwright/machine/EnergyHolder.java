package ports.gridwright.machine;

/**
 * A block entity whose energy the loader adapters expose to other mods, in the directions its
 * {@link EnergyBuffer} allows (generators: extraction only; the ender quarry: insertion only).
 */
public interface EnergyHolder {
    EnergyBuffer energy();
}
