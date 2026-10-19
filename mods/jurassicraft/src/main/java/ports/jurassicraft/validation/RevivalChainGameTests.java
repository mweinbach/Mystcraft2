package ports.jurassicraft.validation;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.cleaning.CleaningStationBlockEntity;
import ports.jurassicraft.cleaning.CleaningStationRegistry;
import ports.jurassicraft.components.ComponentsRegistry;
import ports.jurassicraft.content.JurassicContent;
import ports.jurassicraft.content.Species;
import ports.jurassicraft.entity.DinosaurEntity;
import ports.jurassicraft.genetics.Genetics;
import ports.jurassicraft.genetics.GeneticsRegistry;
import ports.jurassicraft.grinder.FossilGrinderRecipes;
import ports.jurassicraft.hatching.HatcheryRecipes;
import ports.jurassicraft.hatching.HatcheryRegistry;

/** Opt-in integration tests. */
@GameTestHolder(PortMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RevivalChainGameTests {
    private RevivalChainGameTests() {}
    private static void check(GameTestHelper helper, boolean condition, String message) {
        if (!condition) helper.fail(message);
    }

    @GameTest(template = "cleaning_station_test", templateNamespace = PortMod.MOD_ID)
    public static void fossilToHatchedDinosaur(GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        AABB fixture = new AABB(origin, origin.offset(3, 3, 3));
        helper.getLevel().getEntitiesOfClass(DinosaurEntity.class, fixture, animal -> fixture.contains(animal.position()))
                .forEach(DinosaurEntity::discard);
        Species species = Species.VELOCIRAPTOR;
        BlockPos stationPos = new BlockPos(1, 1, 1);
        helper.setBlock(stationPos, CleaningStationRegistry.BLOCK.get());
        CleaningStationBlockEntity station = (CleaningStationBlockEntity) helper.getBlockEntity(stationPos);
        var inventory = station.getCapability(ForgeCapabilities.ITEM_HANDLER).orElseThrow(IllegalStateException::new);
        check(helper, inventory.insertItem(CleaningStationBlockEntity.INPUT,
                new ItemStack(JurassicContent.ENCASED_FOSSILS.get(species).get()), false).isEmpty(), "cleaner must accept fossil");
        check(helper, inventory.insertItem(CleaningStationBlockEntity.FUEL, new ItemStack(Items.WATER_BUCKET), false).isEmpty(), "cleaner must accept water");
        for (int i = 0; i < CleaningStationBlockEntity.CLEAN_TICKS; i++)
            CleaningStationBlockEntity.serverTick(helper.getLevel(), station.getBlockPos(), station.getBlockState(), station);
        ItemStack bone = inventory.extractItem(CleaningStationBlockEntity.FIRST_OUTPUT, 1, false);
        check(helper, !bone.isEmpty(), "cleaning must produce a fossil bone");
        bone.getOrCreateTag().putString("SampleMarker", "preserved");
        RandomSource random = RandomSource.create(1234L);
        ItemStack tissue = ItemStack.EMPTY;
        for (int i = 0; i < 100 && tissue.isEmpty(); i++) {
            ItemStack result = FossilGrinderRecipes.grind(bone, random);
            if (result.is(GeneticsRegistry.softTissue(species).get())) tissue = result;
        }
        check(helper, !tissue.isEmpty(), "grinding must be able to yield matching tissue");
        ItemStack disc = Genetics.sequence(tissue, random);
        check(helper, !disc.isEmpty(), "tissue must sequence into a disc");
        for (int i = 0; i < 20 && Genetics.read(disc.getTag()).orElseThrow().quality() < 100; i++) {
            ItemStack next = Genetics.sequence(tissue, random);
            disc = Genetics.combine(disc, next);
            check(helper, !disc.isEmpty(), "same-species DNA discs must combine");
        }
        ItemStack tube = Genetics.synthesize(disc);
        check(helper, tube.is(GeneticsRegistry.dinosaurDna(species).get()), "complete disc must synthesize matching DNA");
        var dna = Genetics.read(tube.getTag()).orElseThrow();
        check(helper, dna.quality() == 100 && "preserved".equals(tube.getTag().getString("SampleMarker")), "DNA and sample metadata must survive synthesis");
        ItemStack embryo = HatcheryRecipes.embryonic(tube, new ItemStack(ComponentsRegistry.PETRI_DISH.get()),
                new ItemStack(ComponentsRegistry.EMPTY_SYRINGE.get()));
        ItemStack egg = HatcheryRecipes.calcify(embryo, new ItemStack(Items.EGG));
        ItemStack hatched = HatcheryRecipes.incubate(egg, 75);
        check(helper, hatched.is(HatcheryRegistry.hatchedEgg(species)), "DNA must advance through embryonic, calcification and incubation stages");
        check(helper, Genetics.read(hatched.getTag()).filter(dna::equals).isPresent()
                && "preserved".equals(hatched.getTag().getString("SampleMarker")), "incubation must preserve sample DNA");

        helper.setBlock(stationPos, Blocks.AIR);
        BlockPos floor = new BlockPos(1, 0, 1);
        helper.setBlock(floor, Blocks.STONE);
        Player player = helper.makeMockPlayer();
        player.getAbilities().instabuild = false;
        player.setItemInHand(InteractionHand.MAIN_HAND, hatched);
        BlockPos absoluteFloor = helper.absolutePos(floor);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(absoluteFloor), Direction.UP, absoluteFloor, false);
        AABB hatchArea = new AABB(absoluteFloor.above()).inflate(2);
        var existing = helper.getLevel().getEntitiesOfClass(DinosaurEntity.class, hatchArea).stream()
                .map(DinosaurEntity::getUUID).collect(java.util.stream.Collectors.toSet());
        check(helper, hatched.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit)).consumesAction(), "ready egg must hatch");
        var animals = helper.getLevel().getEntitiesOfClass(DinosaurEntity.class, hatchArea,
                animal -> animal.species() == species && !existing.contains(animal.getUUID()));
        check(helper, animals.size() == 1, "egg must spawn exactly one matching dinosaur");
        DinosaurEntity animal = animals.get(0);
        check(helper, animal.isBaby() && animal.isMale() && animal.getDNAQuality() == dna.quality()
                && animal.getGenetics().equals(dna.genetics()), "hatchling must keep sex, age and DNA");
        check(helper, hatched.isEmpty(), "hatching must consume the egg in survival mode");
        helper.succeed();
    }
}
