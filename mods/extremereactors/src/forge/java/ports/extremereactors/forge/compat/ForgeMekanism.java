/*
 * Extreme Reactors 1.19.2 port - Forge adapter
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 *
 * PortGasHandler and the rotary recipe mappings are adapted from Extreme Reactors 2's FluidPortHandlerMekanism
 * (ZeroNoRyouki, MIT).
 */
package ports.extremereactors.forge.compat;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.zerono.mods.extremereactors.api.IMapping;
import it.zerono.mods.extremereactors.api.coolant.FluidMappingsRegistry;
import mekanism.api.Action;
import mekanism.api.chemical.gas.Gas;
import mekanism.api.chemical.gas.GasStack;
import mekanism.api.chemical.gas.IGasHandler;
import mekanism.api.recipes.RotaryRecipe;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import ports.extremereactors.corelib.compat.mekanism.MekanismGas;
import ports.extremereactors.corelib.fluid.FluidStack;
import ports.extremereactors.corelib.fluid.handler.IFluidHandler;
import ports.extremereactors.forge.ForgeTransfer;
import ports.extremereactors.platform.Platform;

import javax.annotation.Nullable;
import java.util.Map;

/**
 * Bridges {@link MekanismGas#HANDLER} to Mekanism's gas handler capability. Only loaded when Mekanism is.
 */
public final class ForgeMekanism {

    public static final Capability<IGasHandler> GAS_HANDLER = CapabilityManager.get(new CapabilityToken<>(){});

    public static void initialize() {
        ForgeTransfer.register(new ForgeTransfer.ForeignCapability<>(GAS_HANDLER, MekanismGas.HANDLER,
                PortGasHandler::new, ConsumerTank::new));
    }

    /**
     * What a Mekanism port offers: its machine's output tank, as the gas its fluid turns into in a rotary condensentrator.
     */
    private record PortGasHandler(IFluidHandler tank) implements IGasHandler {

        @Override
        public int getTanks() {
            return 1;
        }

        @Override
        public GasStack getChemicalInTank(final int idx) {
            return 0 != idx ? GasStack.EMPTY : getGasStack(this.tank.getFluidInTank(0));
        }

        @Override
        public void setChemicalInTank(final int idx, final GasStack stack) {
            // no insertions
        }

        @Override
        public long getTankCapacity(final int idx) {

            if (0 != idx) {
                return 0;
            }

            final int capacity = this.tank.getTankCapacity(0);
            final FluidStack stored = this.tank.getFluidInTank(0);
            final IMapping<Fluid, Gas> mapping = stored.isEmpty() ? null : getFluidMapping(stored.getFluid());

            // an empty tank has no gas to measure in yet, so its capacity is given in fluid units
            return null == mapping ? capacity : toGasAmount(mapping, capacity);
        }

        @Override
        public boolean isValid(final int idx, final GasStack stack) {
            // no insertions
            return false;
        }

        @Override
        public GasStack insertChemical(final int idx, final GasStack stack, final Action action) {
            // no insertions
            return stack;
        }

        @Override
        public GasStack extractChemical(final int idx, final long amount, final Action action) {

            final FluidStack currentStack = this.tank.getFluidInTank(0);

            if (0 != idx || currentStack.isEmpty()) {
                return GasStack.EMPTY;
            }

            final IMapping<Fluid, Gas> fluidMapping = getFluidMapping(currentStack.getFluid());

            if (null == fluidMapping) {
                return GasStack.EMPTY;
            }

            final IMapping<Gas, Fluid> gasMapping = getGasMapping(fluidMapping.getProduct());

            if (null == gasMapping) {
                return GasStack.EMPTY;
            }

            final int amountToRemove = gasMapping.getProductAmount((int) Math.min(amount, Integer.MAX_VALUE));
            final FluidStack removed = this.tank.drain(amountToRemove, action.simulate()
                    ? IFluidHandler.FluidAction.SIMULATE : IFluidHandler.FluidAction.EXECUTE);

            return getGasStack(removed);
        }
    }

    /**
     * A neighbouring Mekanism gas handler, as the tank a port would push its fluid into. Upstream's Mekanism ports are
     * always passive, so it only tells whether a port is connected.
     */
    private record ConsumerTank(IGasHandler gas) implements IFluidHandler {

        @Override
        public int getTanks() {
            return 0;
        }

        @Override
        public FluidStack getFluidInTank(final int tank) {
            return FluidStack.EMPTY;
        }

        @Override
        public int getTankCapacity(final int tank) {
            return 0;
        }

