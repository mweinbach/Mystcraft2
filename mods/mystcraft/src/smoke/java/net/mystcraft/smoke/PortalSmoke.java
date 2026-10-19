package net.mystcraft.smoke;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.mystcraft.integration.MystcraftItems;
import net.mystcraft.integration.registry.PortalRegistry;
import net.mystcraft.minecraft.linking.LinkDestination;
import net.mystcraft.minecraft.portal.BookReceptacleBlock;
import net.mystcraft.minecraft.portal.BookReceptacleBlockEntity;
import net.mystcraft.minecraft.portal.CrystalBlock;
import net.mystcraft.minecraft.portal.LinkPortalBlock;
import net.mystcraft.minecraft.portal.PortalController;

/** Test-only assertions against actual level blocks, source graphs and persistent receptacle data. */
public final class PortalSmoke {
    private PortalSmoke() {}
    public static List<String> run(MinecraftServer server, ServerLevel level) {
        List<String> passed = new ArrayList<>();
        Map<BlockPos, BlockState> states = new LinkedHashMap<>();
        Map<BlockPos, CompoundTag> entities = new LinkedHashMap<>();
        require(level.getMaxBuildHeight() > 307, "Portal smoke requires the Overworld height range");
        BlockPos base = new BlockPos(100050, 280, 100050);
        BlockPos receptaclePos = base.north();
        BlockPos largeBase = new BlockPos(100080, 240, 100080);
        try {
            prepare(level, base, 5, states, entities);
            remember(level, receptaclePos, states, entities);
            level.setBlock(receptaclePos, PortalRegistry.BOOK_RECEPTACLE.get().defaultBlockState()
                    .setValue(BookReceptacleBlock.FACING, Direction.NORTH), 2);
            require(level.getBlockEntity(receptaclePos) instanceof BookReceptacleBlockEntity, "Receptacle BE not registered");
            BookReceptacleBlockEntity receptacle = (BookReceptacleBlockEntity) level.getBlockEntity(receptaclePos);
            ItemStack book = new ItemStack(MystcraftItems.LINKING_BOOK.get());
            new LinkDestination(new ResourceLocation("minecraft", "the_nether"), 80.5, 200, 80.5, 0, 0).write(book);
            book.getOrCreateTag().putString("SmokePortalPayload", "retained");
            receptacle.setBook(book);
            require(portalCount(level, base, 5) == 9, "5x5 crystal frame did not fill all nine interior cells");
            for (int x = 1; x < 4; x++) for (int y = 1; y < 4; y++) {
                BlockPos pos = base.offset(x, y, 0);
                require(PortalController.tension(level, pos), "Created portal lacks classic two-axis tension");
                require(PortalController.source(level, pos) == receptacle, "Source path did not resolve to original receptacle");
                require(level.getBlockState(pos).getValue(LinkPortalBlock.AXIS) == Direction.Axis.Z, "Vertical XY field has wrong plane axis");
            }
            require(ItemStack.matches(receptacle.getBook(), book), "Portal activation changed or consumed its source book");
            passed.add("crystal portal: real 5x5 frame fills nine cells, all resolve to retained bound Nether book through source graph");

            CompoundTag saved = ((net.minecraft.world.level.block.entity.BlockEntity) receptacle).saveWithFullMetadata();
            BlockEntity restored = new BookReceptacleBlockEntity(receptaclePos, ((net.minecraft.world.level.block.entity.BlockEntity) receptacle).getBlockState());
            ((net.minecraft.world.level.block.entity.BlockEntity) restored).load(saved);
            require(ItemStack.matches(((BookReceptacleBlockEntity) restored).getBook(), book), "Receptacle save/load changed book count/destination/unknown NBT");
            passed.add("crystal portal: receptacle block-entity NBT roundtrip preserves exact book destination and custom payload");

            BlockPos first = base.offset(1, 1, 0), second = base.offset(2, 1, 0);
            level.setBlock(first, level.getBlockState(first).setValue(CrystalBlock.SOURCE, Direction.EAST), 2);
            level.setBlock(second, level.getBlockState(second).setValue(CrystalBlock.SOURCE, Direction.WEST), 2);
            require(PortalController.source(level, first) == null, "Cyclic source graph was accepted");
            PortalController.validate(level, first);
            require(!level.getBlockState(first).is(PortalRegistry.LINK_PORTAL.get())
                    && !level.getBlockState(second).is(PortalRegistry.LINK_PORTAL.get()), "Cyclic portal blocks survived validation");
            passed.add("crystal portal: source-pointer cycle is rejected and invalid portal cells are removed");

            receptacle.takeBook();
            require(portalCount(level, base, 5) == 0, "Removing source book left portal blocks");
            receptacle.setBook(book);
            require(portalCount(level, base, 5) == 9, "Reinserting source book did not rebuild intact frame");
            level.setBlock(base.offset(0, 2, 0), Blocks.AIR.defaultBlockState(), 2);
            PortalController.validate(level, base.offset(1, 2, 0));
            require(portalCount(level, base, 5) == 0, "Broken crystal boundary left an unsupported portal field");
            require(ItemStack.matches(receptacle.getBook(), book), "Frame invalidation lost source book");
            passed.add("crystal portal: removing book shuts field down, reinsertion rebuilds, broken frame invalidates field without consuming book");

            // 67x67 boundary would grow 65x65=4225 cells, exceeding the explicit 4096-node safety budget.
            prepare(level, largeBase, 67, states, entities);
            BlockPos largeReceptacle = largeBase.north();
            remember(level, largeReceptacle, states, entities);
            level.setBlock(largeReceptacle, PortalRegistry.BOOK_RECEPTACLE.get().defaultBlockState()
                    .setValue(BookReceptacleBlock.FACING, Direction.NORTH), 2);
            BookReceptacleBlockEntity large = (BookReceptacleBlockEntity) level.getBlockEntity(largeReceptacle);
            large.setBook(book);
            require(portalCount(level, largeBase, 67) == 0, "Budget-aborted activation left partial portal residue");
            require(ItemStack.matches(large.getBook(), book), "Budget-aborted activation consumed book");
            passed.add("crystal portal: oversized growth exceeds explicit work budget and rolls back every new portal cell while preserving book");
        } finally {
            // Empty our temporary receptacles before removal: no duplicate dropped book entities.
            for (BlockPos pos : states.keySet()) {
                if (level.getBlockEntity(pos) instanceof BookReceptacleBlockEntity receptacle) receptacle.takeBook();
            }
            for (Map.Entry<BlockPos, BlockState> entry : states.entrySet()) level.setBlock(entry.getKey(), entry.getValue(), 2);
            for (Map.Entry<BlockPos, CompoundTag> entry : entities.entrySet()) {
                BlockEntity restored = level.getBlockEntity(entry.getKey());
                if (restored != null) restored.load(entry.getValue());
            }
        }
        return passed;
    }
    private static void prepare(ServerLevel level, BlockPos base, int size,
                                Map<BlockPos, BlockState> states, Map<BlockPos, CompoundTag> entities) {
        for (int x = 0; x < size; x++) for (int y = 0; y < size; y++) {
            BlockPos pos = base.offset(x, y, 0);
            remember(level, pos, states, entities);
            boolean edge = x == 0 || y == 0 || x == size - 1 || y == size - 1;
            level.setBlock(pos, edge ? PortalRegistry.CRYSTAL.get().defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
        }
    }
    private static void remember(ServerLevel level, BlockPos pos,
                                 Map<BlockPos, BlockState> states, Map<BlockPos, CompoundTag> entities) {
        level.getChunkAt(pos);
        states.putIfAbsent(pos.immutable(), level.getBlockState(pos));
        BlockEntity entity = level.getBlockEntity(pos);
        if (entity != null) entities.putIfAbsent(pos.immutable(), entity.saveWithFullMetadata());
    }
    private static int portalCount(ServerLevel level, BlockPos base, int size) {
        int count = 0;
        for (int x = 1; x < size - 1; x++) for (int y = 1; y < size - 1; y++) {
            if (level.getBlockState(base.offset(x, y, 0)).is(PortalRegistry.LINK_PORTAL.get())) count++;
        }
        return count;
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
