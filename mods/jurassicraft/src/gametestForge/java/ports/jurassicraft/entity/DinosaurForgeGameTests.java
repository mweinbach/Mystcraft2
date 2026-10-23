package ports.jurassicraft.entity;

import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.world.BiomeModifier;
import net.minecraftforge.common.world.ModifiableBiomeInfo;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.config.JurassicConfig;
import ports.jurassicraft.forge.JurassicBiomeModifier;
import ports.jurassicraft.gametest.TestPlatform;

/**
 * Forge-only dinosaur checks: the timing of Forge's cancellable {@code LivingDeathEvent} around
 * carcasses, and re-application of Forge's biome modifier. The gameplay they guard is shared and
 * covered for both loaders by {@link DinosaurGameTests}.
 */
@GameTestHolder(PortMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DinosaurForgeGameTests {
    private DinosaurForgeGameTests() {}

    /**
     * Forge fires {@code LivingDeathEvent} from {@code die()}, which a carcass defers until it is
     * exhausted; Fabric asks {@code ALLOW_DEATH} from {@code hurt()} before {@code die()}, when the
     * carcass forms, so the event counts below are Forge's contract.
     */
    @GameTest(template = "feeder_test", templateNamespace = PortMod.MOD_ID)
    public static void canceledCarcassDeathCannotDuplicateLoot(GameTestHelper helper) {
        DinosaurGameTests.clearPreviousDinosaurs(helper, new BlockPos(2, 1, 2));
        DinosaurEntity dinosaur = DinosaurGameTests.spawnWithCarcassPolicy(helper, new BlockPos(2, 1, 2), true);
        if (dinosaur == null) { helper.fail("could not create dinosaur"); return; }
        try (TestPlatform.DeathWatch listener = TestPlatform.get().watchDeaths(dinosaur)) {
            AABB area = dinosaur.getBoundingBox().inflate(4);
            Set<UUID> existing = DinosaurGameTests.entityIds(helper, ItemEntity.class, area);
            dinosaur.hurt(DamageSource.GENERIC, 1000);
            DinosaurGameTests.require(helper, dinosaur.isCarcass() && listener.calls() == 0,
                    "the death event must wait until the carcass is exhausted");
            CompoundTag state = dinosaur.saveWithoutId(new CompoundTag());
            state.putInt("CarcassHealth", 0);
            dinosaur.load(state);
            dinosaur.invulnerableTime = 0;
            listener.cancel(true);
            dinosaur.hurt(DamageSource.GENERIC, 1);
            DinosaurGameTests.require(helper, listener.calls() == 1 && dinosaur.isAlive() && !dinosaur.isCarcass()
                    && dinosaur.saveWithoutId(new CompoundTag()).getBoolean("CarcassLootExhausted"),
                    "a canceled death must restore a living animal and mark its loot exhausted");
            int drops = DinosaurGameTests.newEntities(helper, ItemEntity.class, area, existing).size();
            dinosaur.invulnerableTime = 0;
            dinosaur.hurt(DamageSource.GENERIC, 1000);
            DinosaurGameTests.require(helper, listener.calls() == 2 && dinosaur.isAlive() && !dinosaur.isCarcass()
                    && DinosaurGameTests.newEntities(helper, ItemEntity.class, area, existing).size() == drops,
                    "repeated canceled deaths must not create new carcasses or drops");
            listener.cancel(false);
            dinosaur.invulnerableTime = 0;
            dinosaur.hurt(DamageSource.GENERIC, 1000);
            DinosaurGameTests.require(helper, listener.calls() == 3 && !dinosaur.isAlive()
                    && DinosaurGameTests.newEntities(helper, ItemEntity.class, area, existing).size() == drops,
                    "a later successful death must not repeat the carcass loot");
            helper.succeed();
        }
    }
    /** Forge may run a biome modifier more than once per biome; Fabric applies BiomeModifications once. */
    @GameTest(template = "feeder_test", templateNamespace = PortMod.MOD_ID)
    public static void biomeModifierDoesNotDuplicateSpawns(GameTestHelper helper) {
        var plains = helper.getLevel().registryAccess().registryOrThrow(Registry.BIOME_REGISTRY).getHolderOrThrow(Biomes.PLAINS);
        var modifier = new JurassicBiomeModifier(helper.getLevel().registryAccess().registryOrThrow(Registry.PLACED_FEATURE_REGISTRY));
        boolean dinosaurs = JurassicConfig.NATURAL_DINOSAUR_SPAWNING.get();
        try {
            JurassicConfig.NATURAL_DINOSAUR_SPAWNING.set(true);
            var enabled = ModifiableBiomeInfo.BiomeInfo.Builder.copyOf(plains.value().modifiableBiomeInfo().getOriginalBiomeInfo());
            modifier.modify(plains, BiomeModifier.Phase.ADD, enabled);
            int enabledCount = enabled.getMobSpawnSettings().getSpawner(MobCategory.CREATURE).size();
            modifier.modify(plains, BiomeModifier.Phase.ADD, enabled);
            DinosaurGameTests.require(helper, enabled.getMobSpawnSettings().getSpawner(MobCategory.CREATURE).size() == enabledCount,
                    "applying the modifier twice must not duplicate entries");
        } finally {
            JurassicConfig.NATURAL_DINOSAUR_SPAWNING.set(dinosaurs);
        }
        helper.succeed();
    }
}
