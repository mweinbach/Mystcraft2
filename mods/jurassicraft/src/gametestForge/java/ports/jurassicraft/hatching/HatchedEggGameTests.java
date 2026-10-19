package ports.jurassicraft.hatching;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.content.Species;
import ports.jurassicraft.entity.DinosaurEntity;
import ports.jurassicraft.genetics.DNAData;
import ports.jurassicraft.genetics.Genetics;

@GameTestHolder(PortMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HatchedEggGameTests {
    private static final BlockPos FLOOR = new BlockPos(1, 0, 1);
    private HatchedEggGameTests() {}

    private static void check(GameTestHelper h, boolean condition, String message) {
        if (!condition) h.fail(message);
    }

    private static void prepare(GameTestHelper h) {
        BlockPos origin = h.absolutePos(BlockPos.ZERO);
        AABB fixture = new AABB(origin, origin.offset(3, 3, 3));
        h.getLevel().getEntitiesOfClass(DinosaurEntity.class, fixture, animal -> fixture.contains(animal.position()))
                .forEach(DinosaurEntity::discard);
        h.setBlock(FLOOR, Blocks.STONE);
    }

    private static UseOnContext use(GameTestHelper h, Player player, ItemStack egg) {
        player.setItemInHand(InteractionHand.MAIN_HAND, egg);
        BlockPos floor = h.absolutePos(FLOOR);
        return new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(floor), Direction.UP, floor, false));
    }

    private static DinosaurEntity hatch(GameTestHelper h, Player player, ItemStack egg, Species species) {
        BlockPos position = h.absolutePos(FLOOR).above();
        AABB area = new AABB(position).inflate(1);
        Set<UUID> existing = h.getLevel().getEntitiesOfClass(DinosaurEntity.class, area).stream()
                .map(DinosaurEntity::getUUID).collect(Collectors.toSet());
        check(h, egg.useOn(use(h, player, egg)).consumesAction(), "ready egg must hatch: " + species.id());
        var spawned = h.getLevel().getEntitiesOfClass(DinosaurEntity.class, area,
                animal -> animal.species() == species && !existing.contains(animal.getUUID()));
        check(h, spawned.size() == 1, "ready egg must spawn one matching dinosaur: " + species.id());
        return spawned.get(0);
    }

    @GameTest(template = "cleaning_station_test", templateNamespace = PortMod.MOD_ID)
    public static void untaggedCreativeEggsInitializeOnce(GameTestHelper h) {
        prepare(h);
        Player player = h.makeMockPlayer();
        player.getAbilities().instabuild = true;
        for (Species species : new Species[] {Species.VELOCIRAPTOR, Species.COELACANTH}) {
            ItemStack egg = new ItemStack(HatcheryRegistry.hatchedEgg(species));
            DinosaurEntity first = hatch(h, player, egg, species);
            DNAData dna = Genetics.read(egg.getTag()).orElseThrow();
            boolean male = egg.getTag().getBoolean("Gender");
            check(h, dna.dinosaur() == species && dna.quality() == 100 && first.isBaby()
                    && first.getGenetics().equals(dna.genetics()) && first.getDNAQuality() == 100 && first.isMale() == male,
                    "creative egg must initialize and apply full-quality DNA and gender");
            first.discard();
            DinosaurEntity second = hatch(h, player, egg, species);
            check(h, Genetics.read(egg.getTag()).filter(dna::equals).isPresent() && second.isMale() == male
                    && second.getGenetics().equals(dna.genetics()) && egg.getCount() == 1,
                    "reused creative egg must retain its initialized data");
            second.discard();
        }
        h.succeed();
    }

    @GameTest(template = "cleaning_station_test", templateNamespace = PortMod.MOD_ID)
    public static void existingEggDataIsPreserved(GameTestHelper h) {
        prepare(h);
        Player player = h.makeMockPlayer();
        player.getAbilities().instabuild = true;
        ItemStack egg = new ItemStack(HatcheryRegistry.hatchedEgg(Species.VELOCIRAPTOR));
        egg.getOrCreateTag().putString("Genetics", "ACGTACGTAC");
        egg.getTag().putInt("DNAQuality", 75);
        egg.getTag().putBoolean("Gender", false);
        egg.getTag().putString("Marker", "kept");
        DinosaurEntity animal = hatch(h, player, egg, Species.VELOCIRAPTOR);
        check(h, !animal.isMale() && animal.getDNAQuality() == 75 && animal.getGenetics().equals("ACGTACGTAC")
                && egg.getTag().getString("Marker").equals("kept"), "supplied genetic data and gender must be preserved");
        animal.discard();

        ItemStack mismatch = new ItemStack(HatcheryRegistry.hatchedEgg(Species.VELOCIRAPTOR));
        Genetics.write(mismatch.getOrCreateTag(), DNAData.dinosaur(Species.TRICERATOPS, 100, "TTGGCCAATT"));
        mismatch.getTag().putBoolean("Gender", true);
        var original = mismatch.getTag().copy();
        check(h, mismatch.useOn(use(h, player, mismatch)) == InteractionResult.FAIL && mismatch.getTag().equals(original),
                "mismatched DNA must be rejected without altering the egg");

        player.getAbilities().instabuild = false;
        ItemStack untagged = new ItemStack(HatcheryRegistry.hatchedEgg(Species.VELOCIRAPTOR));
        DinosaurEntity survival = hatch(h, player, untagged, Species.VELOCIRAPTOR);
        check(h, survival.getDNAQuality() == 0 && untagged.isEmpty(), "a bare survival egg must default to zero quality and be consumed");
        survival.discard();
        h.succeed();
    }
}
