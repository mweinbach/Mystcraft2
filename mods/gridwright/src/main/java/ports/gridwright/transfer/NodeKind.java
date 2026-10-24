package ports.gridwright.transfer;

/**
 * The nodes. A transfer node pushes out of the inventory, tank or energy store it faces into the pipe
 * network; a retrieval node pulls from the network into the one it faces.
 */
public enum NodeKind {
    ITEM_TRANSFER("transfer_node", Resource.ITEMS, false),
    FLUID_TRANSFER("fluid_transfer_node", Resource.FLUIDS, false),
    ITEM_RETRIEVAL("retrieval_node", Resource.ITEMS, true),
    FLUID_RETRIEVAL("fluid_retrieval_node", Resource.FLUIDS, true),
    /** Phase 3: moves Forge Energy / Team Reborn Energy every tick rather than once per operation. */
    ENERGY_TRANSFER("energy_transfer_node", Resource.ENERGY, false);

    /** What a node moves, and so which neighbours of its pipes count as targets. */
    public enum Resource { ITEMS, FLUIDS, ENERGY }

    /** Items moved per operation without and with a stack upgrade. */
    public static final int ITEMS = 1;
    public static final int ITEMS_STACKED = 64;
    /** Millibuckets moved per operation without and with a stack upgrade. */
    public static final long FLUID = 100;
    public static final long FLUID_STACKED = 1000;
    /** Energy moved per tick, and added per speed upgrade; a stack upgrade multiplies the total. */
    public static final long ENERGY = 1000;
    public static final int ENERGY_STACK_FACTOR = 4;

    private final String id;
    private final Resource resource;
    private final boolean retrieval;

    NodeKind(String id, Resource resource, boolean retrieval) {
        this.id = id;
        this.resource = resource;
        this.retrieval = retrieval;
    }

    public String id() {
        return id;
    }

    public Resource resource() {
        return resource;
    }

    public boolean fluid() {
        return resource == Resource.FLUIDS;
    }

    public boolean energy() {
        return resource == Resource.ENERGY;
    }

    public boolean retrieval() {
        return retrieval;
    }
}
