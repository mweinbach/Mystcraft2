package ports.jurassicraft.gametest;

import java.util.ArrayList;
import java.util.HashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.ChatFormatting;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import ports.jurassicraft.components.ComponentsRegistry;
import ports.jurassicraft.content.Species;
import ports.jurassicraft.cultivator.CultivatorBlockEntity;
import ports.jurassicraft.cultivator.CultivatorRegistry;
import ports.jurassicraft.entity.DinosaurEntity;
import ports.jurassicraft.genetics.DNAData;
import ports.jurassicraft.genetics.Genetics;
import ports.jurassicraft.hatching.HatcheryRecipes;
import ports.jurassicraft.hatching.HatcheryBlockEntity;
import ports.jurassicraft.hatching.HatchedEggItem;
import ports.jurassicraft.hatching.HatcheryRegistry;
import ports.jurassicraft.machine.DnaMachineBlockEntity;
import ports.jurassicraft.machine.DnaMachineRegistry;

/** Real server checks for DNA preservation, recipe loading and Fabric transfers. */
public final class GeneticsMachineGameTests {
    private static void check(GameTestHelper helper, boolean result, String message) {
        if (!result) helper.fail(message);
    }

    @GameTest(template = "jurassicraft:empty")
    public static void geneticsSurviveHatcheryStages(GameTestHelper helper) {
        DNAData dna = DNAData.dinosaur(Species.TRICERATOPS, 100, "ACGTACGTAC");
        CompoundTag original = new CompoundTag();
        original.putString("SpecimenNote", "preserve me");
        ItemStack disc = Genetics.disc(original, dna);
        ItemStack tube = Genetics.synthesize(disc);
        ItemStack syringe = HatcheryRecipes.embryonic(tube, new ItemStack(ComponentsRegistry.PETRI_DISH.get()), new ItemStack(ComponentsRegistry.EMPTY_SYRINGE.get()));
        ItemStack egg = HatcheryRecipes.calcify(syringe, new ItemStack(Items.EGG));
        ItemStack hatched = HatcheryRecipes.incubate(egg, 75);
        check(helper, syringe.is(HatcheryRegistry.syringe(Species.TRICERATOPS)) && egg.is(HatcheryRegistry.egg(Species.TRICERATOPS)), "hatchery species identity changed");
        check(helper, hatched.is(HatcheryRegistry.hatchedEgg(Species.TRICERATOPS)) && Genetics.read(hatched.getTag()).filter(dna::equals).isPresent(), "offspring DNA was lost");
        check(helper, hatched.getTag().getBoolean("Gender") && hatched.getTag().getString("SpecimenNote").equals("preserve me"), "offspring metadata was lost");
        check(helper, HatcheryRecipes.calcify(new ItemStack(HatcheryRegistry.syringe(Species.TRICERATOPS)), new ItemStack(Items.EGG)).isEmpty(), "untagged syringe must not produce an egg");
        helper.succeed();
    }

