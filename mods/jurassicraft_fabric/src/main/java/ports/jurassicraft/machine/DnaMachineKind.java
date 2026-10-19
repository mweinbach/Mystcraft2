package ports.jurassicraft.machine;

public enum DnaMachineKind {
    EXTRACTOR("dna_extractor", 6, 2, 2000),
    SEQUENCER("dna_sequencer", 9, 6, 2000),
    COMBINATOR("dna_combinator_hybridizer", 12, 2, 1000),
    SYNTHESIZER("dna_synthesizer", 7, 3, 1000);

    public final String id;
    public final int slots;
    public final int inputs;
    public final int ticks;
    DnaMachineKind(String id, int slots, int inputs, int ticks) {
        this.id = id; this.slots = slots; this.inputs = inputs; this.ticks = ticks;
    }
    public int lanes() { return this == SEQUENCER ? 3 : 1; }
}
