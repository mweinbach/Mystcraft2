package ports.gridwright.fabric;

import ports.gridwright.Gridwright;
import ports.gridwright.platform.Platform;
import net.fabricmc.fabric.api.client.itemgroup.FabricItemGroupBuilder;
import net.fabricmc.fabric.api.registry.FuelRegistry;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import team.reborn.energy.api.EnergyStorage;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorageUtil;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Fabric registers natively, but entries are queued and flushed blocks first, then items, then the
 * rest, as Forge's registry events do, so shared suppliers resolve the same way on both loaders.
 */
@SuppressWarnings("UnstableApiUsage")
final class FabricPlatform implements Platform.Backend {

    private static final List<ResourceLocation> FIRST = List.of(Registry.BLOCK_REGISTRY.location(), Registry.ITEM_REGISTRY.location());
    private final Map<ResourceLocation, List<Pending<?>>> pending = new LinkedHashMap<>();
    private boolean flushed;

    FabricPlatform() {
        for (ResourceLocation registry : FIRST) pending.put(registry, new ArrayList<>());
    }

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

    @Override
    @SuppressWarnings("unchecked")
    public <T> Supplier<T> register(ResourceKey<? extends Registry<? super T>> registry, String name, Supplier<T> factory) {
        if (flushed) throw new IllegalStateException("Gridwright registration is closed: " + name);
        Registry<? super T> target = (Registry<? super T>) Registry.REGISTRY.get(registry.location());
        if (target == null) throw new IllegalArgumentException("Unknown registry " + registry.location());
        Pending<T> entry = new Pending<>(target, new ResourceLocation(Gridwright.MOD_ID, name), factory);
        pending.computeIfAbsent(registry.location(), key -> new ArrayList<>()).add(entry);
        return entry;
    }

    /** Registers every queued entry; later registry groups may depend on earlier ones. */
    void flush() {
        flushed = true;
        for (List<Pending<?>> entries : pending.values()) {
            for (Pending<?> entry : entries) entry.register();
        }
        pending.clear();
    }

    @Override
    public <M extends AbstractContainerMenu> MenuType<M> menuType(Platform.MenuFactory<M> factory) {
        return new ExtendedScreenHandlerType<>(factory::create);
    }

    @Override
    public void openMenu(ServerPlayer player, MenuProvider provider, BlockPos pos) {
        player.openMenu(new ExtendedScreenHandlerFactory() {
            @Override
            public void writeScreenOpeningData(ServerPlayer target, FriendlyByteBuf buf) {
                buf.writeBlockPos(pos);
            }

            @Override
            public Component getDisplayName() {
                return provider.getDisplayName();
            }

            @Override
            public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player opener) {
                return provider.createMenu(containerId, inventory, opener);
            }
        });
    }

    @Override
    public CreativeModeTab creativeTab(ResourceLocation id, Supplier<ItemStack> icon) {
        return FabricItemGroupBuilder.build(id, icon);
    }

    @Override
    public int burnTime(ItemStack stack) {
        Integer ticks = FuelRegistry.INSTANCE.get(stack.getItem());
        return ticks == null ? 0 : ticks;
    }

    @Override
    public long pushEnergy(ServerLevel level, BlockPos target, Direction face, long amount) {
        EnergyStorage storage = EnergyStorage.SIDED.find(level, target, face);
        if (storage == null || !storage.supportsInsertion()) return 0;
        try (Transaction transaction = Transaction.openOuter()) {
            long inserted = storage.insert(amount, transaction);
            transaction.commit();
            return inserted;
        }
    }

    @Override
    public void openMenu(ServerPlayer player, MenuProvider provider, Consumer<FriendlyByteBuf> data) {
        player.openMenu(new ExtendedScreenHandlerFactory() {
            @Override
            public void writeScreenOpeningData(ServerPlayer target, FriendlyByteBuf buf) {
                data.accept(buf);
            }

            @Override
            public Component getDisplayName() {
                return provider.getDisplayName();
            }

            @Override
            public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player opener) {
                return provider.createMenu(containerId, inventory, opener);
            }
        });
    }

    @Override
    public ServerPlayer fakePlayer(ServerLevel level) {
        return FabricFakePlayer.get(level);
    }

    @Override
    public boolean mayUseBlock(ServerPlayer player, InteractionHand hand, BlockHitResult hit) {
        // Claim mods cancel UseBlockCallback; any answer but PASS means another mod handled or refused it.
        return UseBlockCallback.EVENT.invoker().interact(player, player.level, hand, hit) == InteractionResult.PASS;
    }

    @Override
    public long bucketVolume() {
        return FluidConstants.BUCKET;
    }

    @Override
    public boolean interactWithFluidStorage(Player player, InteractionHand hand, Level level, BlockPos pos, Direction side) {
        Storage<FluidVariant> storage = FluidStorage.SIDED.find(level, pos, side);
        return storage != null && FluidStorageUtil.interactWithFluidStorage(storage, player, hand);
    }
}