        @Override
        public boolean isFluidValid(final int tank, final FluidStack stack) {
            return !getGasStack(stack).isEmpty();
        }

        @Override
        public int fill(final FluidStack resource, final FluidAction action) {

            final GasStack offered = getGasStack(resource);

            if (offered.isEmpty()) {
                return 0;
            }

            final long accepted = offered.getAmount() - this.gas.insertChemical(offered,
                    action.simulate() ? Action.SIMULATE : Action.EXECUTE).getAmount();
            final IMapping<Gas, Fluid> gasMapping = getGasMapping(offered.getRaw());

            return null == gasMapping ? 0 : gasMapping.getProductAmount((int) accepted);
        }

        @Override
        public FluidStack drain(final FluidStack resource, final FluidAction action) {
            return FluidStack.EMPTY;
        }

        @Override
        public FluidStack drain(final int maxDrain, final FluidAction action) {
            return FluidStack.EMPTY;
        }
    }

    //region mappings

    private static GasStack getGasStack(final FluidStack fluidStack) {
        return fluidStack.isEmpty() ? GasStack.EMPTY : getGasStack(fluidStack.getFluid(), fluidStack.getAmount());
    }

    private static GasStack getGasStack(final Fluid fluid, final int amount) {

        final IMapping<Fluid, Gas> mapping = getFluidMapping(fluid);

        return null != mapping ? new GasStack(mapping.getProduct(), mapping.getProductAmount(amount)) : GasStack.EMPTY;
    }

    private static long toGasAmount(final IMapping<Fluid, Gas> mapping, final int fluidAmount) {
        return (long) (fluidAmount / mapping.getSourceAmount()) * mapping.getProductAmount();
    }

    @Nullable
    private static IMapping<Fluid, Gas> getFluidMapping(final Fluid fluid) {
        return mappings().fluidToGas().get(fluid);
    }

    @Nullable
    private static IMapping<Gas, Fluid> getGasMapping(final Gas gas) {
        return mappings().gasToFluid().get(gas);
    }

    /**
     * The mappings of the running server's rotary recipes. A reload or another world brings a new recipe manager,
     * which rebuilds them.
     */
    private static Mappings mappings() {

        final MinecraftServer server = Platform.currentServer();
        final RecipeManager recipes = null == server ? null : server.getRecipeManager();
        Mappings mappings = s_mappings;

        if (null == mappings || mappings.recipes() != recipes) {
            s_mappings = mappings = Mappings.build(recipes);
        }

        return mappings;
    }

    private record Mappings(@Nullable RecipeManager recipes, Map<Fluid, IMapping<Fluid, Gas>> fluidToGas,
                            Map<Gas, IMapping<Gas, Fluid>> gasToFluid) {

        static Mappings build(@Nullable final RecipeManager recipes) {

            final Mappings mappings = new Mappings(recipes, new Object2ObjectOpenHashMap<>(8),
                    new Object2ObjectOpenHashMap<>(8));

            if (null != recipes) {
                mappings.addRotaryRecipes(recipes);
            }

            return mappings;
        }

        private void addRotaryRecipes(final RecipeManager recipes) {

            final ResourceLocation typeId = new ResourceLocation(MekanismGas.MOD_ID, "rotary");
            @SuppressWarnings("unchecked")
            final RecipeType<RotaryRecipe> type = (RecipeType<RotaryRecipe>) Registry.RECIPE_TYPE.get(typeId);

            if (null == type) {
                return;
            }

            recipes.getAllRecipesFor(type).stream()
                    .filter(RotaryRecipe::hasFluidToGas)
                    .forEach(rotaryRecipe -> {

                        final GasStack gasStack = rotaryRecipe.getGasOutputDefinition().get(0);

                        rotaryRecipe.getFluidInput().getRepresentations().stream()
                                .filter(fluidStack -> FluidMappingsRegistry.hasVaporFrom(fluidStack.getFluid()))
                                .forEach(fluidStack -> {

                                    final IMapping<Fluid, Gas> mapping = IMapping.of(fluidStack.getFluid(),
                                            fluidStack.getAmount(), gasStack.getRaw(), (int) gasStack.getAmount());

                                    this.fluidToGas.put(fluidStack.getFluid(), mapping);
                                    this.gasToFluid.put(mapping.getProduct(), mapping.getReverse());
                                });
                    });
        }
    }

    @Nullable
    private static volatile Mappings s_mappings;

    //endregion

    private ForgeMekanism() {
    }
}
