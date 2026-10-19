package ports.buildcraft.forge;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import ports.buildcraft.BuildCraft;
import ports.buildcraft.energy.BCFluid;
import ports.buildcraft.energy.BCFluidDefinition;
import ports.buildcraft.platform.EnergyPort;
import ports.buildcraft.platform.FluidPort;
import ports.buildcraft.platform.ItemPort;
import ports.buildcraft.platform.Platform;

/** Forge registration through deferred registers; neighbours are reached through Forge capabilities. */
final class ForgePlatform implements Platform.Backend {
    private final IEventBus modBus;
    private final Map<ResourceKey<?>, DeferredRegister<?>> registers = new HashMap<>();
    private final Map<BCFluidDefinition, Supplier<FluidType>> fluidTypes = new HashMap<>();

    ForgePlatform(IEventBus modBus) {
        this.modBus = modBus;
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public synchronized <T> Supplier<T> register(ResourceKey<? extends Registry<? super T>> registry, String name, Supplier<T> factory) {
        DeferredRegister deferred = registers.computeIfAbsent(registry, key -> {
            DeferredRegister<?> created = DeferredRegister.create((ResourceKey) key, BuildCraft.MOD_ID);
            created.register(modBus);
            return created;
        });
        return deferred.register(name, factory);
    }

    @Override
    public <M extends AbstractContainerMenu> MenuType<M> menuType(Platform.MenuFactory<M> factory) {
        return IForgeMenuType.create(factory::create);
    }

    @Override
    public void openMenu(ServerPlayer player, MenuProvider provider, Consumer<FriendlyByteBuf> openingData) {
        NetworkHooks.openScreen(player, provider, openingData);
    }

    @Override
    public CreativeModeTab creativeTab(String name, Supplier<ItemStack> icon) {
        return new CreativeModeTab(BuildCraft.MOD_ID + "." + name) {
            @Override public ItemStack makeIcon() { return icon.get(); }
        };
    }

    @Override
    public void defineFluid(BCFluidDefinition definition) {
        fluidTypes.put(definition, register(ForgeRegistries.Keys.FLUID_TYPES, definition.name, () -> new ForgeFluidType(definition)));
    }

    @Override
    public BCFluid fluid(BCFluidDefinition definition, boolean source) {
        Supplier<FluidType> type = fluidTypes.get(definition);
        return new BCFluid(definition, source) {
            @Override public FluidType getFluidType() { return type.get(); }
        };
    }

    @Override
    public LiquidBlock liquidBlock(Supplier<? extends FlowingFluid> fluid, BlockBehaviour.Properties properties) {
        return new LiquidBlock(fluid, properties);
    }

    @Override
    public Item bucket(Supplier<? extends Fluid> fluid, Item.Properties properties) {
        return new BucketItem(fluid, properties);
    }

    @Override public Component fluidName(Fluid fluid) { return fluid.getFluidType().getDescription(); }
    @Override public boolean isGaseous(Fluid fluid) { return fluid.getFluidType().isLighterThanAir(); }
    @Override public int viscosity(Fluid fluid) { return fluid.getFluidType().getViscosity(); }
    @Override public int burnTime(ItemStack stack) { return stack.isEmpty() ? 0 : ForgeHooks.getBurnTime(stack, null); }
    @Override public ItemStack craftingRemainder(ItemStack stack) { return stack.getCraftingRemainingItem(); }

    @Nullable
    @Override
    public EnergyPort findEnergy(Level level, BlockPos pos, Direction side) {
        return ForgePorts.findEnergy(level, pos, side);
    }

    @Nullable
    @Override
    public ItemPort findItems(Level level, BlockPos pos, Direction side) {
        return ForgePorts.findItems(level, pos, side);
    }

    @Nullable
    @Override
    public FluidPort findFluids(Level level, BlockPos pos, Direction side) {
        return ForgePorts.findFluids(level, pos, side);
    }

    @Override
    public boolean interactWithFluidContainer(Player player, InteractionHand hand, Level level, BlockPos pos, Direction side) {
        return FluidUtil.interactWithFluidHandler(player, hand, level, pos, side);
    }
}
