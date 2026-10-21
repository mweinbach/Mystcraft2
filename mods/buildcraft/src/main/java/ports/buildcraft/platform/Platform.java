package ports.buildcraft.platform;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import ports.buildcraft.energy.BCFluid;
import ports.buildcraft.energy.BCFluidDefinition;

/**
 * Loader services used by shared gameplay. Each loader entrypoint installs its backend before any
 * registry class initializes; blocks, items, fluids and rules are defined once in shared code.
 */
public final class Platform {
    public interface Backend {
        <T> Supplier<T> register(ResourceKey<? extends Registry<? super T>> registry, String name, Supplier<T> factory);
        <M extends AbstractContainerMenu> MenuType<M> menuType(MenuFactory<M> factory);
        void openMenu(ServerPlayer player, MenuProvider provider, Consumer<FriendlyByteBuf> openingData);
        CreativeModeTab creativeTab(String name, Supplier<ItemStack> icon);

        /** Called during mod construction for each fluid, before any registry event. */
        void defineFluid(BCFluidDefinition definition);
        BCFluid fluid(BCFluidDefinition definition, boolean source);
        LiquidBlock liquidBlock(Supplier<? extends FlowingFluid> fluid, BlockBehaviour.Properties properties);
        Item bucket(Supplier<? extends Fluid> fluid, Item.Properties properties);
        Component fluidName(Fluid fluid);
        boolean isGaseous(Fluid fluid);
        int viscosity(Fluid fluid);

        int burnTime(ItemStack stack);
        ItemStack craftingRemainder(ItemStack stack);

        /** The loader's energy storage on {@code side} of the block at {@code pos}, in loader energy units. */
        @Nullable EnergyPort findEnergy(Level level, BlockPos pos, Direction side);
        @Nullable ItemPort findItems(Level level, BlockPos pos, Direction side);
        @Nullable FluidPort findFluids(Level level, BlockPos pos, Direction side);
        /** Called when one of a {@link PortHolder}'s ports appears or disappears, so the loader drops what it cached. */
        void invalidatePorts(BlockEntity tile);
        /** Fills or empties a held fluid container against the block's fluid storage. */
        boolean interactWithFluidContainer(Player player, InteractionHand hand, Level level, BlockPos pos, Direction side);
        /** The fluid container in a copy of {@code stack}, counted as one item, or null if it holds no fluid storage. */
        @Nullable ItemFluids itemFluids(ItemStack stack);
        SoundEvent bucketSound(Fluid fluid, boolean fill);

        /** Sends a server-to-client message; the client hands its payload to the receiver registered for {@code channel}. */
        void sendToPlayer(ServerPlayer player, ResourceLocation channel, Consumer<FriendlyByteBuf> payload);

        /** The instance folder on clients and the server folder on dedicated servers. */
        Path gameDirectory();

        List<String> modIds();

        /** The file at {@code path} inside the jar of the mod {@code modId}, if it has one. */
        Optional<Path> modResource(String modId, String path);

        /**
         * The facade item. Forge returns a subclass that attaches the copied-block item renderer; Fabric returns
         * the shared item and registers that renderer from its client entrypoint.
         */
        Item createFacadeItem(Item.Properties properties);
    }

    /** A client-to-server message, handled on the server thread. */
    @FunctionalInterface
    public interface ServerReceiver {
        void receive(ServerPlayer player, FriendlyByteBuf payload);
    }

    @FunctionalInterface
    public interface MenuFactory<M extends AbstractContainerMenu> {
        M create(int containerId, Inventory inventory, FriendlyByteBuf data);
    }

    private static Backend backend;

    private Platform() {}

    public static void install(Backend implementation) {
        if (backend != null) throw new IllegalStateException("BuildCraft platform is already installed");
        backend = Objects.requireNonNull(implementation);
    }

    public static Backend get() {
        if (backend == null) throw new IllegalStateException("BuildCraft platform is not initialized");
        return backend;
    }

    public static <T> Supplier<T> register(ResourceKey<? extends Registry<? super T>> registry, String name, Supplier<T> factory) {
        return get().register(registry, name, factory);
    }

    public static <M extends AbstractContainerMenu> Supplier<MenuType<M>> menu(String name, MenuFactory<M> factory) {
        return register(Registry.MENU_REGISTRY, name, () -> get().menuType(factory));
    }

    public static void openMenu(ServerPlayer player, MenuProvider provider, BlockPos pos) {
        get().openMenu(player, provider, buffer -> buffer.writeBlockPos(pos));
    }
}
