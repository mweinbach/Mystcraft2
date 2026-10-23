package ports.buildcraft.fabric;

import com.mojang.authlib.GameProfile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.fabricmc.fabric.api.client.itemgroup.FabricItemGroupBuilder;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.registry.FuelRegistry;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorageUtil;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariantAttributeHandler;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariantAttributes;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageUtil;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import ports.buildcraft.BuildCraft;
import ports.buildcraft.energy.BCFluid;
import ports.buildcraft.energy.BCFluidDefinition;
import ports.buildcraft.platform.EnergyPort;
import ports.buildcraft.platform.FluidPort;
import ports.buildcraft.lib.FluidAmount;
import ports.buildcraft.platform.ItemFluids;
import ports.buildcraft.platform.ItemPort;
import ports.buildcraft.silicon.plug.FacadeItem;
import ports.buildcraft.platform.Platform;

/**
 * Fabric registers natively, but entries are queued and flushed in Forge's dependency order so the
 * shared registry classes resolve the same way on both loaders.
 */
final class FabricPlatform implements Platform.Backend {
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
            @Override public int getViscosity(FluidVariant variant, @Nullable Level level) { return definition.viscosity; }
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
        // Fabric's FluidConstants use Forge's scale too: water 1000, lava 6000 (2000 in ultrawarm dimensions).
        return FluidVariantAttributes.getViscosity(FluidVariant.of(fluid), null);
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

    @Override public void invalidatePorts(BlockEntity tile) { FabricPorts.invalidate(tile); }

    @Override
    public boolean interactWithFluidContainer(Player player, InteractionHand hand, Level level, BlockPos pos, Direction side) {
        Storage<FluidVariant> storage = FluidStorage.SIDED.find(level, pos, side);
        return storage != null && FluidStorageUtil.interactWithFluidStorage(storage, player, hand);
    }

    @Nullable
    @Override
    public ItemFluids itemFluids(ItemStack stack) {
        ItemStack single = stack.copy();
        single.setCount(1);
        ContainerItemContext context = ContainerItemContext.withInitial(single);
        Storage<FluidVariant> storage = context.find(FluidStorage.ITEM);
        if (storage == null) return null;
        return new ItemFluids() {
            @Override
            public FluidAmount drain(long max, boolean simulate) {
                FluidVariant fluid = StorageUtil.findStoredResource(storage, variant -> !variant.hasNbt());
                if (fluid == null) return FluidAmount.EMPTY;
                long moved = wholeMillibuckets(max, simulate, (droplets, transaction) -> storage.extract(fluid, droplets, transaction));
                return moved <= 0 ? FluidAmount.EMPTY : new FluidAmount(fluid.getFluid(), moved);
            }

            @Override
            public long fill(FluidAmount fluid, boolean simulate) {
                if (fluid.isEmpty()) return 0;
                FluidVariant variant = FluidVariant.of(fluid.fluid());
                return wholeMillibuckets(fluid.amount(), simulate, (droplets, transaction) -> storage.insert(variant, droplets, transaction));
            }

            @Override
            public ItemStack container() {
                ItemVariant variant = context.getItemVariant();
                return variant.isBlank() ? ItemStack.EMPTY : variant.toStack((int) context.getAmount());
            }
        };
    }

    private interface DropletMove {
        long move(long droplets, Transaction transaction);
    }

    /** Moves the most whole millibuckets, up to {@code max}, that the storage accepts in full. */
    private static long wholeMillibuckets(long max, boolean simulate, DropletMove move) {
        long mb;
        try (Transaction transaction = Transaction.openOuter()) {
            mb = move.move(max * FabricPorts.DROPLETS_PER_MB, transaction) / FabricPorts.DROPLETS_PER_MB;
        }
        if (mb <= 0 || simulate) return Math.max(0, mb);
        try (Transaction transaction = Transaction.openOuter()) {
            if (move.move(mb * FabricPorts.DROPLETS_PER_MB, transaction) != mb * FabricPorts.DROPLETS_PER_MB) return 0;
            transaction.commit();
        }
        return mb;
    }

    @Override
    public SoundEvent bucketSound(Fluid fluid, boolean fill) {
        FluidVariant variant = FluidVariant.of(fluid);
        return fill ? FluidVariantAttributes.getFillSound(variant) : FluidVariantAttributes.getEmptySound(variant);
    }

    @Override
    public void sendToPlayer(ServerPlayer player, ResourceLocation channel, Consumer<FriendlyByteBuf> payload) {
        FriendlyByteBuf buffer = PacketByteBufs.create();
        payload.accept(buffer);
        ServerPlayNetworking.send(player, channel, buffer);
    }

    @Override
    public Path gameDirectory() {
        return FabricLoader.getInstance().getGameDir();
    }

    @Override
    public List<String> modIds() {
        return FabricLoader.getInstance().getAllMods().stream().map(mod -> mod.getMetadata().getId()).toList();
    }

    @Override
    public Optional<Path> modResource(String modId, String path) {
        return FabricLoader.getInstance().getModContainer(modId).flatMap(mod -> mod.findPath(path)).filter(Files::isRegularFile);
    }

    @Override
    public Item createFacadeItem(Item.Properties properties) {
        return new FacadeItem(properties);
    }

    @Override
    public ServerPlayer fakePlayer(ServerLevel level, GameProfile profile) {
        return FabricFakePlayer.get(level, profile);
    }
}
