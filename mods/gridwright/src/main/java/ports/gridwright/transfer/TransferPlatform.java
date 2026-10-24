package ports.gridwright.transfer;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;

import javax.annotation.Nullable;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * The loader services the transfer nodes need: Forge item and fluid handlers or the Fabric Transfer
 * API, the loader's block-break event as a permission check for world interaction, and menus that
 * carry their own opening data. Each loader entrypoint installs a backend next to {@code Platform}'s.
 *
 * <p>Fluid amounts are millibuckets on both loaders; the Fabric backend converts to droplets.
 */
public final class TransferPlatform {

    public interface Backend {
        /** Whether the block at {@code pos} exposes an item inventory on {@code face}. */
        boolean hasItems(Level level, BlockPos pos, Direction face);

        /** Whether the block at {@code pos} exposes fluid storage on {@code face}. */
        boolean hasFluids(Level level, BlockPos pos, Direction face);

        /**
         * Takes up to {@code max} items of the first stored kind {@code filter} accepts, gathering that
         * kind from every slot. Returns what was (or would be) taken.
         */
        ItemStack extractItems(ServerLevel level, BlockPos pos, Direction face, Predicate<ItemStack> filter, int max, boolean simulate);

        /** Inserts {@code stack} and returns what did not (or would not) fit. */
        ItemStack insertItems(ServerLevel level, BlockPos pos, Direction face, ItemStack stack, boolean simulate);

        /**
         * Drains up to {@code millibuckets} of the first stored fluid {@code filter} accepts. Fluids
         * carrying NBT are left alone, since the shared code moves fluids by type only.
         */
        FluidAmount extractFluid(ServerLevel level, BlockPos pos, Direction face, Predicate<Fluid> filter, long millibuckets, boolean simulate);

        /** Fills up to {@code millibuckets} of {@code fluid}; returns how much was (or would be) accepted. */
        long insertFluid(ServerLevel level, BlockPos pos, Direction face, Fluid fluid, long millibuckets, boolean simulate);

        /** The fluid a container item such as a bucket holds, or {@code null}. */
        @Nullable
        Fluid fluidIn(ItemStack stack);

        /**
         * Asks the loader's block-break event whether {@code owner} may break the block at {@code pos},
         * as a stand-in player, so claim and protection mods can refuse world interaction.
         */
        boolean mayBreak(ServerLevel level, BlockPos pos, GameProfile owner);

        /** Opens a menu whose client factory reads what {@code data} writes. */
        void openMenu(ServerPlayer player, MenuProvider provider, Consumer<FriendlyByteBuf> data);
    }

    private static Backend backend;

    private TransferPlatform() { }

    public static void install(Backend implementation) {
        if (backend != null) throw new IllegalStateException("Gridwright transfer platform is already installed");
        backend = Objects.requireNonNull(implementation);
    }

    private static Backend backend() {
        if (backend == null) throw new IllegalStateException("Gridwright transfer platform is not installed");
        return backend;
    }

    public static boolean hasItems(Level level, BlockPos pos, Direction face) {
        return backend().hasItems(level, pos, face);
    }

    public static boolean hasFluids(Level level, BlockPos pos, Direction face) {
        return backend().hasFluids(level, pos, face);
    }

    public static ItemStack extractItems(ServerLevel level, BlockPos pos, Direction face, Predicate<ItemStack> filter, int max, boolean simulate) {
        return max <= 0 ? ItemStack.EMPTY : backend().extractItems(level, pos, face, filter, max, simulate);
    }

    public static ItemStack insertItems(ServerLevel level, BlockPos pos, Direction face, ItemStack stack, boolean simulate) {
        return stack.isEmpty() ? ItemStack.EMPTY : backend().insertItems(level, pos, face, stack, simulate);
    }

    public static FluidAmount extractFluid(ServerLevel level, BlockPos pos, Direction face, Predicate<Fluid> filter, long millibuckets, boolean simulate) {
        return millibuckets <= 0 ? FluidAmount.EMPTY : backend().extractFluid(level, pos, face, filter, millibuckets, simulate);
    }

    public static long insertFluid(ServerLevel level, BlockPos pos, Direction face, Fluid fluid, long millibuckets, boolean simulate) {
        return millibuckets <= 0 ? 0 : backend().insertFluid(level, pos, face, fluid, millibuckets, simulate);
    }

    @Nullable
    public static Fluid fluidIn(ItemStack stack) {
        return stack.isEmpty() ? null : backend().fluidIn(stack);
    }

    public static boolean mayBreak(ServerLevel level, BlockPos pos, GameProfile owner) {
        return backend().mayBreak(level, pos, owner);
    }

    public static void openMenu(ServerPlayer player, MenuProvider provider, Consumer<FriendlyByteBuf> data) {
        backend().openMenu(player, provider, data);
    }
}
