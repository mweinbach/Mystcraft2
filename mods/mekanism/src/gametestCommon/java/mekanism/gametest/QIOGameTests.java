/*
 * Mekanism 1.19.2 port - in-world tests shared by the Forge and Fabric builds.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package mekanism.gametest;

import static mekanism.gametest.CoreMachineGameTests.TEMPLATE;
import static mekanism.gametest.CoreMachineGameTests.check;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import mekanism.api.inventory.IInventorySlot;
import mekanism.api.text.EnumColor;
import mekanism.common.content.qio.IQIODriveItem.DriveMetadata;
import mekanism.common.content.qio.QIOFrequency;
import mekanism.common.content.qio.filter.QIOItemStackFilter;
import mekanism.common.item.ItemPortableQIODashboard;
import mekanism.common.lib.frequency.Frequency.FrequencyIdentity;
import mekanism.common.lib.frequency.FrequencyType;
import mekanism.common.registries.MekanismBlocks;
import mekanism.common.registries.MekanismItems;
import mekanism.common.tile.qio.TileEntityQIOComponent;
import mekanism.common.tile.qio.TileEntityQIODashboard;
import mekanism.common.tile.qio.TileEntityQIODriveArray;
import mekanism.common.tile.qio.TileEntityQIOExporter;
import mekanism.common.tile.qio.TileEntityQIOImporter;
import mekanism.common.tile.qio.TileEntityQIORedstoneAdapter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * A QIO frequency is public or private. The drive array holds the drives that store the items. An importer pulls
 * from the chest behind it, and an exporter pushes into the chest behind it. Both chests are the loader's item
 * inventory: Forge's item handler, and the Transfer API on Fabric.
 */
public class QIOGameTests {

    private static final AtomicInteger IDS = new AtomicInteger();
    private static final int IRON = 32;
    private static final int GOLD = 16;
    private static final int FILTERED_IRON = 10;
    private static final int FILTERED_GOLD = 7;
    private static final int PRIVATE_DIAMONDS = 5;
    private static final long BASE_CAPACITY = 16_000L;
    /** The importer and exporter each move one item type, then wait ten ticks. */
    private static final int MOVE_TICKS = 400;

