package ports.gridwright.platform;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The loader services the shared core needs. Each loader entrypoint installs a backend before any
 * registry class initialises, so every block, item, menu and recipe is defined once in shared code.
 */
public final class Platform {

    public interface Backend {
        <T> Supplier<T> register(ResourceKey<? extends Registry<? super T>> registry, String name, Supplier<T> factory);

        <M extends AbstractContainerMenu> MenuType<M> menuType(MenuFactory<M> factory);

        /** Opens a machine menu; the client's menu factory reads {@code pos} from the opening data. */
        void openMenu(ServerPlayer player, MenuProvider provider, BlockPos pos);

        /** A creative tab whose translation key is {@code itemGroup.<namespace>.<path>}. */
        CreativeModeTab creativeTab(ResourceLocation id, Supplier<ItemStack> icon);

        /** Furnace burn time of {@code stack} in ticks, including fuels other mods register; 0 if none. */
        int burnTime(ItemStack stack);

        /**
         * Offers {@code amount} energy units (Forge Energy or Team Reborn Energy) to the block at
         * {@code target}, entering through its {@code face}. Returns how much it accepted.
         */
        long pushEnergy(ServerLevel level, BlockPos target, Direction face, long amount);

        /** Opens a menu whose client factory reads whatever {@code data} writes. */
        void openMenu(ServerPlayer player, MenuProvider provider, Consumer<FriendlyByteBuf> data);

        /**
         * A server player with no client that blocks can attribute damage to (Forge's
         * {@code FakePlayer}; on Fabric, Gridwright's own equivalent). Never added to the level.
         */
        ServerPlayer fakePlayer(ServerLevel level);

        /**
         * Whether protection mods let {@code player} use the block {@code hit} points at with the item in
         * {@code hand}, asked before a wand places a block against it. Forge already fires
         * {@code BlockEvent.EntityPlaceEvent} for the placement itself and answers {@code true}; Fabric has no
         * placement event, so it asks {@code UseBlockCallback}, which claim mods cancel.
         */
        boolean mayUseBlock(ServerPlayer player, InteractionHand hand, BlockHitResult hit);

        /** Fluid units in one bucket: 1,000 millibuckets on Forge, 81,000 droplets on Fabric. */
        long bucketVolume();

        /**
         * Fills or empties the fluid container in {@code hand} against the fluid storage of the block at
         * {@code pos}, as a bucket would. Returns whether anything moved.
         */
        boolean interactWithFluidStorage(Player player, InteractionHand hand, Level level, BlockPos pos, Direction side);
    }

    @FunctionalInterface
    public interface MenuFactory<M extends AbstractContainerMenu> {
        M create(int containerId, Inventory inventory, FriendlyByteBuf data);
    }

    private static Backend backend;

    private Platform() { }

    public static void install(Backend implementation) {
        if (backend != null) throw new IllegalStateException("Gridwright platform is already installed");
        backend = Objects.requireNonNull(implementation);
    }

    private static Backend backend() {
        if (backend == null) throw new IllegalStateException("Gridwright platform is not installed");
        return backend;
    }

    public static <T> Supplier<T> register(ResourceKey<? extends Registry<? super T>> registry, String name, Supplier<T> factory) {
        return backend().register(registry, name, factory);
    }

    public static <M extends AbstractContainerMenu> Supplier<MenuType<M>> menu(String name, MenuFactory<M> factory) {
        return register(Registry.MENU_REGISTRY, name, () -> backend().menuType(factory));
    }

    public static void openMenu(ServerPlayer player, MenuProvider provider, BlockPos pos) {
        backend().openMenu(player, provider, pos);
    }

    public static CreativeModeTab creativeTab(ResourceLocation id, Supplier<ItemStack> icon) {
        return backend().creativeTab(id, icon);
    }

    public static int burnTime(ItemStack stack) {
        return stack.isEmpty() ? 0 : backend().burnTime(stack);
    }

    public static long pushEnergy(ServerLevel level, BlockPos target, Direction face, long amount) {
        return amount <= 0 ? 0 : backend().pushEnergy(level, target, face, amount);
    }

    public static void openMenu(ServerPlayer player, MenuProvider provider, Consumer<FriendlyByteBuf> data) {
        backend().openMenu(player, provider, data);
    }

    public static ServerPlayer fakePlayer(ServerLevel level) {
        return backend().fakePlayer(level);
    }

    public static boolean mayUseBlock(ServerPlayer player, InteractionHand hand, BlockHitResult hit) {
        return backend().mayUseBlock(player, hand, hit);
    }

    public static long bucketVolume() {
        return backend().bucketVolume();
    }

    public static boolean interactWithFluidStorage(Player player, InteractionHand hand, Level level, BlockPos pos, Direction side) {
        return backend().interactWithFluidStorage(player, hand, level, pos, side);
    }
}
