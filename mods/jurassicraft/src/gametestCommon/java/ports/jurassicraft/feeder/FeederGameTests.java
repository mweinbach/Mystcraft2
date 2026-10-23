package ports.jurassicraft.feeder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import ports.jurassicraft.gametest.TestPlatform;
import ports.jurassicraft.content.Species;
import ports.jurassicraft.entity.DinosaurEntity;
import ports.jurassicraft.entity.DinosaurRegistry;

public final class FeederGameTests {
    private static final BlockPos POS = new BlockPos(1, 1, 1);
    private FeederGameTests() {}
    private static void require(GameTestHelper helper, boolean value, String message) {
        if (!value) helper.fail(message);
    }
    private static FeederBlockEntity place(GameTestHelper helper) {
        helper.setBlock(POS, FeederRegistry.BLOCK.get());
        return (FeederBlockEntity) helper.getBlockEntity(POS);
    }

    @GameTest(template = "jurassicraft:feeder_test")
    public static void inventoryAndPersistence(GameTestHelper helper) {
        FeederBlockEntity feeder = place(helper);
        TestPlatform.ItemAccess automated = TestPlatform.automation(feeder, Direction.NORTH);
        require(helper, automated.insert(0, new ItemStack(Items.BEEF, 8), false).isEmpty(), "meat slots must accept meat");
        require(helper, !automated.insert(9, new ItemStack(Items.BEEF), false).isEmpty(), "plant slots must reject meat");
        require(helper, automated.insert(9, new ItemStack(Items.WHEAT, 8), false).isEmpty(), "plant slots must accept plants");
        require(helper, !automated.insert(0, new ItemStack(Items.WHEAT), false).isEmpty(), "meat slots must reject plants");
        require(helper, automated.extract(0, 1, false).isEmpty(), "automated extraction is disabled");
        require(helper, feeder.comparatorSignal() > 0, "comparator should detect food");
        CompoundTag saved = feeder.saveWithoutMetadata();
        FeederBlockEntity restored = new FeederBlockEntity(feeder.getBlockPos(), feeder.getBlockState());
        restored.load(saved);
        require(helper, restored.getItem(0).getCount() == 8 && restored.getItem(0).is(Items.BEEF), "meat inventory must persist");
        require(helper, restored.getItem(9).getCount() == 8 && restored.getItem(9).is(Items.WHEAT), "plant inventory must persist");
        helper.succeed();
    }

    @GameTest(template = "jurassicraft:feeder_test", timeoutTicks = 80)
    public static void feedsHungryHerbivore(GameTestHelper helper) {
        FeederBlockEntity feeder = place(helper);
        feeder.setItem(0, new ItemStack(Items.BEEF, 2));
        feeder.setItem(9, new ItemStack(Items.CARROT, 2));
        DinosaurEntity dino = DinosaurRegistry.type(Species.TRICERATOPS).create(helper.getLevel());
        if (dino == null) { helper.fail("could not create dinosaur"); return; }
        BlockPos near = helper.absolutePos(new BlockPos(1, 1, 2));
        dino.moveTo(near.getX() + 0.5, near.getY(), near.getZ() + 0.5, 0, 0);
        CompoundTag state = new CompoundTag();
        dino.addAdditionalSaveData(state);
        state.putInt("Energy", 0);
        state.putInt("DigestingFood", 0);
        dino.readAdditionalSaveData(state);
        helper.getLevel().addFreshEntity(dino);
        require(helper, dino.needsFood(), "test dinosaur should be hungry");
        require(helper, feeder.hasFoodFor(dino), "the feeder should offer food matching the dinosaur's diet");
        helper.succeedWhen(() -> {
            require(helper, feeder.getItem(9).getCount() == 1, "one suitable food should be consumed");
            require(helper, feeder.getItem(0).getCount() == 2, "an herbivore must not consume meat");
            require(helper, dino.isAlive(), "dinosaur must remain alive while feeding");
            CompoundTag tag = feeder.getUpdateTag();
            require(helper, tag.getBoolean("VisualOpen") && !tag.contains("Inventory"), "feeding must open the visual lids without sending the inventory");
            FeederBlockEntity client = new FeederBlockEntity(feeder.getBlockPos(), feeder.getBlockState());
            client.load(tag);
            for (int i = 0; i < 25; i++) FeederBlockEntity.clientTick(helper.getLevel(), client.getBlockPos(), client.getBlockState(), client);
            require(helper, client.lidOpening(1) > .99F, "client lids must reach their fully open position");
            tag.putBoolean("VisualOpen", false);
            client.load(tag);
            for (int i = 0; i < 25; i++) FeederBlockEntity.clientTick(helper.getLevel(), client.getBlockPos(), client.getBlockState(), client);
            require(helper, client.lidOpening(1) == 0, "client lids must close again");
        });
    }
}
