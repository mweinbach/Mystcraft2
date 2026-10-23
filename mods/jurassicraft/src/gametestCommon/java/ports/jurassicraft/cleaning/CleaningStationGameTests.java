package ports.jurassicraft.cleaning;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import ports.jurassicraft.gametest.TestPlatform;
import ports.jurassicraft.content.JurassicContent;
import ports.jurassicraft.content.Species;

/** Opt-in Forge GameTests. */
public final class CleaningStationGameTests {
    private static final BlockPos POS = new BlockPos(1, 1, 1);

    private CleaningStationGameTests() {}

    private static CleaningStationBlockEntity place(GameTestHelper helper) {
        helper.setBlock(POS, CleaningStationRegistry.BLOCK.get());
        return (CleaningStationBlockEntity) helper.getBlockEntity(POS);
    }

    private static void tick(GameTestHelper helper, CleaningStationBlockEntity station, int count) {
        for (int i = 0; i < count; i++) {
            CleaningStationBlockEntity.serverTick(helper.getLevel(), station.getBlockPos(), station.getBlockState(), station);
        }
    }

    private static void require(GameTestHelper helper, boolean condition, String message) {
        if (!condition) helper.fail(message);
    }

    @GameTest(template = "jurassicraft:cleaning_station_test")
    public static void waterAndAutomation(GameTestHelper helper) {
        CleaningStationBlockEntity station = place(helper);
        TestPlatform.ItemAccess hopper = TestPlatform.automation(station, Direction.UP);
        ItemStack fossil = new ItemStack(JurassicContent.ENCASED_FOSSILS.get(Species.VELOCIRAPTOR).get(), 8);
        require(helper, hopper.insert(0, fossil, false).isEmpty(), "fossil input should accept its stack");
        require(helper, !hopper.insert(2, new ItemStack(Items.STONE), false).isEmpty(), "output slots must reject input");
        require(helper, hopper.extract(0, 1, false).isEmpty(), "automation must not remove the fossil input");
        require(helper, hopper.insert(1, new ItemStack(Items.WATER_BUCKET), false).isEmpty(), "water bucket should be accepted");
        for (int cycle = 0; cycle < 8; cycle++) {
            tick(helper, station, CleaningStationBlockEntity.CLEAN_TICKS);
            int results = 0;
            for (int slot = 2; slot < 8; slot++) {
                ItemStack result = hopper.extract(slot, 64, false);
                if (!result.isEmpty()) {
                    boolean valid = Species.VELOCIRAPTOR.bones().stream().anyMatch(bone ->
                            result.is(JurassicContent.BONES.get(Species.VELOCIRAPTOR).get(bone).get()));
                    require(helper, valid, "result must be a velociraptor bone");
                    results += result.getCount();
                }
            }
            require(helper, results == 1, "each fossil must produce exactly one bone");
        }
        require(helper, station.data().get(0) == 0, "one bucket should last exactly eight cycles");
        require(helper, station.getItem(0).isEmpty(), "all eight fossils should be consumed");
        require(helper, hopper.extract(1, 1, false).is(Items.BUCKET), "automation must retrieve the empty bucket");
        helper.succeed();
    }

    @GameTest(template = "jurassicraft:cleaning_station_test")
    public static void blockedAndRestored(GameTestHelper helper) {
        CleaningStationBlockEntity station = place(helper);
        station.setItem(0, new ItemStack(JurassicContent.ENCASED_FOSSILS.get(Species.TRICERATOPS).get()));
        station.setItem(1, new ItemStack(Items.WATER_BUCKET));
        for (int slot = 2; slot < 8; slot++) station.setItem(slot, new ItemStack(Items.STONE, 64));
        tick(helper, station, 10);
        require(helper, station.data().get(0) == 0, "blocked outputs must not consume water");
        require(helper, station.getItem(1).is(Items.WATER_BUCKET), "blocked outputs must not consume a bucket");
        CompoundTag blocked = station.saveWithoutMetadata();
        require(helper, blocked.contains("PendingResult"), "chosen result must be saved even when blocked");
        station.setItem(2, ItemStack.EMPTY);
        tick(helper, station, 50);
        CompoundTag midCycle = station.saveWithoutMetadata();
        require(helper, midCycle.getCompound("PendingResult").equals(blocked.getCompound("PendingResult")), "unblocking must not reroll the result");
        CleaningStationBlockEntity restored = new CleaningStationBlockEntity(station.getBlockPos(), station.getBlockState());
        restored.load(midCycle);
        require(helper, restored.data().get(0) == 1550 && restored.data().get(2) == 50, "water and progress must survive loading");
        tick(helper, restored, 150);
        require(helper, restored.getItem(0).isEmpty(), "restored cycle must consume the fossil");
        require(helper, ItemStack.isSameItemSameTags(restored.getItem(2), ItemStack.of(blocked.getCompound("PendingResult"))), "restored cycle must deliver the saved result");
        helper.succeed();
    }

    @GameTest(template = "jurassicraft:cleaning_station_test")
    public static void visualStateFollowsProcessing(GameTestHelper helper) {
        CleaningStationBlockEntity station = place(helper);
        ItemStack fossil = new ItemStack(JurassicContent.ENCASED_FOSSILS.get(Species.TRICERATOPS).get());
        fossil.getOrCreateTag().putString("Sample", "visible");
        station.setItem(0, fossil);
        tick(helper, station, 1);
        CompoundTag dry = station.getUpdateTag();
        require(helper, !dry.getBoolean("VisualActive") && !dry.contains("Inventory"), "dry station must display an inactive sample without exposing its inventory");
        CleaningStationBlockEntity client = new CleaningStationBlockEntity(station.getBlockPos(), station.getBlockState());
        client.load(dry);
        require(helper, ItemStack.isSameItemSameTags(client.visualInput(), fossil), "sample and metadata must reach the renderer");
        station.setItem(1, new ItemStack(Items.WATER_BUCKET));
        tick(helper, station, 1);
        CompoundTag running = station.getUpdateTag();
        require(helper, running.getBoolean("VisualActive"), "station must animate while cleaning");
        client.load(running);
        for (int i = 0; i < 40; i++) CleaningStationBlockEntity.clientTick(helper.getLevel(), client.getBlockPos(), client.getBlockState(), client);
        require(helper, client.visualRotation(1) > 0, "the sample must turn on the client");
        station.setItem(0, ItemStack.EMPTY);
        tick(helper, station, 1);
        client.load(station.getUpdateTag());
        require(helper, client.visualInput().isEmpty() && !station.getUpdateTag().getBoolean("VisualActive"), "removing the sample must stop the display");
        helper.succeed();
    }

    @GameTest(template = "jurassicraft:cleaning_station_test")
    public static void plantOutputs(GameTestHelper helper) {
        ItemStack input = new ItemStack(JurassicContent.PLANT_FOSSIL_BLOCK.get());
        Set<Item> outputs = new HashSet<>();
        RandomSource random = RandomSource.create(4391L);
        for (int i = 0; i < 10000; i++) outputs.add(CleaningStationBlockEntity.chooseResult(input, random).getItem());
        require(helper, outputs.equals(Set.of(JurassicContent.PLANT_FOSSIL.get(), JurassicContent.TWIG_FOSSIL.get(),
                Items.COAL, Items.FLINT, Items.COBBLESTONE)), "plant cleaning must include both fossils and all three byproducts");
        helper.succeed();
    }
}
