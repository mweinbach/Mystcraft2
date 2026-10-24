package ports.gridwright.block;

/** What a trash can destroys. */
public enum TrashKind {
    ITEM("trash_can"),
    FLUID("fluid_trash_can"),
    ENERGY("energy_trash_can");

    private final String registryName;

    TrashKind(String registryName) {
        this.registryName = registryName;
    }

    public String registryName() {
        return registryName;
    }
}
