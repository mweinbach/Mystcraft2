package ports.buildcraft.forge;

import java.util.function.Consumer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.fluids.FluidType;
import ports.buildcraft.energy.BCFluidDefinition;

/**
 * The Forge {@link FluidType} for a shared fluid definition. Entities do not swim in these fluids,
 * matching the Fabric build, which has no fluid physics hook.
 */
final class ForgeFluidType extends FluidType {
    private final BCFluidDefinition definition;

    ForgeFluidType(BCFluidDefinition definition) {
        super(Properties.create()
                .descriptionId("fluid.buildcraft." + definition.name)
                .density(definition.density)
                .viscosity(definition.viscosity)
                .temperature(definition.temperature)
                .canSwim(false)
                .canDrown(false)
                .canPushEntity(false)
                .canConvertToSource(false));
        this.definition = definition;
    }

    @Override
    public void initializeClient(Consumer<IClientFluidTypeExtensions> consumer) {
        consumer.accept(new IClientFluidTypeExtensions() {
            @Override public ResourceLocation getStillTexture() { return definition.stillTexture(); }
            @Override public ResourceLocation getFlowingTexture() { return definition.flowingTexture(); }
        });
    }
}