    @GameTest(template = "jurassicraft:empty")
    public static void machinesIncubateBothSexesAndRetainProgress(GameTestHelper helper) {
        DNAData dna = DNAData.dinosaur(Species.TRICERATOPS, 100, "ACGTACGTAC");
        CompoundTag metadata = new CompoundTag();
        metadata.putString("SpecimenNote", "kept through machines");
        ItemStack tube = Genetics.synthesize(Genetics.disc(metadata, dna));

        BlockPos embryonicPos = new BlockPos(1, 1, 1);
        helper.setBlock(embryonicPos, HatcheryRegistry.block(HatcheryRegistry.Machine.EMBRYONIC));
        var level = helper.getLevel();
        var embryonicWorldPos = helper.absolutePos(embryonicPos);
        var embryonic = (HatcheryBlockEntity) level.getBlockEntity(embryonicWorldPos);
        embryonic.setItem(0, tube);
        embryonic.setItem(1, new ItemStack(ComponentsRegistry.PETRI_DISH.get()));
        embryonic.setItem(2, new ItemStack(ComponentsRegistry.EMPTY_SYRINGE.get()));
        var shown = embryonic.getUpdateTag();
        check(helper, shown.getBoolean("VisualSync") && shown.getBoolean("VisualDish") && shown.getBoolean("VisualTubes") && !shown.contains("Items"),
                "embryonic parts must sync without the inventory");
        var client = new HatcheryBlockEntity(embryonicWorldPos, embryonic.getBlockState(), HatcheryRegistry.Machine.EMBRYONIC);
        client.load(shown);
        check(helper, client.visualDish() && client.visualTubes(), "DNA shows the dish parts and the petri dish shows the tube rack");
        ItemStack keptTube = tube.copy();
        embryonic.removeItem(0, 1);
        client.load(embryonic.getUpdateTag());
        check(helper, !client.visualDish() && client.visualTubes(), "clearing the DNA slot hides the petri-dish parts");
        embryonic.setItem(0, keptTube);
        for (int i = 0; i < HatcheryRegistry.Machine.EMBRYONIC.ticks; i++)
            HatcheryBlockEntity.tick(level, embryonicWorldPos, embryonic.getBlockState(), embryonic);
        ItemStack syringe = embryonic.getItem(3).copy();
        check(helper, syringe.is(HatcheryRegistry.syringe(Species.TRICERATOPS))
                && embryonic.getItem(0).isEmpty() && embryonic.getItem(1).isEmpty() && embryonic.getItem(2).isEmpty(),
                "embryonic machine must consume its ingredients and produce a syringe");

        BlockPos calcifierPos = new BlockPos(2, 1, 1);
        helper.setBlock(calcifierPos, HatcheryRegistry.block(HatcheryRegistry.Machine.CALCIFIER));
        var calcifierWorldPos = helper.absolutePos(calcifierPos);
        var calcifier = (HatcheryBlockEntity) level.getBlockEntity(calcifierWorldPos);
        calcifier.setItem(0, syringe);
        calcifier.setItem(1, new ItemStack(Items.EGG));
        for (int i = 0; i < HatcheryRegistry.Machine.CALCIFIER.ticks; i++)
            HatcheryBlockEntity.tick(level, calcifierWorldPos, calcifier.getBlockState(), calcifier);
        ItemStack egg = calcifier.getItem(2).copy();
        check(helper, egg.is(HatcheryRegistry.egg(Species.TRICERATOPS))
                && calcifier.getItem(0).isEmpty() && calcifier.getItem(1).isEmpty(),
                "calcifier must consume the syringe and chicken egg");

        BlockPos incubatorPos = new BlockPos(3, 1, 1);
        helper.setBlock(incubatorPos, HatcheryRegistry.block(HatcheryRegistry.Machine.INCUBATOR));
        var incubatorWorldPos = helper.absolutePos(incubatorPos);
        var incubator = (HatcheryBlockEntity) level.getBlockEntity(incubatorWorldPos);
        incubator.setItem(0, egg.copy());
        incubator.setItem(1, egg.copy());
        incubator.setItem(5, new ItemStack(HatcheryRegistry.PEAT_MOSS_ITEM.get(), 2));
        incubator.setTemperature(0, 50);
        incubator.setTemperature(1, 55);
        for (int i = 0; i < 4000; i++) HatcheryBlockEntity.tick(level, incubatorWorldPos, incubator.getBlockState(), incubator);
        incubator.load(incubator.saveWithoutMetadata());
        for (int i = 0; i < 3999; i++) HatcheryBlockEntity.tick(level, incubatorWorldPos, incubator.getBlockState(), incubator);
        check(helper, incubator.getItem(0).is(HatcheryRegistry.egg(Species.TRICERATOPS)), "incubator finished early after reload");
        HatcheryBlockEntity.tick(level, incubatorWorldPos, incubator.getBlockState(), incubator);
        ItemStack female = incubator.getItem(0), male = incubator.getItem(1);
        check(helper, female.is(HatcheryRegistry.hatchedEgg(Species.TRICERATOPS))
                && male.is(HatcheryRegistry.hatchedEgg(Species.TRICERATOPS))
                && !female.getTag().getBoolean("Gender") && male.getTag().getBoolean("Gender")
                && incubator.getItem(5).isEmpty(), "incubator must use the temperature boundary and one peat moss per egg");
        check(helper, Genetics.read(female.getTag()).filter(dna::equals).isPresent()
                && Genetics.read(male.getTag()).filter(dna::equals).isPresent()
                && male.getTag().getString("SpecimenNote").equals("kept through machines"),
                "incubation must preserve genetics and specimen metadata");
        checkGenderTooltip(helper, female, "female");
        checkGenderTooltip(helper, male, "male");
        ItemStack uninitialized = new ItemStack(HatcheryRegistry.hatchedEgg(Species.TRICERATOPS));
        checkGenderTooltip(helper, uninitialized, "random");
        check(helper, !uninitialized.hasTag(), "viewing a tooltip must not initialize or mutate the egg");
        helper.succeed();
    }

    private static void checkGenderTooltip(GameTestHelper helper, ItemStack egg, String expected) {
        var lines = new ArrayList<Component>();
        ((HatchedEggItem) egg.getItem()).appendHoverText(egg, null, lines, TooltipFlag.Default.NORMAL);
        Component gender = Component.translatable("tooltip.jurassicraft.spawn_egg_gender",
                Component.translatable("mode.jurassicraft." + expected)).withStyle(ChatFormatting.GOLD);
        check(helper, lines.contains(gender), "hatched egg tooltip must show " + expected);
    }

