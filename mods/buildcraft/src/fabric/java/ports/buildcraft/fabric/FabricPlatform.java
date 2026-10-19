package ports.buildcraft.fabric;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.fabricmc.fabric.api.client.itemgroup.FabricItemGroupBuilder;
import net.fabricmc.fabric.api.registry.FuelRegistry;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorageUtil;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariantAttributeHandler;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariantAttributes;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
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
import ports.buildcraft.BuildCraft;
import ports.buildcraft.energy.BCFluid;
import ports.buildcraft.energy.BCFluidDefinition;
import ports.buildcraft.platform.EnergyPort;
import ports.buildcraft.platform.FluidPort;
import ports.buildcraft.platform.ItemPort;
import ports.buildcraft.platform.Platform;

/**
 * Fabric registers natively, but entries are queued and flushed in Forge's dependency order so the
 * shared registry classes resolve the same way on both loaders.
 */
final class FabricPlatform implements Platform.Backend {
    /** Fabric fluid viscosity is in ticks (water 5); shared code compares Forge's scale (water 1000). */
    private static final int FORGE_VISCOSITY_PER_TICK = 200;
    private static final List<ResourceKey<? extends Registry<?>>> ORDER = List.of(Registry.FLUID_REGISTRY, Registry.BLOCK_REGISTRY,
            Registry.ITEM_REGISTRY, Registry.BLOCK_ENTITY_TYPE_REGISTRY, Registry.MENU_REGISTRY);
    private final Map<ResourceLocation, List<Pending<?>>> pending = new LinkedHashMap<>();
    private boolean flushed;

    private static final class Pending<T> implements Supplier<T> {
        private final Registry<? super T> registry;
        private final ResourceLocation id;
        private final Supplier<T> factory;
        private T value;

        Pending(Registry<? super T> registry, ResourceLocation id, Supplier<T> factory) {
            this.registry = registry;
            this.id = id;
            this.factory = factory;
        }

        void register() {
            value = Registry.register(registry, id, factory.get());
        }

        @Override
        public T get() {
            return Objects.requireNonNull(value, () -> "Registry entry " + id + " is not registered yet");
        }
    }

    FabricPlatform() {
        for (ResourceKey<? extends Registry<?>> registry : ORDER) pending.put(registry.location(), new ArrayList<>());
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Supplier<T> register(ResourceKey<? extends Registry<? super T>> registry, String name, Supplier<T> factory) {
        if (flushed) throw new IllegalStateException("BuildCraft registration is closed: " + name);
        Registry<? super T> target = (Registry<? super T>) Registry.REGISTRY.get(registry.location());
        if (target == null) throw new IllegalArgumentException("Unknown registry " + registry.location());
        Pending<T> entry = new Pending<>(target, BuildCraft.id(name), factory);
        pending.computeIfAbsent(registry.location(), key -> new ArrayList<>()).add(entry);
        return entry;
    }

    /** Registers every queued entry; later registry groups may depend on earlier ones. */
    void flush() {
        flushed = true;
        for (List<Pending<?>> entries : pending.values()) for (Pending<?> entry : entries) entry.register();
        pending.clear();
    }

    @Override
    public <M extends AbstractContainerMenu> MenuType<M> menuType(Platform.MenuFactory<M> factory) {
        return new ExtendedScreenHandlerType<>(factory::create);
    }

    @Override
    public void openMenu(ServerPlayer player, MenuProvider provider, Consumer<FriendlyByteBuf> openingData) {
        player.openMenu(new ExtendedScreenHandlerFactory() {
            @Override public void writeScreenOpeningData(ServerPlayer target, FriendlyByteBuf buffer) { openingData.accept(buffer); }
            @Override public Component getDisplayName() { return provider.getDisplayName(); }
            @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player opener) { return provider.createMenu(id, inventory, opener); }
        });
    }

    @Override
    public CreativeModeTab creativeTab(String name, Supplier<ItemStack> icon) {
        return FabricItemGroupBuilder.build(BuildCraft.id(name), icon);
    }

    @Override
    public void defineFluid(BCFluidDefinition definition) {}

    @Override
    public BCFluid fluid(BCFluidDefinition definition, boolean source) {
        BCFluid fluid = new BCFluid(definition, source);
        FluidVariantAttributes.register(fluid, new FluidVariantAttributeHandler() {
            @Override public Component getName(FluidVariant variant) { return Component.translatable("fluid.buildcraft." + definition.name); }
            @Override public int getTemperature(FluidVariant variant) { return definition.temperature; }
            @Override public int getViscosity(FluidVariant variant, @Nullable Level level) { return Math.max(1, definition.viscosity / FORGE_VISCOSITY_PER_TICK); }
            @Override public boolean isLighterThanAir(FluidVariant variant) { return definition.isGaseous(); }
        });
        return fluid;
    }

    @Override
    public LiquidBlock liquidBlock(Supplier<? extends FlowingFluid> fluid, BlockBehaviour.Properties properties) {
        return new LiquidBlock(fluid.get(), properties) {};
    }

    @Override
    public Item bucket(Supplier<? extends Fluid> fluid, Item.Properties properties) {
        return new BucketItem(fluid.get(), properties);
    }

    @Override
    public Component fluidName(Fluid fluid) {
        return FluidVariantAttributes.getName(FluidVariant.of(fluid));
    }

    @Override
    public boolean isGaseous(Fluid fluid) {
        return FluidVariantAttributes.isLighterThanAir(FluidVariant.of(fluid));
    }

    @Override
    public int viscosity(Fluid fluid) {
        if (fluid instanceof BCFluid bc) return bc.definition().viscosity;
        return FluidVariantAttributes.getViscosity(FluidVariant.of(fluid), null) * FORGE_VISCOSITY_PER_TICK;
    }

    @Override
    public int burnTime(ItemStack stack) {
        if (stack.isEmpty()) return 0;
        Integer time = FuelRegistry.INSTANCE.get(stack.getItem());
        return time == null ? 0 : time;
    }

    @Override
    public ItemStack craftingRemainder(ItemStack stack) {
        Item remainder = stack.getItem().getCraftingRemainingItem();
        return remainder == null ? ItemStack.EMPTY : new ItemStack(remainder);
    }

    @Nullable @Override public EnergyPort findEnergy(Level level, BlockPos pos, Direction side) { return FabricPorts.findEnergy(level, pos, side); }
    @Nullable @Override public ItemPort findItems(Level level, BlockPos pos, Direction side) { return FabricPorts.findItems(level, pos, side); }
    @Nullable @Override public FluidPort findFluids(Level level, BlockPos pos, Direction side) { return FabricPorts.findFluids(level, pos, side); }

    @Override
    public boolean interactWithFluidContainer(Player player, InteractionHand hand, Level level, BlockPos pos, Direction side) {
        Storage<FluidVariant> storage = FluidStorage.SIDED.find(level, pos, side);
        return storage != null && FluidStorageUtil.interactWithFluidStorage(storage, player, hand);
    }
}
