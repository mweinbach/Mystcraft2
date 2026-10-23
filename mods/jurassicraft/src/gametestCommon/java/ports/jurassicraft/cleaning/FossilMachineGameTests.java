package ports.jurassicraft.cleaning;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import ports.jurassicraft.assembly.AssemblyRegistry;
import ports.jurassicraft.assembly.SkeletonDisplayItem;
import ports.jurassicraft.assembly.SkeletonRecipes;
import ports.jurassicraft.content.JurassicContent;
import ports.jurassicraft.content.Species;
import ports.jurassicraft.gametest.TestPlatform;
import ports.jurassicraft.genetics.GeneticsRegistry;
import ports.jurassicraft.grinder.FossilGrinderBlockEntity;
import ports.jurassicraft.grinder.FossilGrinderRecipes;
import ports.jurassicraft.grinder.FossilGrinderRegistry;
import ports.jurassicraft.grinder.GrinderInput;

/** Loader-level regression checks for automation, persistence and fossil assembly. */
public final class FossilMachineGameTests {
    private static final BlockPos POS = new BlockPos(1, 1, 1);
    private static void check(GameTestHelper helper, boolean value, String message) { if (!value) helper.fail(message); }
    private static void tick(GameTestHelper helper, CleaningStationBlockEntity station, int count) {
        for (int i = 0; i < count; i++) CleaningStationBlockEntity.serverTick(helper.getLevel(), station.getBlockPos(), station.getBlockState(), station);
    }
    private static void tick(GameTestHelper helper, FossilGrinderBlockEntity grinder, int count) {
        for (int i = 0; i < count; i++) FossilGrinderBlockEntity.tick(helper.getLevel(), grinder.getBlockPos(), grinder.getBlockState(), grinder);
    }

    @GameTest(template = "jurassicraft:cleaning_station_test")
    public static void cleaningAutomationAndPersistence(GameTestHelper helper) {
        helper.setBlock(POS, CleaningStationRegistry.BLOCK.get());
        var station = (CleaningStationBlockEntity) helper.getBlockEntity(POS);
        TestPlatform.ItemAccess storage = TestPlatform.get().items(station, Direction.UP);
        check(helper, storage != null, "cleaning station must expose item automation");
        ItemStack fossil = new ItemStack(JurassicContent.ENCASED_FOSSILS.get(Species.VELOCIRAPTOR).get());
        check(helper, storage.insert(fossil.copy(), true).isEmpty(), "fossil must be accepted");
        check(helper, station.getItem(0).isEmpty(), "simulated transfers must not change the inventory");
        check(helper, storage.insert(fossil.copy(), false).isEmpty(), "fossil insertion failed");
        check(helper, storage.insert(new ItemStack(Items.WATER_BUCKET), false).isEmpty(), "bucket insertion failed");
        check(helper, storage.extract(fossil, 1, false).isEmpty(), "automation must not extract the input");
        tick(helper, station, 80);
        CompoundTag saved = station.saveWithoutMetadata();
        check(helper, saved.getInt("Water") == 1520 && saved.getInt("Progress") == 80 && saved.contains("PendingResult"),
                "water, progress and chosen output must be saved");
        var restored = new CleaningStationBlockEntity(station.getBlockPos(), station.getBlockState());
        restored.load(saved);
        tick(helper, restored, 120);
        check(helper, restored.getItem(0).isEmpty(), "restored station must consume its fossil");
        ItemStack expected = ItemStack.of(saved.getCompound("PendingResult"));
        boolean found = false;
        for (int slot = CleaningStationBlockEntity.FIRST_OUTPUT; slot < CleaningStationBlockEntity.SLOT_COUNT; slot++) {
            if (ItemStack.isSameItemSameTags(expected, restored.getItem(slot))) found = true;
        }
        check(helper, found, "restored station must produce the same chosen output");
        check(helper, station.canTakeItemThroughFace(1, station.getItem(1), Direction.UP), "empty bucket must be extractable");
        helper.succeed();
    }

