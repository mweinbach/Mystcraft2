package ports.gridwright.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluid;

/**
 * The loader APIs the shared GameTests observe: Forge Energy or Team Reborn Energy, Forge item handlers
 * or the Fabric Transfer API, and a test-only energy sink each loader attaches to vanilla barrels.
 */
public interface TestPlatform {

    /** The block's energy as other mods see it through this loader's energy API. */
    EnergyAccess energy(BlockEntity blockEntity, Direction side);

    /** The block's inventory as hoppers and pipes see it through this loader's item API. */
    ItemAccess items(BlockEntity blockEntity, Direction side);

    /** How much energy the test sink on {@code barrel} has accepted through this loader's energy API. */
    long sunk(BlockEntity barrel);

    /** The block's fluid storage as other mods see it: a Forge fluid handler or a Fabric fluid storage. */
    FluidAccess fluids(BlockEntity blockEntity, Direction side);

    /**
     * Offers {@code stack} to whatever item storage the block exposes on {@code side} (not only
     * container-backed ones) and returns how many items it accepted.
     */
    long offerItems(BlockEntity blockEntity, Direction side, ItemStack stack);

    /**
     * Makes {@code pos} (an absolute position) behave like a claimed block another player owns: this
     * loader's break and place hooks refuse every player there, as protection mods do. {@code false}
     * lifts the claim.
     */
    void protect(BlockPos pos, boolean claimed);

    interface FluidAccess {
        /** Returns how much of {@code fluid} was (or would be) accepted, in loader units. */
        long fill(Fluid fluid, long amount, boolean simulate);
        /** Returns how much fluid was (or would be) removed, in loader units. */
        long drain(long amount, boolean simulate);
        long amount();
        Fluid fluid();
    }

    interface EnergyAccess {
        long stored();
        long capacity();
        /** Returns the energy taken (or that would be taken). */
        long extract(long amount, boolean simulate);
        /** Returns the energy accepted (or that would be accepted). */
        long insert(long amount, boolean simulate);
    }

    interface ItemAccess {
        /** Inserts into any slot the face allows and returns what did not fit. */
        ItemStack insert(ItemStack stack);
        /** Takes from the face's {@code slot}-th slot. */
        ItemStack extract(int slot, int amount, boolean simulate);
    }

    final class Holder {
        private static TestPlatform platform;

        private Holder() { }

        public static void install(TestPlatform implementation) {
            platform = implementation;
        }

        public static TestPlatform get() {
            if (platform == null) throw new IllegalStateException("No GameTest platform installed");
            return platform;
        }
    }
}