    /**
     * Iron and gold leave a chest through an importer, sit on the drive in the drive array, and return through an
     * exporter once that exporter is allowed to run without a filter. A second drive array, a dashboard and a portable
     * dashboard on the same public frequency see the stored count. The redstone adapter outputs a full signal while
     * the iron is stored.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = MOVE_TICKS)
    public static void qioStoresAndRetrievesItems(GameTestHelper helper) {
        BlockEntity source = chest(helper, new BlockPos(1, 1, 1));
        BlockPos importerPos = new BlockPos(2, 1, 1);
        helper.setBlock(importerPos, facing(MekanismBlocks.QIO_IMPORTER.getBlock(), Direction.EAST));
        BlockPos arrayPos = new BlockPos(4, 1, 1);
        helper.setBlock(arrayPos, MekanismBlocks.QIO_DRIVE_ARRAY.getBlock());
        BlockPos otherArrayPos = new BlockPos(6, 1, 1);
        helper.setBlock(otherArrayPos, MekanismBlocks.QIO_DRIVE_ARRAY.getBlock());
        BlockPos dashboardPos = new BlockPos(4, 1, 3);
        helper.setBlock(dashboardPos, facing(MekanismBlocks.QIO_DASHBOARD.getBlock(), Direction.NORTH));
        BlockPos adapterPos = new BlockPos(6, 1, 3);
        helper.setBlock(adapterPos, facing(MekanismBlocks.QIO_REDSTONE_ADAPTER.getBlock(), Direction.NORTH));
        BlockEntity destination = chest(helper, new BlockPos(1, 1, 3));
        TileEntityQIOImporter importer = component(helper, importerPos, TileEntityQIOImporter.class);
        TileEntityQIODriveArray array = component(helper, arrayPos, TileEntityQIODriveArray.class);
        TileEntityQIODriveArray otherArray = component(helper, otherArrayPos, TileEntityQIODriveArray.class);
        TileEntityQIODashboard dashboard = component(helper, dashboardPos, TileEntityQIODashboard.class);
        TileEntityQIORedstoneAdapter adapter = component(helper, adapterPos, TileEntityQIORedstoneAdapter.class);
        check(importer.getDirection() == Direction.EAST, "Importer faces " + importer.getDirection());
        check(importer.getImportWithoutFilter(), "Import without a filter starts off");
        String name = frequency("pub");
        UUID owner = UUID.randomUUID();
        link(importer, name, true, owner);
        link(array, name, true, owner);
        link(otherArray, name, true, owner);
        link(dashboard, name, true, owner);
        link(adapter, name, true, owner);
        array.getDriveSlots().get(0).setStack(new ItemStack(MekanismItems.BASE_QIO_DRIVE.get()));
        adapter.handleStackChange(new ItemStack(Items.IRON_INGOT));
        adapter.handleCountChange(IRON);
        ItemStack portable = new ItemStack(MekanismItems.PORTABLE_QIO_DASHBOARD.get());
        ItemPortableQIODashboard portableItem = (ItemPortableQIODashboard) portable.getItem();
        portableItem.setFrequency(portable, array.getQIOFrequency());
        give(source, Items.GOLD_INGOT, GOLD);
        give(source, Items.IRON_INGOT, IRON);
        check(count(source, Direction.EAST, Items.IRON_INGOT) == IRON, "The chest's east side is what the importer reads");
        int[] phase = {0};
        helper.succeedWhen(() -> {
            QIOFrequency frequency = array.getQIOFrequency();
            check(frequency != null, "The drive array has no frequency");
            if (phase[0] == 0) {
                check(count(source, Direction.EAST, Items.IRON_INGOT) == 0 && count(source, Direction.EAST, Items.GOLD_INGOT) == 0,
                      "Source still holds " + count(source, Direction.EAST, Items.IRON_INGOT) + " iron and "
                            + count(source, Direction.EAST, Items.GOLD_INGOT) + " gold");
                check(frequency.getStored(new ItemStack(Items.IRON_INGOT)) == IRON, "Frequency holds "
                      + frequency.getStored(new ItemStack(Items.IRON_INGOT)) + " iron");
                check(frequency.getStored(new ItemStack(Items.GOLD_INGOT)) == GOLD, "Frequency holds "
                      + frequency.getStored(new ItemStack(Items.GOLD_INGOT)) + " gold");
                check(frequency.getTotalItemCount() == IRON + GOLD, "Frequency total is " + frequency.getTotalItemCount());
                check(frequency.getTotalItemCountCapacity() == BASE_CAPACITY, "Drive capacity is " + frequency.getTotalItemCountCapacity());
                IInventorySlot slot = array.getDriveSlots().get(0);
                check(DriveMetadata.load(slot.getStack()).count() == IRON + GOLD,
                      "Drive metadata count is " + DriveMetadata.load(slot.getStack()).count());
                check(otherArray.getQIOFrequency() == frequency && dashboard.getQIOFrequency() == frequency,
                      "The other array and the dashboard are on a different frequency");
                check(stored(portableItem, portable) == IRON + GOLD, "Portable dashboard sees " + stored(portableItem, portable));
                check(portableItem.getColor(portable) == EnumColor.INDIGO, "Portable dashboard color is " + portableItem.getColor(portable));
                check(signal(helper, adapterPos) == 15, "Redstone adapter outputs " + signal(helper, adapterPos) + " while the iron is stored");
                check(importer.getActive() && array.getActive() && dashboard.getActive(), "A frequency member is still inactive");
                ItemStack drive = slot.getStack();
                slot.setStack(ItemStack.EMPTY);
                check(frequency.getTotalItemCount() == 0, "Removing the drive left " + frequency.getTotalItemCount() + " items on the frequency");
                check(DriveMetadata.load(drive).count() == IRON + GOLD, "The removed drive kept " + DriveMetadata.load(drive).count() + " items");
                slot.setStack(drive);
                check(frequency.getTotalItemCount() == IRON + GOLD, "Reinserting the drive restored " + frequency.getTotalItemCount());
                BlockPos exporterPos = new BlockPos(2, 1, 3);
                helper.setBlock(exporterPos, facing(MekanismBlocks.QIO_EXPORTER.getBlock(), Direction.EAST));
                TileEntityQIOExporter exporter = component(helper, exporterPos, TileEntityQIOExporter.class);
                check(!exporter.getExportWithoutFilter(), "Export without a filter starts on");
                link(exporter, name, true, owner);
                exporter.toggleExportWithoutFilter();
                phase[0] = 1;
                throw new GameTestAssertException("exporting");
            }
            check(count(destination, Direction.EAST, Items.IRON_INGOT) == IRON, "Destination holds "
                  + count(destination, Direction.EAST, Items.IRON_INGOT) + " iron");
            check(count(destination, Direction.EAST, Items.GOLD_INGOT) == GOLD, "Destination holds "
                  + count(destination, Direction.EAST, Items.GOLD_INGOT) + " gold");
            check(frequency.getTotalItemCount() == 0, "Frequency still holds " + frequency.getTotalItemCount());
            check(count(source, Direction.EAST, Items.IRON_INGOT) == 0 && count(source, Direction.EAST, Items.GOLD_INGOT) == 0,
                  "Source received items back");
            check(DriveMetadata.load(array.getDriveSlots().get(0).getStack()).count() == 0, "The drive still records stored items");
            check(signal(helper, adapterPos) == 0, "Redstone adapter still outputs " + signal(helper, adapterPos));
            check(stored(portableItem, portable) == 0, "Portable dashboard still sees " + stored(portableItem, portable));
        });
    }

    /**
     * An item filter on the importer takes the iron and leaves the gold in the chest. The same filter on the exporter
     * returns that iron.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = MOVE_TICKS)
    public static void qioItemFilterSelectsWhatMoves(GameTestHelper helper) {
        BlockEntity source = chest(helper, new BlockPos(1, 1, 1));
        BlockPos importerPos = new BlockPos(2, 1, 1);
        helper.setBlock(importerPos, facing(MekanismBlocks.QIO_IMPORTER.getBlock(), Direction.EAST));
        BlockPos arrayPos = new BlockPos(4, 1, 1);
        helper.setBlock(arrayPos, MekanismBlocks.QIO_DRIVE_ARRAY.getBlock());
        BlockEntity destination = chest(helper, new BlockPos(1, 1, 3));
        TileEntityQIOImporter importer = component(helper, importerPos, TileEntityQIOImporter.class);
        TileEntityQIODriveArray array = component(helper, arrayPos, TileEntityQIODriveArray.class);
        String name = frequency("flt");
        UUID owner = UUID.randomUUID();
        link(importer, name, true, owner);
        link(array, name, true, owner);
        array.getDriveSlots().get(0).setStack(new ItemStack(MekanismItems.BASE_QIO_DRIVE.get()));
        QIOItemStackFilter iron = new QIOItemStackFilter(new ItemStack(Items.IRON_INGOT));
        check(importer.getFilterManager().addFilter(iron), "The importer refused the iron filter");
        give(source, Items.GOLD_INGOT, FILTERED_GOLD);
        give(source, Items.IRON_INGOT, FILTERED_IRON);
        int[] phase = {0};
        helper.succeedWhen(() -> {
            QIOFrequency frequency = array.getQIOFrequency();
            check(frequency != null, "The filtered drive array has no frequency");
            check(count(source, Direction.EAST, Items.GOLD_INGOT) == FILTERED_GOLD,
                  "Gold left the source: " + count(source, Direction.EAST, Items.GOLD_INGOT));
            if (phase[0] == 0) {
                check(count(source, Direction.EAST, Items.IRON_INGOT) == 0, "Source still holds iron");
                check(frequency.getStored(new ItemStack(Items.IRON_INGOT)) == FILTERED_IRON, "Frequency holds "
                      + frequency.getStored(new ItemStack(Items.IRON_INGOT)) + " iron");
                check(frequency.getStored(new ItemStack(Items.GOLD_INGOT)) == 0, "The iron filter stored gold");
                BlockPos exporterPos = new BlockPos(2, 1, 3);
                helper.setBlock(exporterPos, facing(MekanismBlocks.QIO_EXPORTER.getBlock(), Direction.EAST));
                TileEntityQIOExporter exporter = component(helper, exporterPos, TileEntityQIOExporter.class);
                link(exporter, name, true, owner);
                check(exporter.getFilterManager().addFilter(new QIOItemStackFilter(new ItemStack(Items.IRON_INGOT))),
                      "The exporter refused the iron filter");
                phase[0] = 1;
                throw new GameTestAssertException("exporting iron");
            }
            check(count(destination, Direction.EAST, Items.IRON_INGOT) == FILTERED_IRON, "Destination holds "
                  + count(destination, Direction.EAST, Items.IRON_INGOT) + " iron");
            check(count(destination, Direction.EAST, Items.GOLD_INGOT) == 0, "Gold reached the destination");
            check(frequency.getTotalItemCount() == 0, "Frequency still holds " + frequency.getTotalItemCount());
        });
    }

    /**
     * The same private name for two owners is two frequencies. Items the first owner's importer stores stay on that
     * owner's drive. The second owner's drive array, and a portable dashboard set to that second frequency, stay empty.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = MOVE_TICKS)
    public static void privateQioFrequencyStaysWithItsOwner(GameTestHelper helper) {
        BlockEntity source = chest(helper, new BlockPos(1, 1, 1));
        BlockPos importerPos = new BlockPos(2, 1, 1);
        helper.setBlock(importerPos, facing(MekanismBlocks.QIO_IMPORTER.getBlock(), Direction.EAST));
        BlockPos ownerPos = new BlockPos(4, 1, 1);
        BlockPos otherPos = new BlockPos(6, 1, 1);
        helper.setBlock(ownerPos, MekanismBlocks.QIO_DRIVE_ARRAY.getBlock());
        helper.setBlock(otherPos, MekanismBlocks.QIO_DRIVE_ARRAY.getBlock());
        TileEntityQIOImporter importer = component(helper, importerPos, TileEntityQIOImporter.class);
        TileEntityQIODriveArray ownerArray = component(helper, ownerPos, TileEntityQIODriveArray.class);
        TileEntityQIODriveArray otherArray = component(helper, otherPos, TileEntityQIODriveArray.class);
        String name = frequency("prv");
        UUID owner = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        link(importer, name, false, owner);
        link(ownerArray, name, false, owner);
        link(otherArray, name, false, other);
        ownerArray.getDriveSlots().get(0).setStack(new ItemStack(MekanismItems.BASE_QIO_DRIVE.get()));
        otherArray.getDriveSlots().get(0).setStack(new ItemStack(MekanismItems.BASE_QIO_DRIVE.get()));
        ItemStack ownerPortable = new ItemStack(MekanismItems.PORTABLE_QIO_DASHBOARD.get());
        ItemStack otherPortable = new ItemStack(MekanismItems.PORTABLE_QIO_DASHBOARD.get());
        ItemPortableQIODashboard portableItem = (ItemPortableQIODashboard) ownerPortable.getItem();
        portableItem.setFrequency(ownerPortable, ownerArray.getQIOFrequency());
        portableItem.setFrequency(otherPortable, otherArray.getQIOFrequency());
        give(source, Items.DIAMOND, PRIVATE_DIAMONDS);
        helper.succeedWhen(() -> {
            QIOFrequency ownerFrequency = ownerArray.getQIOFrequency();
            QIOFrequency otherFrequency = otherArray.getQIOFrequency();
            check(ownerFrequency != null && otherFrequency != null, "A private frequency was not created");
            check(ownerFrequency != otherFrequency, "Two owners share one private frequency");
            check(count(source, Direction.EAST, Items.DIAMOND) == 0, "Source still holds diamonds");
            check(ownerFrequency.getStored(new ItemStack(Items.DIAMOND)) == PRIVATE_DIAMONDS,
                  "Owner frequency holds " + ownerFrequency.getStored(new ItemStack(Items.DIAMOND)) + " diamonds");
            check(otherFrequency.getTotalItemCount() == 0, "The other owner received " + otherFrequency.getTotalItemCount() + " items");
            check(DriveMetadata.load(ownerArray.getDriveSlots().get(0).getStack()).count() == PRIVATE_DIAMONDS,
                  "The owner's drive recorded " + DriveMetadata.load(ownerArray.getDriveSlots().get(0).getStack()).count());
            check(DriveMetadata.load(otherArray.getDriveSlots().get(0).getStack()).count() == 0, "The other owner's drive holds items");
            check(stored(portableItem, ownerPortable) == PRIVATE_DIAMONDS, "The owner's portable dashboard sees "
                  + stored(portableItem, ownerPortable));
            check(stored(portableItem, otherPortable) == 0, "The other portable dashboard sees " + stored(portableItem, otherPortable));
            check(FrequencyType.QIO.getFrequency(new FrequencyIdentity(name, false), UUID.randomUUID()) == null,
                  "A player who never created the private frequency can read it");
        });
    }

    private static String frequency(String prefix) {
        return prefix + IDS.incrementAndGet();
    }

    private static void link(TileEntityQIOComponent tile, String name, boolean isPublic, UUID owner) {
        tile.setFrequency(FrequencyType.QIO, new FrequencyIdentity(name, isPublic), owner);
    }

    private static BlockState facing(Block block, Direction direction) {
        return block.defaultBlockState().setValue(BlockStateProperties.FACING, direction);
    }

    private static <T extends TileEntityQIOComponent> T component(GameTestHelper helper, BlockPos pos, Class<T> type) {
        BlockEntity blockEntity = helper.getBlockEntity(pos);
        check(type.isInstance(blockEntity), pos + " has " + blockEntity);
        return type.cast(blockEntity);
    }

    private static BlockEntity chest(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, Blocks.CHEST);
        return helper.getBlockEntity(pos);
    }

    private static void give(BlockEntity chest, Item item, int amount) {
        long moved = TestPlatform.insertItems(chest, Direction.UP, new ItemStack(item, amount));
        check(moved == amount, "Inserted " + moved + " of " + amount + " " + item);
    }

    private static int count(BlockEntity chest, Direction side, Item item) {
        return TestPlatform.countItems(chest, side, item);
    }

    private static long stored(ItemPortableQIODashboard item, ItemStack stack) {
        return item.getFrequency(stack) instanceof QIOFrequency frequency ? frequency.getTotalItemCount() : -1;
    }

    private static int signal(GameTestHelper helper, BlockPos pos) {
        return helper.getBlockState(pos).getSignal(helper.getLevel(), helper.absolutePos(pos), Direction.NORTH);
    }
}