    @GameTest(template = "jurassicraft:cleaning_station_test")
    public static void grinderCyclePersistenceAndReplacement(GameTestHelper helper) {
        helper.setBlock(POS, FossilGrinderRegistry.FOSSIL_GRINDER.get());
        var grinder = (FossilGrinderBlockEntity) helper.getBlockEntity(POS);
        TestPlatform.ItemAccess top = TestPlatform.get().items(grinder, Direction.UP);
        TestPlatform.ItemAccess bottom = TestPlatform.get().items(grinder, Direction.DOWN);
        check(helper, top != null && bottom != null, "grinder must expose sided item automation");
        ItemStack skull = new ItemStack(JurassicContent.BONES.get(Species.VELOCIRAPTOR).get("skull").get());
        check(helper, top.insert(skull.copy(), false).isEmpty(), "top must accept bones");
        check(helper, bottom.insert(skull.copy(), false).getCount() == 1, "bottom must reject input");
        tick(helper, grinder, 100);
        var restored = new FossilGrinderBlockEntity(grinder.getBlockPos(), grinder.getBlockState());
        restored.load(grinder.saveWithoutMetadata());
        tick(helper, restored, 100);
        check(helper, restored.getItem(0).isEmpty() && !restored.getItem(6).isEmpty(), "grinding progress must survive reload");
        helper.setBlock(POS, Blocks.AIR);
        helper.setBlock(POS, FossilGrinderRegistry.FOSSIL_GRINDER.get());
        grinder = (FossilGrinderBlockEntity) helper.getBlockEntity(POS);
        grinder.setItem(0, skull.copy());
        tick(helper, grinder, 100);
        grinder.setItem(0, new ItemStack(JurassicContent.BONES.get(Species.TRICERATOPS).get("skull").get()));
        tick(helper, grinder, 100);
        check(helper, !grinder.getItem(0).isEmpty() && grinder.getItem(6).isEmpty(), "replacement must not inherit progress");
        tick(helper, grinder, 100);
        check(helper, grinder.getItem(0).isEmpty() && !grinder.getItem(6).isEmpty(), "replacement must finish after a full cycle");
        helper.succeed();
    }

    @GameTest(template = "jurassicraft:cleaning_station_test")
    public static void fossilsAndFreshBonesPreserveDna(GameTestHelper helper) {
        ItemStack fossil = new ItemStack(JurassicContent.BONES.get(Species.VELOCIRAPTOR).get("skull").get());
        fossil.getOrCreateTag().putString("SampleMarker", "retained");
        RandomSource random = RandomSource.create(1234);
        int meal = 0, flint = 0, tissue = 0;
        for (int i = 0; i < 1200; i++) {
            ItemStack out = FossilGrinderRecipes.grind(fossil, random);
            if (out.is(Items.BONE_MEAL)) meal++;
            else if (out.is(Items.FLINT)) flint++;
            else if (out.is(GeneticsRegistry.softTissue(Species.VELOCIRAPTOR).get())) {
                tissue++;
                check(helper, fossil.getTag().equals(out.getTag()), "tissue must retain fossil NBT");
            } else helper.fail("unexpected fossil output");
        }
        check(helper, meal > 500 && meal < 700 && flint > 300 && flint < 500 && tissue > 140 && tissue < 260,
                "fossil output must follow the original 3:2:1 weighting");
        ItemStack fresh = new ItemStack(AssemblyRegistry.FRESH_BONES.get(Species.TYRANNOSAURUS).get("skull").get());
        fresh.getOrCreateTag().putString("SampleMarker", "fresh");
        check(helper, fresh.getItem() instanceof GrinderInput, "fresh bone must implement grinder input");
        ItemStack result = ((GrinderInput) fresh.getItem()).grind(fresh, random);
        check(helper, result.is(GeneticsRegistry.softTissue(Species.TYRANNOSAURUS).get()) && fresh.getTag().equals(result.getTag()),
                "fresh bone must always yield matching tissue and copy DNA");
        helper.succeed();
    }

    @GameTest(template = "jurassicraft:cleaning_station_test")
    public static void allSkeletonLayoutsRejectMixedBones(GameTestHelper helper) {
        for (Species species : Species.values()) for (boolean fresh : new boolean[] {false, true}) {
            String[][] layout = SkeletonRecipes.layout(species);
            check(helper, layout.length > 0, "missing skeleton layout " + species);
            SimpleContainer grid = new SimpleContainer(25);
            int first = -1;
            String firstBone = null;
            for (int y = 0; y < layout.length; y++) for (int x = 0; x < layout[y].length; x++) {
                String bone = layout[y][x];
                if (bone.isEmpty()) continue;
                int slot = x + y * 5;
                if (first < 0) { first = slot; firstBone = bone; }
                var registered = (fresh ? AssemblyRegistry.FRESH_BONES : JurassicContent.BONES).get(species).get(bone);
                check(helper, registered != null, "missing bone " + species + " " + bone);
                grid.setItem(slot, new ItemStack(registered.get()));
            }
            var match = SkeletonRecipes.find(grid);
            check(helper, match != null && match.species() == species && match.fresh() == fresh, "wrong assembled skeleton " + species);
            ItemStack display = SkeletonDisplayItem.create(species, fresh);
            check(helper, SkeletonDisplayItem.species(display) == species && SkeletonDisplayItem.fresh(display) == fresh,
                    "display item must retain its identity");
            var opposite = (fresh ? JurassicContent.BONES : AssemblyRegistry.FRESH_BONES).get(species).get(firstBone);
            grid.setItem(first, new ItemStack(opposite.get()));
            check(helper, SkeletonRecipes.find(grid) == null, "mixed fresh and fossil bones must fail " + species);
        }
        helper.succeed();
    }
}
