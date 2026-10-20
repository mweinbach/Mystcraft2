package ports.buildcraft.builders.snapshot;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

/**
 * Upstream {@code SchematicBlockManager} with the API's {@code SchematicBlockFactoryRegistry}: the factory with
 * the highest priority whose predicate accepts a scanned block records it.
 */
public final class SchematicBlockManager {
    private static final List<Factory<?>> FACTORIES = new ArrayList<>();

    private SchematicBlockManager() {}

    /** Upstream {@code SchematicBlockFactory}. The instance class identifies the factory when writing. */
    public record Factory<S extends SchematicBlock>(ResourceLocation name, int priority, Predicate<SchematicBlockContext> predicate,
            Supplier<S> supplier, Class<?> clazz) {}

    public static synchronized <S extends SchematicBlock> void registerFactory(ResourceLocation name, int priority,
            Predicate<SchematicBlockContext> predicate, Supplier<S> supplier) {
        FACTORIES.removeIf(factory -> factory.name().equals(name));
        FACTORIES.add(new Factory<>(name, priority, predicate, supplier, supplier.get().getClass()));
        FACTORIES.sort(Comparator.<Factory<?>>comparingInt(Factory::priority).thenComparing(factory -> factory.name().toString()));
    }

    public static synchronized List<Factory<?>> getFactories() {
        return List.copyOf(FACTORIES);
    }

    public static SchematicBlock getSchematicBlock(SchematicBlockContext context) {
        List<Factory<?>> factories = getFactories();
        for (int i = factories.size() - 1; i >= 0; i--) {
            Factory<?> factory = factories.get(i);
            if (factory.predicate().test(context)) {
                SchematicBlock schematicBlock = factory.supplier().get();
                schematicBlock.init(context);
                return schematicBlock;
            }
        }
        throw new UnsupportedOperationException("No schematic accepts " + context.blockState());
    }

    @SuppressWarnings("unchecked")
    public static <S extends SchematicBlock> Factory<S> getFactoryByInstance(S instance) {
        return (Factory<S>) getFactories().stream()
                .filter(factory -> factory.clazz() == instance.getClass())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Didn't find a factory for " + instance.getClass()));
    }

    @Nullable
    public static Factory<?> getFactoryByName(ResourceLocation name) {
        return getFactories().stream().filter(factory -> factory.name().equals(name)).findFirst().orElse(null);
    }

    public static <S extends SchematicBlock> S createCleanCopy(S schematicBlock) {
        return getFactoryByInstance(schematicBlock).supplier().get();
    }

    public static CompoundTag writeToNBT(SchematicBlock schematicBlock) {
        CompoundTag tag = new CompoundTag();
        tag.putString("name", getFactoryByInstance(schematicBlock).name().toString());
        tag.put("data", schematicBlock.serializeNBT());
        return tag;
    }

    public static SchematicBlock readFromNBT(CompoundTag tag) throws InvalidSnapshotException {
        ResourceLocation name = ResourceLocation.tryParse(tag.getString("name"));
        Factory<?> factory = name == null ? null : getFactoryByName(name);
        if (factory == null) throw new InvalidSnapshotException("Unknown schematic type " + tag.getString("name"));
        SchematicBlock schematicBlock = factory.supplier().get();
        CompoundTag data = tag.getCompound("data");
        try {
            schematicBlock.deserializeNBT(data);
        } catch (InvalidSnapshotException e) {
            throw new InvalidSnapshotException("Failed to load the schematic from " + data, e);
        }
        return schematicBlock;
    }
}
