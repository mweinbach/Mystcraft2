package ports.buildcraft.transport;

import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.state.BlockBehaviour;
import ports.buildcraft.transport.behaviour.PipeBehaviour;

/**
 * Upstream {@code PipeDefinition} plus its {@code BCTransportConfig} transfer rates. Each pipe is its
 * own block so that its connections can be described by a vanilla multipart model.
 */
public final class PipeDefinition {
    public enum Flow { ITEM, FLUID, POWER, STRUCTURE }

    /** The pipe name without prefix, such as {@code wood_item}; registered as {@code pipe_<name>}. */
    public final String name;
    public final Flow flow;
    final Function<PipeBlockEntity, PipeBehaviour> behaviour;
    /** Texture of each face (by {@code Direction} ordinal) and of the inventory item, under {@code block/pipe/}. */
    public final String[] faceTextures;
    @Nullable
    public final String specialTexture;
    public final String itemTexture;
    public final int fluidTransferPerTick;
    public final int fluidTransferDelay;
    /** Micro-MJ per tick. */
    public final long powerTransferPerTick;
    public final boolean powerReceiver;
    /** True when using the pipe opens a screen (diamond pipes). */
    public final boolean hasMenu;
    final BiFunction<BlockBehaviour.Properties, PipeDefinition, PipeBlock> blockFactory;
    Supplier<PipeBlock> block;
    Supplier<Item> item;

    private PipeDefinition(Builder builder) {
        name = builder.name;
        flow = builder.flow;
        behaviour = builder.behaviour;
        faceTextures = builder.faceTextures;
        specialTexture = builder.specialTexture;
        itemTexture = builder.itemTexture;
        fluidTransferPerTick = builder.fluidTransferPerTick;
        fluidTransferDelay = builder.fluidTransferDelay;
        powerTransferPerTick = builder.powerTransferPerTick;
        powerReceiver = builder.powerReceiver;
        hasMenu = builder.hasMenu;
        blockFactory = builder.blockFactory;
    }

    public String registryName() {
        return "pipe_" + name;
    }

    public PipeBlock block() {
        return block.get();
    }

    public Item item() {
        return item.get();
    }

    @SafeVarargs
    static Builder builder(String name, Flow flow, Function<PipeBlockEntity, PipeBehaviour> behaviour, List<PipeDefinition>... lists) {
        return new Builder(name, flow, behaviour, lists);
    }

    static final class Builder {
        private final String name;
        private final Flow flow;
        private final Function<PipeBlockEntity, PipeBehaviour> behaviour;
        private String[] faceTextures;
        private String specialTexture;
        private String itemTexture;
        private int fluidTransferPerTick;
        private int fluidTransferDelay = 10;
        private long powerTransferPerTick;
        private boolean powerReceiver;
        private boolean hasMenu;
        private BiFunction<BlockBehaviour.Properties, PipeDefinition, PipeBlock> blockFactory = PipeBlock::new;
        private final List<PipeDefinition>[] lists;

        private Builder(String name, Flow flow, Function<PipeBlockEntity, PipeBehaviour> behaviour, List<PipeDefinition>[] lists) {
            this.lists = lists;
            this.name = name;
            this.flow = flow;
            this.behaviour = behaviour;
            texture(name);
        }

        Builder texture(String texture) {
            faceTextures = new String[] {texture, texture, texture, texture, texture, texture};
            itemTexture = texture;
            return this;
        }

        Builder faceTextures(String[] textures, String item) {
            faceTextures = textures;
            itemTexture = item;
            return this;
        }

        Builder special(String texture) {
            specialTexture = texture;
            return this;
        }

        Builder fluid(int transferPerTick, int delay) {
            fluidTransferPerTick = transferPerTick;
            fluidTransferDelay = delay;
            return this;
        }

        Builder power(long transferPerTick, boolean receiver) {
            powerTransferPerTick = Math.max(10, transferPerTick);
            powerReceiver = receiver;
            return this;
        }

        Builder menu() {
            hasMenu = true;
            return this;
        }

        /** The block class, for pipes whose model reads more block state than their connections. */
        Builder block(BiFunction<BlockBehaviour.Properties, PipeDefinition, PipeBlock> factory) {
            blockFactory = factory;
            return this;
        }

        /** Builds the definition and appends it to the lists given to {@link #builder}. */
        PipeDefinition build() {
            PipeDefinition definition = new PipeDefinition(this);
            for (List<PipeDefinition> list : lists) list.add(definition);
            return definition;
        }
    }
}
