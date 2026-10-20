package ports.buildcraft.transport.pluggable;

import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import ports.buildcraft.transport.PipeBlockEntity;

/**
 * Upstream {@code PluggableDefinition} and {@code PluggableRegistry}. Upstream's separate NBT and
 * network loaders become one creator: the pluggable is made in its default state and then reads
 * either its saved or its client data.
 */
public final class PluggableDefinition {
    @FunctionalInterface
    public interface Creator {
        PipePluggable create(PluggableDefinition definition, PipeBlockEntity holder, Direction side);
    }

    private static final Map<ResourceLocation, PluggableDefinition> REGISTRY = new HashMap<>();

    public final ResourceLocation identifier;
    private final Creator creator;

    private PluggableDefinition(ResourceLocation identifier, Creator creator) {
        this.identifier = identifier;
        this.creator = creator;
    }

    public static PluggableDefinition register(ResourceLocation identifier, Creator creator) {
        PluggableDefinition definition = new PluggableDefinition(identifier, creator);
        if (REGISTRY.putIfAbsent(identifier, definition) != null) throw new IllegalStateException("Duplicate pluggable " + identifier);
        return definition;
    }

    @Nullable
    public static PluggableDefinition get(ResourceLocation identifier) {
        return REGISTRY.get(identifier);
    }

    public PipePluggable create(PipeBlockEntity holder, Direction side) {
        return creator.create(this, holder, side);
    }
}
