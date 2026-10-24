package ports.gridwright.machine;

/**
 * Screen layouts. Both machines have an input slot and an output slot; they differ in what the
 * synchronised data means.
 */
public enum MachineLayout {
    /** Slots: fuel, empty container. Data: see {@link GeneratorBlockEntity#data()}. */
    GENERATOR(GeneratorBlockEntity.DATA_COUNT),
    /** Slots: input, output. Data: see {@link ResonatorBlockEntity#data()}. */
    RESONATOR(ResonatorBlockEntity.DATA_COUNT);

    public static final int INPUT_X = 44;
    public static final int OUTPUT_X = 116;
    public static final int SLOT_Y = 35;

    private final int dataCount;

    MachineLayout(int dataCount) {
        this.dataCount = dataCount;
    }

    public int slots() {
        return 2;
    }

    public int dataCount() {
        return dataCount;
    }
}
