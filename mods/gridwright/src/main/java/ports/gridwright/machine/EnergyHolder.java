package ports.gridwright.machine;

/** A block entity whose energy the loader adapters expose to other mods (extraction only). */
public interface EnergyHolder {
    EnergyBuffer energy();
}