    @GameTest(template = "jurassicraft:empty")
    public static void cultivatorConsumesWaterAndNutrientsForCoelacanth(GameTestHelper helper) {
        DNAData dna = DNAData.dinosaur(Species.COELACANTH, 100, "TTGGCCAATT");
        CompoundTag metadata = new CompoundTag();
        metadata.putString("SpecimenNote", "coelacanth sample");
        ItemStack tube = Genetics.synthesize(Genetics.disc(metadata, dna));
        ItemStack syringe = HatcheryRecipes.embryonic(tube, new ItemStack(ComponentsRegistry.PETRI_DISH.get()),
                new ItemStack(ComponentsRegistry.EMPTY_SYRINGE.get()));
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, CultivatorRegistry.CULTIVATOR.get());
        var level = helper.getLevel();
        var worldPos = helper.absolutePos(pos);
        var cultivator = (CultivatorBlockEntity) level.getBlockEntity(worldPos);
        cultivator.setItem(0, syringe);
        cultivator.setTemperature(55);
        for (int i = 0; i < 2; i++) {
            cultivator.setItem(2, new ItemStack(Items.WATER_BUCKET));
            CultivatorBlockEntity.tick(level, worldPos, cultivator.getBlockState(), cultivator);
        }
        cultivator.setItem(1, new ItemStack(Items.COOKED_PORKCHOP, 6));
        for (int i = 0; i < 6; i++) CultivatorBlockEntity.tick(level, worldPos, cultivator.getBlockState(), cultivator);
        CompoundTag before = cultivator.saveWithoutMetadata();
        check(helper, before.getInt("Water") == 2 && cultivator.getItem(3).is(Items.BUCKET)
                && cultivator.getItem(3).getCount() == 2, "cultivator must retain two empty buckets");
        for (String nutrient : new String[]{"Proximates", "Minerals", "Vitamins", "Lipids"})
            check(helper, before.getInt(nutrient) >= CultivatorBlockEntity.REQUIRED, "insufficient " + nutrient);
        cultivator.load(before);
        for (int i = 0; i < CultivatorBlockEntity.TICKS; i++)
            CultivatorBlockEntity.tick(level, worldPos, cultivator.getBlockState(), cultivator);
        ItemStack result = cultivator.getItem(0);
        CompoundTag after = cultivator.saveWithoutMetadata();
        check(helper, result.is(HatcheryRegistry.hatchedEgg(Species.COELACANTH)) && result.getTag().getBoolean("Gender")
                && Genetics.read(result.getTag()).filter(dna::equals).isPresent()
                && result.getTag().getString("SpecimenNote").equals("coelacanth sample"),
                "cultivator must produce the selected sex and preserve the sample");
        check(helper, after.getInt("Water") == 1, "cultivator must consume one unit of water");
        for (String nutrient : new String[]{"Proximates", "Minerals", "Vitamins", "Lipids"})
            check(helper, after.getInt(nutrient) == before.getInt(nutrient) - CultivatorBlockEntity.REQUIRED,
                    "cultivator must consume the required " + nutrient);
        checkGenderTooltip(helper, result, "male");
        helper.succeed();
    }

    @GameTest(template = "jurassicraft:empty")
    public static void gestatedCoelacanthCanBeReleasedIntoWater(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 1, 2);
        helper.setBlock(pos, Blocks.WATER);
        var level = helper.getLevel();
        BlockPos worldPos = helper.absolutePos(pos);
        var player = helper.makeMockPlayer();
        player.getAbilities().instabuild = false;
        player.setPos(worldPos.getX() + .5, worldPos.getY() + 1, worldPos.getZ() + .5);
        player.setXRot(90);
        ItemStack fish = new ItemStack(HatcheryRegistry.hatchedEgg(Species.COELACANTH));
        DNAData dna = DNAData.dinosaur(Species.COELACANTH, 100, "ACGTACGTAC");
        Genetics.write(fish.getOrCreateTag(), dna);
        fish.getTag().putBoolean("Gender", true);
        player.setItemInHand(InteractionHand.OFF_HAND, fish);

        player.getAbilities().mayBuild = false;
        check(helper, fish.getItem().use(level, player, InteractionHand.OFF_HAND).getResult() == InteractionResult.FAIL
                && fish.getCount() == 1, "denied placement must leave the fish unconsumed");
        player.getAbilities().mayBuild = true;
        AABB target = new AABB(worldPos).inflate(1);
        var before = new HashSet<java.util.UUID>();
        for (DinosaurEntity entity : level.getEntitiesOfClass(DinosaurEntity.class, target)) before.add(entity.getUUID());
        check(helper, fish.getItem().use(level, player, InteractionHand.OFF_HAND).getResult().consumesAction()
                && fish.isEmpty(), "using the off-hand item on a water source must consume it");
        helper.runAfterDelay(1, () -> {
            var spawned = level.getEntitiesOfClass(DinosaurEntity.class, target,
                    entity -> entity.species() == Species.COELACANTH && !before.contains(entity.getUUID()));
            check(helper, spawned.size() == 1, "exactly one new coelacanth must be released");
            DinosaurEntity animal = spawned.get(0);
            try {
                check(helper, animal.isMale() && animal.isBaby(), "water release must retain sex and baby age");
                check(helper, animal.getDNAQuality() == 100 && animal.getGenetics().equals(dna.genetics()),
                        "water release must retain DNA");
                check(helper, animal.getOwnerUUID() == null, "coelacanth is not an imprintable species");
            } finally {
                animal.discard();
            }
            helper.succeed();
        });
    }

    @GameTest(template = "jurassicraft:empty")
    public static void sidedTransfersRespectModeAndTransactions(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 1, 2);
        helper.setBlock(pos, DnaMachineRegistry.COMBINATOR.get());
        BlockPos worldPos = helper.absolutePos(pos);
        DnaMachineBlockEntity entity = (DnaMachineBlockEntity) helper.getLevel().getBlockEntity(worldPos);
        check(helper, entity != null, "combinator block entity missing");
        var input = TestPlatform.get().items(entity, Direction.UP);
        var output = TestPlatform.get().items(entity, Direction.DOWN);
        check(helper, input != null && output != null, "sided item access not registered");
        ItemStack disc = Genetics.disc(null, DNAData.dinosaur(Species.TRICERATOPS, 100, "ACGTACGTAC"));
        check(helper, input.insert(disc.copy(), true).isEmpty(), "normal mode insertion failed");
        check(helper, entity.getItem(0).isEmpty(), "simulated insertion changed inventory");
        check(helper, input.insert(disc.copy(), false).isEmpty(), "committed insertion failed");
        entity.setItem(2, disc.copy());
        entity.setHybridizerMode(true);
        var visual = entity.getUpdateTag();
        check(helper, visual.getBoolean("VisualSync") && visual.getBoolean("HybridizerMode") && !visual.contains("Items"),
                "hybridizer mode must reach clients without the inventory");
        var viewer = new DnaMachineBlockEntity(worldPos, entity.getBlockState());
        viewer.load(visual);
        check(helper, viewer.hybridizerMode(), "client must use the hybridizer screens");
        check(helper, input.insert(disc.copy(), false).isEmpty(), "cached input stopped working after mode change");
        check(helper, output.extract(disc, 1, false).isEmpty(), "inactive output was extracted");
        check(helper, entity.getItem(0).getCount() == 1 && entity.getItem(3).getCount() == 1 && entity.getItem(2).getCount() == 1, "mode switch mixed slots or lost items");
        entity.setHybridizerMode(false);
        viewer.load(entity.getUpdateTag());
        check(helper, !viewer.hybridizerMode(), "client must restore the combinator screens");
        check(helper, output.extract(disc, 1, false).getCount() == 1, "active output could not be extracted");
        check(helper, entity.getItem(2).isEmpty() && entity.getItem(3).getCount() == 1, "switching back lost hybrid input");
        helper.succeed();
    }

    @GameTest(template = "jurassicraft:empty")
    public static void waterPotionSmeltingUsesPartialNbt(GameTestHelper helper) {
        ItemStack water = PotionUtils.setPotion(new ItemStack(Items.POTION), Potions.WATER);
        water.getOrCreateTag().putString("SpecimenNote", "extra tag");
        ItemStack other = PotionUtils.setPotion(new ItemStack(Items.POTION), Potions.AWKWARD);
        var recipes = helper.getLevel().getRecipeManager();
        var waterRecipe = recipes.getRecipeFor(RecipeType.SMELTING, new SimpleContainer(water), helper.getLevel());
        check(helper, waterRecipe.isPresent() && waterRecipe.get().getId().equals(new ResourceLocation("jurassicraft", "dna_nucleotides_from_water")), "water potion recipe missing or did not accept extra NBT");
        check(helper, recipes.getRecipeFor(RecipeType.SMELTING, new SimpleContainer(other), helper.getLevel()).isEmpty(), "other potions must not match nucleotide smelting");
        helper.succeed();
    }
}
