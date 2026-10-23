package ports.jurassicraft.assembly;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.AABB;
import ports.jurassicraft.content.JurassicContent;
import ports.jurassicraft.content.Species;
import ports.jurassicraft.genetics.GeneticsRegistry;
import ports.jurassicraft.grinder.GrinderInput;

import java.util.stream.Collectors;

public final class AssemblyGameTests {
    private static final BlockPos POS = new BlockPos(1, 1, 1);
    private static final String[][] VELOCIRAPTOR = {
            {"", "", "neck_vertebrae", "skull"},
            {"tail_vertebrae", "ribcage", "shoulder_bone", "tooth"},
            {"leg_bones", "leg_bones", "arm_bones", "claw"},
            {"foot_bones", "foot_bones", "", ""}};
    private AssemblyGameTests() {}
    private static void require(GameTestHelper helper, boolean value, String message) { if (!value) helper.fail(message); }
    private static ItemStack bone(Species species, String bone) { return new ItemStack(JurassicContent.BONES.get(species).get(bone).get()); }

    @GameTest(template = "jurassicraft:assembly_test")
    public static void layoutAndMixedSpecies(GameTestHelper helper) {
        SimpleContainer inventory = new SimpleContainer(25);
        // Offset from the top left is allowed, matching the original table.
        for (int y = 0; y < VELOCIRAPTOR.length; y++) for (int x = 0; x < VELOCIRAPTOR[y].length; x++) {
            String b = VELOCIRAPTOR[y][x];
            if (!b.isEmpty()) inventory.setItem((x + 1) + (y + 1) * 5, bone(Species.VELOCIRAPTOR, b));
        }
        require(helper, SkeletonRecipes.match(inventory) == Species.VELOCIRAPTOR, "matching offset layout should make the correct skeleton");
        inventory.setItem(4 + 5, bone(Species.TRICERATOPS, "skull"));
        require(helper, SkeletonRecipes.match(inventory) == null, "mixing species must reject the layout");
        inventory.setItem(4 + 5, bone(Species.VELOCIRAPTOR, "skull"));
        inventory.setItem(0, new ItemStack(Items.STONE));
        require(helper, SkeletonRecipes.match(inventory) == null, "extra items must reject the layout");
        helper.succeed();
    }

    @GameTest(template = "jurassicraft:assembly_test")
    public static void outputConsumesBones(GameTestHelper helper) {
        helper.setBlock(POS, AssemblyRegistry.TABLE.get());
        Player player = helper.makeMockPlayer();
        SkeletonAssemblyMenu menu = new SkeletonAssemblyMenu(1, player.getInventory(), helper.absolutePos(POS));
        for (int y = 0; y < VELOCIRAPTOR.length; y++) for (int x = 0; x < VELOCIRAPTOR[y].length; x++) {
            String b = VELOCIRAPTOR[y][x];
            if (!b.isEmpty()) menu.getSlot(1 + x + y * 5).set(bone(Species.VELOCIRAPTOR, b));
        }
        Slot result = menu.getSlot(0);
        ItemStack display = result.remove(1);
        require(helper, display.is(AssemblyRegistry.DISPLAY_ITEM.get()), "complete recipe should produce a display");
        require(helper, SkeletonDisplayItem.species(display) == Species.VELOCIRAPTOR, "display must preserve the species");
        result.onTake(player, display);
        for (int i = 1; i <= 25; i++) require(helper, !menu.getSlot(i).hasItem(), "crafting must consume one bone from each position");
        require(helper, !menu.getSlot(0).hasItem(), "a second result cannot be taken without more bones");
        helper.succeed();
    }

