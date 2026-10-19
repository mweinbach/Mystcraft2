package net.mystcraft.smoke;

import com.mojang.authlib.GameProfile;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.mystcraft.integration.registry.AgeRegistry;
import net.mystcraft.integration.registry.ExplorationRegistry;
import net.mystcraft.minecraft.exploration.BookDisplayMenu;
import net.mystcraft.minecraft.exploration.MystLecternBlockEntity;

/** Real display inventory/menu and full NBT invariants, without a graphical client. */
public final class LecternSmoke {
    private LecternSmoke() {}
    public static List<String> run(MinecraftServer server, ServerLevel level) {
        BlockPos pos = new BlockPos(3488, 200, 3488);
        var old = level.getBlockState(pos);
        BlockEntity oldEntity = level.getBlockEntity(pos);
        CompoundTag oldData = oldEntity == null ? null : oldEntity.saveWithFullMetadata();
        try {
            level.removeBlockEntity(pos);
            level.setBlock(pos, ExplorationRegistry.LECTERN.get().defaultBlockState(), 18);
            var lectern = (MystLecternBlockEntity) level.getBlockEntity(pos);
            Container nativeInventory = lectern;
            require(nativeInventory.getContainerSize() == 1 && nativeInventory.getMaxStackSize() == 1, "Lectern invented extra inventory capacity");
            require(!nativeInventory.canPlaceItem(0, new ItemStack(Items.DIAMOND)), "Lectern accepted unrelated item");
            ItemStack page = new ItemStack(AgeRegistry.PAGE.get(), 3);
            page.getOrCreateTag().putString("FixturePayload", "preserved");
            nativeInventory.setItem(0, page);
            require(nativeInventory.getItem(0).getCount() == 1 && page.getCount() == 3, "Display insertion changed source count or exceeded limit");
            lectern.setYaw(-179); lectern.setPitch(725);
            CompoundTag saved = ((BlockEntity) lectern).saveWithFullMetadata();
            var restored = new MystLecternBlockEntity(pos, level.getBlockState(pos));
            ((BlockEntity) restored).load(saved);
            require(restored.getYaw() == -90 && restored.getPitch() == 5 && ItemStack.matches(restored.getBook(), lectern.getBook()), "Lectern display payload or source stored rotations failed NBT roundtrip");
            ServerPlayer first = player(server, level, pos, "LecternOne");
            ServerPlayer second = player(server, level, pos, "LecternTwo");
            var menuOne = new BookDisplayMenu(71, first.getInventory(), lectern, pos, null);
            var menuTwo = new BookDisplayMenu(72, second.getInventory(), lectern, pos, null);
            require(((AbstractContainerMenu) menuOne).stillValid(first) && ((AbstractContainerMenu) menuTwo).stillValid(second), "Display viewers invalid at source");
            ((AbstractContainerMenu) menuOne).quickMoveStack(first, 0);
            require(lectern.getBook().isEmpty() && ((AbstractContainerMenu) menuTwo).quickMoveStack(second, 0).isEmpty(), "Two viewers duplicated display item");
            require(first.getInventory().items.stream().filter(stack -> stack.hasTag() && stack.getTag().getString("FixturePayload").equals("preserved")).mapToInt(ItemStack::getCount).sum() == 1, "Display extraction lost/duplicated exact page payload");
            ServerLevel otherWorld = server.getLevel(net.minecraft.world.level.Level.NETHER);
            if (otherWorld != null) require(!nativeInventory.stillValid(player(server, otherWorld, pos, "LecternOtherWorld")), "Display inventory accepted another dimension at identical coordinates");
            ((Entity) first).setPos(pos.getX() + 20, pos.getY(), pos.getZ());
            require(!((AbstractContainerMenu) menuOne).stillValid(first), "Display menu retained remote access");
            ItemStack map = new ItemStack(Items.FILLED_MAP);
            map.getOrCreateTag().putInt("map", 123456789);
            nativeInventory.setItem(0, map);
            MystLecternBlockEntity.tick(level, pos, level.getBlockState(pos), lectern);
            require(ItemStack.matches(lectern.getBook(), map), "Missing native map data damaged stored map NBT");
            require(ItemStack.matches(nativeInventory.removeItem(0, 1), map) && nativeInventory.removeItem(0, 1).isEmpty(), "Display removal duplicated map");
            return List.of("lectern: native one-slot filtering/count bound and exact page NBT plus source stored quarter-turn yaw/pitch persist",
                    "lectern: two real display menus cannot duplicate one page, remote access rejects, filled-map missing-data tick preserves exact stack");
        } finally {
            level.removeBlockEntity(pos); level.setBlock(pos, old, 18);
            BlockEntity be = level.getBlockEntity(pos);
            if (oldData != null && be != null) { be.load(oldData); be.setChanged(); }
        }
    }
    private static ServerPlayer player(MinecraftServer server, ServerLevel level, BlockPos pos, String name) {
        ServerPlayer player = new ServerPlayer(server, level, new GameProfile(UUID.randomUUID(), name), null);
        ((Entity) player).setPos(pos.getX() + .5, pos.getY() + 1, pos.getZ() + .5);
        return player;
    }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
