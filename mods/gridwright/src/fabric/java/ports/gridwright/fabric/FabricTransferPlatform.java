package ports.gridwright.fabric;

import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import ports.gridwright.transfer.FluidAmount;
import ports.gridwright.transfer.PipeNetwork;
import ports.gridwright.transfer.TransferPlatform;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Transfer services on Fabric: Transfer API item and fluid storages (millibuckets converted to
 * droplets, only whole millibuckets are moved), a cached stand-in {@link ServerPlayer} per level and
 * owner for {@link PlayerBlockBreakEvents#BEFORE}, and extended screen handler factories.
 */
@SuppressWarnings("UnstableApiUsage")
final class FabricTransferPlatform implements TransferPlatform.Backend {

    private static final long DROPLETS_PER_MB = FluidConstants.BUCKET / 1000;

    /** Stand-in players by level and owner, for the current server only. */
    private final Map<ServerLevel, Map<UUID, ServerPlayer>> standIns = new HashMap<>();
    @Nullable private MinecraftServer standInServer;

    @Nullable
    private static Storage<ItemVariant> items(Level level, BlockPos pos, Direction face) {
        return level.hasChunkAt(pos) ? ItemStorage.SIDED.find(level, pos, face) : null;
    }

    @Nullable
    private static Storage<FluidVariant> fluids(Level level, BlockPos pos, Direction face) {
        return level.hasChunkAt(pos) ? FluidStorage.SIDED.find(level, pos, face) : null;
    }

    @Override
    public boolean hasItems(Level level, BlockPos pos, Direction face) {
        return items(level, pos, face) != null;
    }

    @Override
    public boolean hasFluids(Level level, BlockPos pos, Direction face) {
        return fluids(level, pos, face) != null;
    }

    @Override
    public ItemStack extractItems(ServerLevel level, BlockPos pos, Direction face, Predicate<ItemStack> filter, int max, boolean simulate) {
        Storage<ItemVariant> storage = items(level, pos, face);
        if (storage == null || !storage.supportsExtraction()) return ItemStack.EMPTY;
        // Collect first: the filter may open its own transactions, and extracting while iterating is unsafe.
        Set<ItemVariant> stored = new LinkedHashSet<>();
        for (StorageView<ItemVariant> view : storage) {
            if (!view.isResourceBlank() && view.getAmount() > 0) stored.add(view.getResource());
        }
        for (ItemVariant variant : stored) {
            if (!filter.test(variant.toStack())) continue;
            int limit = Math.min(max, variant.getItem().getMaxStackSize());
            try (Transaction transaction = Transaction.openOuter()) {
                // A view may hold items its storage will not give up on this face; try the next kind.
                long taken = storage.extract(variant, limit, transaction);
                if (taken <= 0) continue;
                if (!simulate) transaction.commit();
                return variant.toStack((int) taken);
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    public ItemStack insertItems(ServerLevel level, BlockPos pos, Direction face, ItemStack stack, boolean simulate) {
        Storage<ItemVariant> storage = items(level, pos, face);
        if (storage == null || !storage.supportsInsertion()) return stack;
        try (Transaction transaction = Transaction.openOuter()) {
            long inserted = storage.insert(ItemVariant.of(stack), stack.getCount(), transaction);
            if (!simulate) transaction.commit();
            ItemStack rest = stack.copy();
            rest.shrink((int) inserted);
            return rest;
        }
    }

    @Override
    public boolean fitsAll(ServerLevel level, List<PipeNetwork.Target> targets, List<ItemStack> stacks) {
        // One transaction for the whole batch, never committed: each insert sees the ones before it.
        try (Transaction transaction = Transaction.openOuter()) {
            for (ItemStack stack : stacks) {
                long left = stack.getCount();
                ItemVariant variant = ItemVariant.of(stack);
                for (PipeNetwork.Target target : targets) {
                    if (left <= 0) break;
                    Storage<ItemVariant> storage = items(level, target.pos(), target.face());
                    if (storage != null && storage.supportsInsertion()) left -= storage.insert(variant, left, transaction);
                }
                if (left > 0) return false;
            }
            return true;
        }
    }

    @Override
    public FluidAmount extractFluid(ServerLevel level, BlockPos pos, Direction face, Predicate<Fluid> filter, long millibuckets, boolean simulate) {
        Storage<FluidVariant> storage = fluids(level, pos, face);
        if (storage == null || !storage.supportsExtraction()) return FluidAmount.EMPTY;
        Set<FluidVariant> stored = new LinkedHashSet<>();
        for (StorageView<FluidVariant> view : storage) {
            if (!view.isResourceBlank() && view.getAmount() >= DROPLETS_PER_MB && !view.getResource().hasNbt()) stored.add(view.getResource());
        }
        for (FluidVariant variant : stored) {
            if (!filter.test(variant.getFluid())) continue;
            try (Transaction transaction = Transaction.openOuter()) {
                // Probe how much can leave, then take only whole millibuckets of it.
                long available;
                try (Transaction probe = transaction.openNested()) {
                    available = storage.extract(variant, millibuckets * DROPLETS_PER_MB, probe);
                }
                long mb = available / DROPLETS_PER_MB;
                if (mb <= 0) continue;
                long taken = storage.extract(variant, mb * DROPLETS_PER_MB, transaction);
                if (taken != mb * DROPLETS_PER_MB) continue;
                if (!simulate) transaction.commit();
                return new FluidAmount(variant.getFluid(), mb);
            }
        }
        return FluidAmount.EMPTY;
    }

    @Override
    public long insertFluid(ServerLevel level, BlockPos pos, Direction face, Fluid fluid, long millibuckets, boolean simulate) {
        Storage<FluidVariant> storage = fluids(level, pos, face);
        if (storage == null || !storage.supportsInsertion()) return 0;
        FluidVariant variant = FluidVariant.of(fluid);
        try (Transaction transaction = Transaction.openOuter()) {
            long accepted;
            try (Transaction probe = transaction.openNested()) {
                accepted = storage.insert(variant, millibuckets * DROPLETS_PER_MB, probe);
            }
            long mb = accepted / DROPLETS_PER_MB;
            if (mb <= 0) return 0;
            long inserted = storage.insert(variant, mb * DROPLETS_PER_MB, transaction);
            if (inserted != mb * DROPLETS_PER_MB) return 0;
            if (!simulate) transaction.commit();
            return mb;
        }
    }

    @Nullable
    @Override
    public Fluid fluidIn(ItemStack stack) {
        Storage<FluidVariant> storage = ContainerItemContext.withConstant(stack).find(FluidStorage.ITEM);
        if (storage == null) return null;
        for (StorageView<FluidVariant> view : storage) {
            if (!view.isResourceBlank() && view.getAmount() > 0) return view.getResource().getFluid();
        }
        return null;
    }

    @Override
    public boolean mayBreak(ServerLevel level, BlockPos pos, GameProfile owner) {
        MinecraftServer server = level.getServer();
        ServerPlayer player = standIn(level, owner);
        // Building a player for the owner's UUID re-points an online owner's advancements at it.
        ServerPlayer online = server.getPlayerList().getPlayer(owner.getId());
        if (online != null) server.getPlayerList().getPlayerAdvancements(online);
        player.moveTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 0, 0);
        if (!level.mayInteract(player, pos)) return false;
        BlockState state = level.getBlockState(pos);
        try {
            return PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(level, player, pos, state, level.getBlockEntity(pos));
        } catch (RuntimeException e) {
            // A listener that cannot cope with a player without a connection refuses, conservatively.
            return false;
        }
    }

    private synchronized ServerPlayer standIn(ServerLevel level, GameProfile owner) {
        if (standInServer != level.getServer()) {
            standInServer = level.getServer();
            standIns.clear();
        }
        return standIns.computeIfAbsent(level, key -> new HashMap<>())
                .computeIfAbsent(owner.getId(), id -> new StandInPlayer(level, owner));
    }

    /** A player that never joins: it only answers permission checks and ignores messages sent to it. */
    private static final class StandInPlayer extends ServerPlayer {
        StandInPlayer(ServerLevel level, GameProfile profile) {
            super(level.getServer(), level, profile, null);
        }

        @Override
        public void sendSystemMessage(Component message) { }

        @Override
        public void sendSystemMessage(Component message, boolean overlay) { }

        @Override
        public void displayClientMessage(Component message, boolean overlay) { }
    }

    @Override
    public void openMenu(ServerPlayer player, MenuProvider provider, Consumer<FriendlyByteBuf> data) {
        player.openMenu(new ExtendedScreenHandlerFactory() {
            @Override
            public void writeScreenOpeningData(ServerPlayer opener, FriendlyByteBuf buf) {
                data.accept(buf);
            }

            @Override
            public Component getDisplayName() {
                return provider.getDisplayName();
            }

            @Nullable
            @Override
            public AbstractContainerMenu createMenu(int id, Inventory inventory, Player opener) {
                return provider.createMenu(id, inventory, opener);
            }
        });
    }
}