    @GameTest(template = "jurassicraft:assembly_test")
    public static void twoBlockTable(GameTestHelper helper) {
        helper.setBlock(POS, AssemblyRegistry.TABLE.get());
        BlockPos absolute = helper.absolutePos(POS);
        BlockState state = helper.getBlockState(POS);
        AssemblyRegistry.TABLE.get().setPlacedBy(helper.getLevel(), absolute, state, null, ItemStack.EMPTY);
        Direction other = state.getValue(SkeletonAssemblyBlock.FACING).getCounterClockWise();
        BlockPos second = POS.relative(other);
        require(helper, helper.getBlockState(second).is(AssemblyRegistry.TABLE.get()), "placement must create the second half");
        require(helper, helper.getBlockState(second).getValue(SkeletonAssemblyBlock.PART) == BedPart.HEAD, "second half must be marked as such");
        AABB area = new AABB(helper.absolutePos(second)).inflate(3);
        var previousItems = helper.getLevel().getEntitiesOfClass(ItemEntity.class, area).stream().map(ItemEntity::getUUID).collect(Collectors.toSet());
        helper.destroyBlock(second);
        require(helper, helper.getBlockState(POS).isAir(), "destroying either half must remove the other");
        var drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class, area,
                item -> !previousItems.contains(item.getUUID()) && item.getItem().is(AssemblyRegistry.TABLE_ITEM.get()));
        require(helper, drops.size() == 1 && drops.get(0).getItem().getCount() == 1, "breaking the table should drop one table item");
        drops.forEach(ItemEntity::discard);
        helper.succeed();
    }

    @GameTest(template = "jurassicraft:assembly_test")
    public static void everyUpstreamLayoutProducesItsMatchingDisplay(GameTestHelper helper) {
        helper.setBlock(POS, AssemblyRegistry.TABLE.get());
        Player player = helper.makeMockPlayer();
        for (var entry : AssemblyRecipeFixtures.RECIPES.entrySet()) {
            Species species = entry.getKey();
            String[][] recipe = entry.getValue();
            for (boolean fresh : new boolean[] {false, true}) {
                SkeletonAssemblyMenu menu = new SkeletonAssemblyMenu(1, player.getInventory(), helper.absolutePos(POS));
                int firstSlot = -1;
                for (int y = 0; y < recipe.length; y++) for (int x = 0; x < recipe[y].length; x++) {
                    String bone = recipe[y][x];
                    if (!bone.isEmpty()) {
                        int slot = 1 + x + y * 5;
                        if (firstSlot < 0) firstSlot = slot;
                        var item = (fresh ? AssemblyRegistry.FRESH_BONES : JurassicContent.BONES).get(species).get(bone);
                        require(helper, item != null, "registered bone missing: " + species + " " + bone);
                        menu.getSlot(slot).set(new ItemStack(item.get()));
                    }
                }
                ItemStack result = menu.getSlot(0).getItem();
                require(helper, result.is(AssemblyRegistry.DISPLAY_ITEM.get()), "no result for " + species + " fresh=" + fresh);
                require(helper, SkeletonDisplayItem.species(result) == species && SkeletonDisplayItem.fresh(result) == fresh,
                        "incorrect display for " + species + " fresh=" + fresh);
                SkeletonDisplayBlockEntity display = new SkeletonDisplayBlockEntity(helper.absolutePos(POS), AssemblyRegistry.DISPLAY.get().defaultBlockState());
                display.load(BlockItem.getBlockEntityData(result));
                require(helper, display.species() == species && display.fresh() == fresh, "display data did not survive placement");
                String firstBone = recipe[(firstSlot - 1) / 5][(firstSlot - 1) % 5];
                var wrongKind = (fresh ? JurassicContent.BONES : AssemblyRegistry.FRESH_BONES).get(species).get(firstBone);
                menu.getSlot(firstSlot).set(new ItemStack(wrongKind.get()));
                require(helper, menu.getSlot(0).getItem().isEmpty(), "mixed fossil and fresh bones accepted for " + species);
            }
        }
        require(helper, AssemblyRecipeFixtures.RECIPES.size() == Species.values().length, "fixtures must cover all species");
        helper.succeed();
    }

    @GameTest(template = "jurassicraft:assembly_test")
    public static void freshBonesPreserveGeneticsWhenGround(GameTestHelper helper) {
        ItemStack bone = new ItemStack(AssemblyRegistry.FRESH_BONES.get(Species.TYRANNOSAURUS).get("skull").get());
        CompoundTag dna = bone.getOrCreateTag();
        dna.putString("Genetics", "ACGTACGTAC");
        dna.putInt("DNAQuality", 81);
        dna.putInt("Dinosaur", Species.TYRANNOSAURUS.legacyId());
        require(helper, bone.getItem() instanceof GrinderInput, "fresh bones must be grindable");
        for (int i = 0; i < 12; i++) {
            ItemStack tissue = ((GrinderInput) bone.getItem()).grind(bone, RandomSource.create(i));
            require(helper, tissue.is(GeneticsRegistry.softTissue(Species.TYRANNOSAURUS).get()), "fresh bone should always produce matching soft tissue");
            require(helper, dna.equals(tissue.getTag()), "grinding must retain the bone's genetics");
        }
        helper.succeed();
    }

    @GameTest(template = "jurassicraft:assembly_test")
    public static void skeletonAppearanceSurvivesBreakingAndReplacement(GameTestHelper helper) {
        ItemStack displayItem = SkeletonDisplayItem.create(Species.TYRANNOSAURUS, true);
        CompoundTag appearance = BlockItem.getBlockEntityData(displayItem);
        appearance.putInt("Gender", 2);
        appearance.putInt("Pose", 1);
        helper.setBlock(POS, AssemblyRegistry.DISPLAY.get());
        BlockPos absolute = helper.absolutePos(POS);
        require(helper, BlockItem.updateCustomBlockEntityTag(helper.getLevel(), helper.makeMockPlayer(), absolute, displayItem), "appearance must load when placed");
        SkeletonDisplayBlockEntity placed = (SkeletonDisplayBlockEntity) helper.getLevel().getBlockEntity(absolute);
        require(helper, placed.species() == Species.TYRANNOSAURUS && placed.fresh() && placed.gender() == 2 && placed.pose() == 1,
                "placed display must retain its appearance");
        // GameTestHelper.destroyBlock deliberately suppresses drops; exercise the
        // real loot table to verify that appearance survives breaking and replacing.
        AABB area = new AABB(absolute).inflate(2);
        var previousItems = helper.getLevel().getEntitiesOfClass(ItemEntity.class, area).stream().map(ItemEntity::getUUID).collect(Collectors.toSet());
        helper.getLevel().destroyBlock(absolute, true);
        var drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class, area,
                item -> !previousItems.contains(item.getUUID()) && item.getItem().is(AssemblyRegistry.DISPLAY_ITEM.get()));
        require(helper, drops.size() == 1 && drops.get(0).getItem().getCount() == 1, "breaking a display should drop one skeleton");
        ItemStack dropped = drops.get(0).getItem();
        CompoundTag droppedData = BlockItem.getBlockEntityData(dropped);
        require(helper, SkeletonDisplayItem.species(dropped) == Species.TYRANNOSAURUS && SkeletonDisplayItem.fresh(dropped)
                && droppedData != null && droppedData.getInt("Gender") == 2 && droppedData.getInt("Pose") == 1,
                "the drop must preserve species, fresh state, gender and pose");
        helper.setBlock(POS, AssemblyRegistry.DISPLAY.get());
        require(helper, BlockItem.updateCustomBlockEntityTag(helper.getLevel(), helper.makeMockPlayer(), absolute, dropped), "dropped appearance must load when replaced");
        SkeletonDisplayBlockEntity replaced = (SkeletonDisplayBlockEntity) helper.getLevel().getBlockEntity(absolute);
        require(helper, replaced.species() == Species.TYRANNOSAURUS && replaced.fresh() && replaced.gender() == 2 && replaced.pose() == 1,
                "replaced display must retain its appearance");
        drops.forEach(ItemEntity::discard);
        helper.succeed();
    }
}
