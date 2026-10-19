package ports.jurassicraft.compat.jade;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.content.Species;
import ports.jurassicraft.entity.DinosaurEntity;
import ports.jurassicraft.entity.DinosaurRegistry;
import ports.jurassicraft.feeder.FeederBlockEntity;
import ports.jurassicraft.feeder.FeederRegistry;
import ports.jurassicraft.hatching.HatcheryBlockEntity;
import ports.jurassicraft.hatching.HatcheryRegistry;

/** Runs with or without Jade; only snapshot collection is common gameplay code. */
@GameTestHolder(PortMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class InspectionGameTests {
    private InspectionGameTests() {}
    private static void check(GameTestHelper h, boolean value, String message) { if (!value) h.fail(message); }
    @GameTest(template = "cleaning_station_test", templateNamespace = PortMod.MOD_ID)
    public static void serverSnapshotsUseLiveState(GameTestHelper h) {
        BlockPos relative = new BlockPos(1, 1, 1);
        h.setBlock(relative, FeederRegistry.BLOCK.get());
        FeederBlockEntity feeder = (FeederBlockEntity) h.getBlockEntity(relative);
        var items = feeder.getCapability(ForgeCapabilities.ITEM_HANDLER).resolve().orElseThrow();
        check(h, items.insertItem(0, new ItemStack(Items.BEEF, 64), false).isEmpty(), "meat slot must accept test food");
        check(h, items.insertItem(9, new ItemStack(Items.WHEAT, 32), false).isEmpty(), "plant slot must accept test food");
        CompoundTag food = InspectionData.feeder(feeder);
        check(h, food.getInt("Meat") == 64 && food.getInt("Plants") == 32, "feeder snapshot must reflect both separate food inventories");
        h.setBlock(relative, HatcheryRegistry.block(HatcheryRegistry.Machine.INCUBATOR));
        HatcheryBlockEntity incubator = (HatcheryBlockEntity) h.getBlockEntity(relative);
        ItemStack egg = new ItemStack(HatcheryRegistry.egg(Species.TYRANNOSAURUS));
        egg.getOrCreateTag().putString("InspectionTest", "preserved");
        incubator.setItem(2, egg);
        incubator.setTemperature(2, 75);
        CompoundTag state = incubator.saveWithoutMetadata();
        state.putIntArray("Progress", new int[]{0, 0, 4000, 0, 0});
        incubator.load(state);
        var eggs = InspectionData.incubator(incubator).getList("Eggs", Tag.TAG_COMPOUND);
        check(h, eggs.size() == 1, "incubator snapshot must omit empty slots");
        CompoundTag entry = eggs.getCompound(0);
        check(h, entry.getInt("Slot") == 2 && entry.getInt("Progress") == 4000 && entry.getInt("Temperature") == 75,
                "incubator snapshot must carry each egg's server progress and temperature");
        ItemStack copy = ItemStack.of(entry.getCompound("Egg"));
        check(h, ItemStack.isSameItemSameTags(copy, egg), "incubator snapshot must preserve egg genetics and item identity");
        h.setBlock(relative, HatcheryRegistry.block(HatcheryRegistry.Machine.EMBRYONIC));
        check(h, InspectionData.incubator((HatcheryBlockEntity) h.getBlockEntity(relative)).isEmpty(),
                "other hatchery machines must not advertise incubator data");
        DinosaurEntity dinosaur = DinosaurRegistry.type(Species.VELOCIRAPTOR).create(h.getLevel());
        check(h, dinosaur != null, "could not create a dinosaur");
        dinosaur.setMale(true);
        var owner = h.makeMockPlayer();
        dinosaur.setOwner(owner.getUUID());
        CompoundTag dino = InspectionData.dinosaur(dinosaur, "Keeper");
        check(h, dino.getBoolean("Male") && dino.getString("Order").equals("wander") && dino.getUUID("Owner").equals(owner.getUUID())
                && dino.getString("OwnerName").equals("Keeper"), "dinosaur snapshot must contain synchronized gender, owner and order");
        h.succeed();
    }
}
