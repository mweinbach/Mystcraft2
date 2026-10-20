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
 * Upstream {@code SchematicEntityManager} with the API's {@code SchematicEntityFactoryRegistry}. Entities no
 * factory accepts are left out of blueprints.
 */
public final class SchematicEntityManager {
    private static final List<Factory<?>> FACTORIES = new ArrayList<>();

    private SchematicEntityManager() {}

    /** Upstream {@code SchematicEntityFactory}. */
    public record Factory<S extends SchematicEntity>(ResourceLocation name, int priority, Predicate<SchematicEntityContext> predicate,
            Supplier<S> supplier, Class<?> clazz) {}

    public static synchronized <S extends SchematicEntity> void registerFactory(ResourceLocation name, int priority,
            Predicate<SchematicEntityContext> predicate, Supplier<S> supplier) {
        FACTORIES.removeIf(factory -> factory.name().equals(name));
        FACTORIES.add(new Factory<>(name, priority, predicate, supplier, supplier.get().getClass()));
        FACTORIES.sort(Comparator.<Factory<?>>comparingInt(Factory::priority).thenComparing(factory -> factory.name().toString()));
    }

    public static synchronized List<Factory<?>> getFactories() {
        return List.copyOf(FACTORIES);
    }

    @Nullable
    public static SchematicEntity getSchematicEntity(SchematicEntityContext context) {
        List<Factory<?>> factories = getFactories();
        for (int i = factories.size() - 1; i >= 0; i--) {
            Factory<?> factory = factories.get(i);
            if (factory.predicate().test(context)) {
                SchematicEntity schematicEntity = factory.supplier().get();
                schematicEntity.init(context);
                return schematicEntity;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public static <S extends SchematicEntity> Factory<S> getFactoryByInstance(S instance) {
        return (Factory<S>) getFactories().stream()
                .filter(factory -> factory.clazz() == instance.getClass())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Didn't find a factory for " + instance.getClass()));
    }

    @Nullable
    public static Factory<?> getFactoryByName(ResourceLocation name) {
        return getFactories().stream().filter(factory -> factory.name().equals(name)).findFirst().orElse(null);
    }

    public static <S extends SchematicEntity> S createCleanCopy(S schematicEntity) {
        return getFactoryByInstance(schematicEntity).supplier().get();
    }

    public static CompoundTag writeToNBT(SchematicEntity schematicEntity) {
        CompoundTag tag = new CompoundTag();
        tag.putString("name", getFactoryByInstance(schematicEntity).name().toString());
        tag.put("data", schematicEntity.serializeNBT());
        return tag;
    }

    public static SchematicEntity readFromNBT(CompoundTag tag) throws InvalidSnapshotException {
        ResourceLocation name = ResourceLocation.tryParse(tag.getString("name"));
        Factory<?> factory = name == null ? null : getFactoryByName(name);
        if (factory == null) throw new InvalidSnapshotException("Unknown schematic type " + tag.getString("name"));
        SchematicEntity schematicEntity = factory.supplier().get();
        CompoundTag data = tag.getCompound("data");
        try {
            schematicEntity.deserializeNBT(data);
        } catch (InvalidSnapshotException e) {
            throw new InvalidSnapshotException("Failed to load the schematic from " + data, e);
        }
        return schematicEntity;
    }
}
