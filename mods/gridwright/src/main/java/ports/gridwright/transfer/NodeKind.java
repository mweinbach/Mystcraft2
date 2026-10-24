package ports.gridwright.transfer;

/**
 * The four nodes. A transfer node pushes out of the inventory (or tank) it faces into the pipe network;
 * a retrieval node pulls from the network into the one it faces.
 */
public enum NodeKind {
    ITEM_TRANSFER("transfer_node", false, false),
    FLUID_TRANSFER("fluid_transfer_node", true, false),
    ITEM_RETRIEVAL("retrieval_node", false, true),
    FLUID_RETRIEVAL("fluid_retrieval_node", true, true);

    /** Items moved per operation without and with a stack upgrade. */
    public static final int ITEMS = 1;
    public static final int ITEMS_STACKED = 64;
    /** Millibuckets moved per operation without and with a stack upgrade. */
    public static final long FLUID = 100;
    public static final long FLUID_STACKED = 1000;

    private final String id;
    private final boolean fluid;
    private final boolean retrieval;

    NodeKind(String id, boolean fluid, boolean retrieval) {
        this.id = id;
        this.fluid = fluid;
        this.retrieval = retrieval;
    }

    public String id() {
        return id;
    }

    public boolean fluid() {
        return fluid;
    }

    public boolean retrieval() {
        return retrieval;
    }
}
