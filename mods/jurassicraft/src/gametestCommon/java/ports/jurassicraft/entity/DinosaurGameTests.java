package ports.jurassicraft.entity;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import ports.jurassicraft.assembly.AssemblyRegistry;
import ports.jurassicraft.bugs.BugRegistry;
import ports.jurassicraft.config.JurassicConfig;
import ports.jurassicraft.content.Species;
import ports.jurassicraft.gametest.TestPlatform;
import ports.jurassicraft.genetics.DinosaurAttributes;
import ports.jurassicraft.hatching.HatcheryRegistry;
import ports.jurassicraft.plants.PlantRegistry;
import ports.jurassicraft.plants.PlantSpecies;
import ports.jurassicraft.scanner.DinoScannerRegistry;
import ports.jurassicraft.worldgen.WorldgenPlacements;

/** Dinosaur integration checks for the GameTest server. */
public final class DinosaurGameTests {
    private DinosaurGameTests() {}
    static void require(GameTestHelper helper, boolean condition, String message) {
        if (!condition) helper.fail(message);
    }

    static <T extends Entity> Set<UUID> entityIds(GameTestHelper helper, Class<T> type, AABB area) {
        return helper.getLevel().getEntitiesOfClass(type, area).stream().map(Entity::getUUID).collect(Collectors.toSet());
    }

    static <T extends Entity> List<T> newEntities(GameTestHelper helper, Class<T> type, AABB area, Set<UUID> existing) {
        return helper.getLevel().getEntitiesOfClass(type, area, entity -> !existing.contains(entity.getUUID()));
    }

    // Persistent dinosaurs from a previous run can obstruct a spawn or be chosen by scavenging AI.
    static void clearPreviousDinosaurs(GameTestHelper helper, BlockPos relative) {
        BlockPos pos = helper.absolutePos(relative);
        helper.getLevel().getEntitiesOfClass(DinosaurEntity.class, new AABB(pos).inflate(2),
                dinosaur -> dinosaur.blockPosition().distSqr(pos) <= 4).forEach(Entity::discard);
    }

    @GameTest(template = "jurassicraft:vehicle_test")
    public static void spawnEggSelectsGenderAndAgeAndStillConfiguresSpawners(GameTestHelper helper) {
        var level = helper.getLevel();
        var player = helper.makeMockPlayer();
        player.getAbilities().mayBuild = true;
        player.getAbilities().instabuild = false;
        var egg = DinosaurRegistry.spawnEgg(Species.MUSSAURUS).get();
        ItemStack stack = new ItemStack(egg, 4);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        player.setShiftKeyDown(true);
        require(helper, DinosaurSpawnEgg.genderMode(stack) == 0, "spawn eggs start in random mode");
        egg.use(level, player, InteractionHand.MAIN_HAND);
        require(helper, DinosaurSpawnEgg.genderMode(stack) == 1
                && DinosaurSpawnEgg.genderMode(ItemStack.of(stack.save(new CompoundTag()))) == 1,
                "sneak-use must select male and save the mode on the item");
        helper.setBlock(2, 1, 2, Blocks.STONE);
        BlockPos first = helper.absolutePos(new BlockPos(2, 1, 2));
        AABB area = new AABB(first).inflate(10);
        Set<UUID> previous = entityIds(helper, DinosaurEntity.class, area);
        var firstHit = new BlockHitResult(Vec3.atCenterOf(first), Direction.UP, first, false);
        require(helper, egg.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, firstHit)) == InteractionResult.CONSUME,
                "sneak-use on a block must spawn a dinosaur");
        List<DinosaurEntity> created = newEntities(helper, DinosaurEntity.class, area, previous);
        require(helper, created.size() == 1, "one dinosaur should be spawned");
        DinosaurEntity baby = created.get(0);
        require(helper, baby.isBaby() && baby.isMale() && baby.getDNAQuality() == 100 && stack.getCount() == 3,
                "the male mode must produce a male baby with full DNA and consume one egg");

        egg.use(level, player, InteractionHand.MAIN_HAND);
        require(helper, DinosaurSpawnEgg.genderMode(stack) == 2, "the next mode must be female");
        player.setShiftKeyDown(false);
        player.getAbilities().instabuild = true;
        helper.setBlock(5, 1, 2, Blocks.STONE);
        BlockPos second = helper.absolutePos(new BlockPos(5, 1, 2));
        var secondHit = new BlockHitResult(Vec3.atCenterOf(second), Direction.UP, second, false);
        egg.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, secondHit));
        created = newEntities(helper, DinosaurEntity.class, area, previous);
        require(helper, created.size() == 2 && created.stream().anyMatch(dinosaur -> dinosaur != baby
                && !dinosaur.isBaby() && !dinosaur.isMale()) && stack.getCount() == 3,
                "normal use must spawn an adult female without consuming a creative egg");

        player.setShiftKeyDown(true);
        egg.use(level, player, InteractionHand.MAIN_HAND);
        require(helper, DinosaurSpawnEgg.genderMode(stack) == 0, "gender modes must cycle back to random");
        helper.setBlock(8, 1, 2, Blocks.SPAWNER);
        BlockPos spawnerPos = helper.absolutePos(new BlockPos(8, 1, 2));
        var spawnerHit = new BlockHitResult(Vec3.atCenterOf(spawnerPos), Direction.UP, spawnerPos, false);
        player.getAbilities().instabuild = false;
        egg.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, spawnerHit));
        CompoundTag spawner = level.getBlockEntity(spawnerPos).saveWithoutMetadata();
        require(helper, "jurassicraft:mussaurus".equals(spawner.getCompound("SpawnData").getCompound("entity").getString("id"))
                && stack.getCount() == 2 && newEntities(helper, DinosaurEntity.class, area, previous).size() == 2,
                "using an egg on a spawner must configure it without spawning a dinosaur");
        created.forEach(Entity::discard);
        helper.succeed();
    }

    // Forge's config is file-backed and auto-saved. Isolate the death policy per fixture
    // so parallel tests and the config watcher cannot change each other's settings.
    static DinosaurEntity spawnWithCarcassPolicy(GameTestHelper helper, BlockPos relative, boolean allowed) {
        var level = helper.getLevel();
        DinosaurEntity dinosaur = new DinosaurEntity(DinosaurRegistry.type(Species.MUSSAURUS), level) {
            @Override boolean allowsCarcass() { return allowed; }
        };
        BlockPos pos = helper.absolutePos(relative);
        dinosaur.moveTo(pos.getX() + .5, pos.getY(), pos.getZ() + .5, level.random.nextFloat() * 360, 0);
        dinosaur.setBaby(false);
        dinosaur.setMale(false);
        dinosaur.setPersistenceRequired();
        return level.noCollision(dinosaur) && level.addFreshEntity(dinosaur) ? dinosaur : null;
    }

    @GameTest(template = "jurassicraft:feeder_test")
    public static void naturalSpawningIsOptInAndSpeciesSpecific(GameTestHelper helper) {
        var biomes = helper.getLevel().registryAccess().registryOrThrow(Registry.BIOME_REGISTRY);
        var plains = biomes.getHolderOrThrow(Biomes.PLAINS);
        var ocean = biomes.getHolderOrThrow(Biomes.OCEAN);
        var jungle = biomes.getHolderOrThrow(Biomes.JUNGLE);
        boolean dinosaurs = JurassicConfig.NATURAL_DINOSAUR_SPAWNING.get(), goats = JurassicConfig.NATURAL_GOAT_SPAWNING.get();
        try {
            JurassicConfig.NATURAL_GOAT_SPAWNING.set(false);
            JurassicConfig.NATURAL_DINOSAUR_SPAWNING.set(false);
            int originalCount = plains.value().getMobSettings().getMobs(MobCategory.CREATURE).unwrap().size();
            require(helper, TestPlatform.get().naturalSpawns(plains, MobCategory.CREATURE).size() == originalCount,
                    "disabled natural spawning must leave the biome untouched");
            JurassicConfig.NATURAL_DINOSAUR_SPAWNING.set(true);
            var enabled = TestPlatform.get().naturalSpawns(plains, MobCategory.CREATURE);
            var gallimimus = enabled.stream()
                    .filter(spawn -> spawn.type == DinosaurRegistry.type(Species.GALLIMIMUS)).findFirst().orElse(null);
            var tyrannosaurus = enabled.stream()
                    .filter(spawn -> spawn.type == DinosaurRegistry.type(Species.TYRANNOSAURUS)).findFirst().orElse(null);
            require(helper, hasSpawn(gallimimus, 25, 1, 6) && hasSpawn(tyrannosaurus, 5, 1, 1),
                    "plains spawn weights and herd limits must match the original");
            var coelacanth = TestPlatform.get().naturalSpawns(ocean, MobCategory.WATER_CREATURE).stream()
                    .filter(spawn -> spawn.type == DinosaurRegistry.type(Species.COELACANTH)).findFirst().orElse(null);
            require(helper, hasSpawn(coelacanth, 5, 1, 1), "ocean fish use water creature spawning and valid solitary groups");
        } finally {
            JurassicConfig.NATURAL_DINOSAUR_SPAWNING.set(dinosaurs);
            JurassicConfig.NATURAL_GOAT_SPAWNING.set(goats);
        }
        require(helper, jungle.is(WorldgenPlacements.tag("spawns/microraptor")) && !plains.is(WorldgenPlacements.tag("spawns/coelacanth")),
                "biome selector uses the intended tag unions");
        require(helper, SpawnPlacements.getPlacementType(DinosaurRegistry.type(Species.COELACANTH)) == SpawnPlacements.Type.IN_WATER
                && SpawnPlacements.getPlacementType(DinosaurRegistry.type(Species.VELOCIRAPTOR)) == SpawnPlacements.Type.ON_GROUND,
                "land and aquatic species need the correct spawn placements");
        helper.succeed();
    }

    private static boolean hasSpawn(MobSpawnSettings.SpawnerData spawn, int weight, int min, int max) {
        return spawn != null && spawn.getWeight().asInt() == weight && spawn.minCount == min && spawn.maxCount == max;
    }

    @GameTest(template = "jurassicraft:feeder_test")
    public static void carcassHarvestPreservesGeneticsAndDecomposes(GameTestHelper helper) {
        clearPreviousDinosaurs(helper, new BlockPos(2, 1, 2));
        AABB area = new AABB(helper.absolutePos(new BlockPos(2, 1, 2))).inflate(5);
        Set<UUID> existing = entityIds(helper, ItemEntity.class, area);
        try {
            DinosaurEntity dinosaur = spawnWithCarcassPolicy(helper, new BlockPos(2, 1, 2), true);
            if (dinosaur == null) { helper.fail("could not create dinosaur"); return; }
            dinosaur.setGenetics("ACGTACGTAC");
            dinosaur.setDNAQuality(73);
            require(helper, dinosaur.hurt(DamageSource.GENERIC, 1000) && dinosaur.isCarcass()
                    && !dinosaur.isAlive() && dinosaur.isNoAi(), "lethal damage must leave an inert carcass");
            require(helper, newEntities(helper, ItemEntity.class, area, existing).isEmpty(), "death must not drop meat before harvesting");
            CompoundTag saved = dinosaur.saveWithoutId(new CompoundTag());
            int remaining = saved.getInt("CarcassHealth");
            require(helper, remaining >= 1 && remaining <= 10, "carcass health must depend on body size");
            DinosaurEntity loaded = DinosaurRegistry.type(Species.MUSSAURUS).create(helper.getLevel());
            if (loaded == null) { helper.fail("could not restore carcass"); return; }
            loaded.load(saved);
            require(helper, loaded.isCarcass() && !loaded.isAlive() && loaded.isNoAi()
                    && loaded.getGenetics().equals("ACGTACGTAC") && loaded.getDNAQuality() == 73,
                    "carcass state and genetics must survive reload");
            dinosaur.invulnerableTime = 0;
            require(helper, dinosaur.hurt(DamageSource.GENERIC, 1), "a carcass should be harvestable");
            var drops = newEntities(helper, ItemEntity.class, area, existing);
            require(helper, drops.size() == 1 && drops.get(0).getItem().is(DinosaurRegistry.rawMeat(Species.MUSSAURUS).get())
                    && drops.get(0).getItem().getCount() >= 1 && drops.get(0).getItem().getCount() <= 2
                    && drops.get(0).getItem().getTag().getString("Genetics").equals("ACGTACGTAC")
                    && drops.get(0).getItem().getTag().getInt("DNAQuality") == 73,
                    "harvesting must produce species meat with the carcass DNA");
            for (int i = 0; i < remaining; i++) {
                dinosaur.invulnerableTime = 0;
                if (i == remaining - 1) dinosaur.getRandom().setSeed(42);
                dinosaur.hurt(DamageSource.GENERIC, 1);
            }
            require(helper, dinosaur.isRemoved(), "fully harvested carcass must disappear");
            var meat = newEntities(helper, ItemEntity.class, area, existing).stream()
                    .filter(item -> item.getItem().is(DinosaurRegistry.rawMeat(Species.MUSSAURUS).get())).toList();
            require(helper, meat.size() == remaining + 1, "the final death must not add duplicate meat drops");
            var bones = newEntities(helper, ItemEntity.class, area, existing).stream()
                    .map(ItemEntity::getItem).filter(stack -> Species.MUSSAURUS.bones().stream()
                            .anyMatch(bone -> stack.is(AssemblyRegistry.FRESH_BONES.get(Species.MUSSAURUS).get(bone).get()))).toList();
            require(helper, !bones.isEmpty() && bones.stream().allMatch(stack -> stack.getTag() != null
                    && stack.getTag().getString("Genetics").equals("ACGTACGTAC") && stack.getTag().getInt("DNAQuality") == 73),
                    "fresh bones must preserve the carcass DNA");
            helper.succeed();
        } finally {
            newEntities(helper, ItemEntity.class, area, existing).forEach(Entity::discard);
        }
    }

    @GameTest(template = "jurassicraft:feeder_test")
    public static void carcassDecayAndCarnivoreScavenging(GameTestHelper helper) {
        clearPreviousDinosaurs(helper, new BlockPos(1, 1, 2));
        clearPreviousDinosaurs(helper, new BlockPos(3, 1, 2));
        DinosaurEntity body = spawnWithCarcassPolicy(helper, new BlockPos(1, 1, 2), true);
        DinosaurEntity hunter = DinosaurRegistry.spawn(Species.VELOCIRAPTOR, helper.getLevel(),
                helper.absolutePos(new BlockPos(3, 1, 2)), false, true);
        if (body == null || hunter == null) { helper.fail("could not create dinosaurs"); return; }
        body.hurt(DamageSource.GENERIC, 1000);
        CompoundTag state = body.saveWithoutId(new CompoundTag());
        int before = state.getInt("CarcassHealth");
        state.putInt("CarcassDecayTicks", 999);
        body.load(state);
        body.aiStep();
        require(helper, body.saveWithoutId(new CompoundTag()).getInt("CarcassHealth") == before - 1,
                "a loaded carcass must decay after 1000 ticks");
        body.invulnerableTime = 0;
        hunter.reduceEnergy(hunter.maximumEnergy());
        int energy = hunter.energy();
        ScavengeCarcassGoal goal = new ScavengeCarcassGoal(hunter);
        require(helper, goal.canUse(), "a hungry carnivore should seek a nearby carcass");
        goal.start();
        goal.tick();
        require(helper, hunter.energy() > energy && (body.isRemoved()
                || body.saveWithoutId(new CompoundTag()).getInt("CarcassHealth") == before - 2),
                "a scavenger bite must consume meat and exhaust the carcass");
        goal.stop();
        body.discard();
        hunter.discard();
        helper.succeed();
    }

    @GameTest(template = "jurassicraft:feeder_test")
    public static void carcassRespectsMobLootAndConfig(GameTestHelper helper) {
        var rule = helper.getLevel().getGameRules().getRule(GameRules.RULE_DOMOBLOOT);
        boolean previousLoot = rule.get();
        clearPreviousDinosaurs(helper, new BlockPos(1, 1, 1));
        clearPreviousDinosaurs(helper, new BlockPos(3, 1, 3));
        AABB area = new AABB(helper.absolutePos(new BlockPos(1, 1, 1))).inflate(4);
        Set<UUID> existing = entityIds(helper, ItemEntity.class, area);
        try {
            DinosaurEntity configured = DinosaurRegistry.type(Species.MUSSAURUS).create(helper.getLevel());
            require(helper, configured != null && configured.allowsCarcass() == JurassicConfig.ALLOW_CARCASS.get(),
                    "ordinary dinosaurs must read the configured carcass policy");
            rule.set(false, helper.getLevel().getServer());
            DinosaurEntity dinosaur = spawnWithCarcassPolicy(helper, new BlockPos(1, 1, 1), true);
            if (dinosaur == null) { helper.fail("could not create dinosaur"); return; }
            dinosaur.hurt(DamageSource.GENERIC, 1000);
            require(helper, dinosaur.isCarcass(), "the carcass should form even without mob loot");
            int remaining = dinosaur.saveWithoutId(new CompoundTag()).getInt("CarcassHealth");
            for (int i = 0; i <= remaining; i++) {
                dinosaur.invulnerableTime = 0;
                dinosaur.hurt(DamageSource.GENERIC, 1);
            }
            require(helper, dinosaur.isRemoved() && newEntities(helper, ItemEntity.class, area, existing).isEmpty(),
                    "doMobLoot=false must suppress all carcass meat and bones");
            DinosaurEntity normal = spawnWithCarcassPolicy(helper, new BlockPos(3, 1, 3), false);
            if (normal == null) { helper.fail("could not create second dinosaur"); return; }
            boolean hurt = normal.hurt(DamageSource.GENERIC, 1000);
            require(helper, !normal.isCarcass() && !normal.isAlive(), "disabling carcasses must use normal death"
                    + " (hurt=" + hurt + ", health=" + normal.getHealth() + ", alive=" + normal.isAlive()
                    + ", carcass=" + normal.isCarcass() + ", removed=" + normal.isRemoved()
                    + ", allowed=" + normal.allowsCarcass() + ", invulnerable=" + normal.isInvulnerable()
                    + ", invulnerableTime=" + normal.invulnerableTime + ")");
            helper.succeed();
        } finally {
            rule.set(previousLoot, helper.getLevel().getServer());
        }
    }


    @GameTest(template = "jurassicraft:feeder_test")
    public static void naturalSpawnGroupsAreAdults(GameTestHelper helper) {
        DinosaurEntity dinosaur = DinosaurRegistry.type(Species.MICRORAPTOR).create(helper.getLevel());
        if (dinosaur == null) { helper.fail("could not create dinosaur"); return; }
        var group = new AgeableMob.AgeableMobGroupData(1.0f);
        group.increaseGroupSizeByOne();
        dinosaur.setBaby(true);
        BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 2));
        dinosaur.finalizeSpawn(helper.getLevel(), helper.getLevel().getCurrentDifficultyAt(pos), MobSpawnType.NATURAL, group, null);
        require(helper, !dinosaur.isBaby() && dinosaur.growth() == 1, "wild spawn group members must start fully grown");
        helper.succeed();
    }

    @GameTest(template = "jurassicraft:feeder_test")
    public static void aquaticSpawnAllowsWaterButNotSolidBlocks(GameTestHelper helper) {
        DinosaurEntity fish = DinosaurRegistry.type(Species.COELACANTH).create(helper.getLevel());
        DinosaurEntity land = DinosaurRegistry.type(Species.MICRORAPTOR).create(helper.getLevel());
        if (fish == null || land == null) { helper.fail("could not create dinosaurs"); return; }
        BlockPos relative = new BlockPos(2, 1, 2);
        BlockPos pos = helper.absolutePos(relative);
        helper.setBlock(relative, Blocks.WATER);
        fish.moveTo(pos.getX() + .5, pos.getY(), pos.getZ() + .5, 0, 0);
        land.moveTo(pos.getX() + .5, pos.getY(), pos.getZ() + .5, 0, 0);
        require(helper, fish.checkSpawnObstruction(helper.getLevel()), "fish must be able to spawn in water");
        require(helper, !land.checkSpawnObstruction(helper.getLevel()), "land dinosaurs must reject water in their spawn volume");
        helper.setBlock(relative, Blocks.STONE);
        require(helper, !NaturalSpawner.isSpawnPositionOk(SpawnPlacements.Type.IN_WATER, helper.getLevel(), pos, fish.getType()),
                "fish must not spawn in a solid block");
        helper.succeed();
    }
    @GameTest(template = "jurassicraft:feeder_test")
    public static void babyGrowthAndGeneticsPersist(GameTestHelper helper) {
        clearPreviousDinosaurs(helper, new BlockPos(2, 1, 2));
        var dinosaur = DinosaurRegistry.spawn(Species.VELOCIRAPTOR, helper.getLevel(),
                helper.absolutePos(new BlockPos(2, 1, 2)), true, true);
        if (dinosaur == null) { helper.fail("hatchling was not spawned"); return; }
        dinosaur.setGenetics("ACGTACGTAC");
        dinosaur.setDNAQuality(73);
        float babyWidth = dinosaur.getBbWidth();
        CompoundTag saved = dinosaur.saveWithoutId(new CompoundTag());
        DinosaurEntity loaded = DinosaurRegistry.type(Species.VELOCIRAPTOR).create(helper.getLevel());
        if (loaded == null) { helper.fail("unable to construct restored dinosaur"); return; }
        loaded.load(saved);
        require(helper, loaded.species() == Species.VELOCIRAPTOR && loaded.isBaby() && loaded.isMale(), "species, age and sex must persist");
        require(helper, loaded.getGenetics().equals("ACGTACGTAC") && loaded.getDNAQuality() == 73, "DNA must persist");
        require(helper, Math.abs(loaded.getBbWidth() - babyWidth) < .001f, "baby dimensions must persist");
        loaded.setBaby(false);
        require(helper, loaded.getBbWidth() > babyWidth && loaded.growth() == 1, "adult must be larger than hatchling");
        dinosaur.discard();
        helper.succeed();
    }
    @GameTest(template = "jurassicraft:feeder_test")
    public static void inheritedAttributesApplyAndPersist(GameTestHelper helper) {
        var animal = DinosaurRegistry.type(Species.MUSSAURUS).create(helper.getLevel());
        var loaded = DinosaurRegistry.type(Species.MUSSAURUS).create(helper.getLevel());
        if (animal == null || loaded == null) { helper.fail("could not create animals"); return; }
        animal.setBaby(false);
        var traits = new DinosaurAttributes(1.2f, 1.1f, .9f, .8f);
        animal.setGeneticAttributes(traits);
        DinosaurProfile profile = DinosaurProfile.of(Species.MUSSAURUS);
        require(helper, Math.abs(animal.getBbWidth() - profile.adultWidth() * traits.scale()) < .001
                && Math.abs(animal.getMaxHealth() - profile.adultHealth() * traits.health()) < .001
                && Math.abs(animal.getAttribute(Attributes.ATTACK_DAMAGE).getBaseValue() - profile.adultDamage() * traits.damage()) < .001
                && Math.abs(animal.getAttribute(Attributes.MOVEMENT_SPEED).getBaseValue() - profile.speed() * traits.speed()) < .001,
                "scale, health, damage, and speed must use their inherited multipliers");
        CompoundTag data = animal.saveWithoutId(new CompoundTag());
        loaded.load(data);
        require(helper, loaded.geneticAttributes().equals(traits) && Math.abs(loaded.geneticScale() - traits.scale()) < .001
                && Math.abs(loaded.getBbWidth() - animal.getBbWidth()) < .001,
                "inherited traits and synchronized scale must survive reload");
        data.remove("GeneticAttributes");
        loaded.load(data);
        require(helper, loaded.geneticAttributes().equals(new DinosaurAttributes(1, 1, 1, 1)),
                "existing saves without traits must retain their original, unmodified stats");
        helper.succeed();
    }

    @GameTest(template = "jurassicraft:feeder_test")
    public static void loadedHealthRespectsRecalculatedMaximum(GameTestHelper helper) {
        var animal = DinosaurRegistry.type(Species.TRICERATOPS).create(helper.getLevel());
        var loaded = DinosaurRegistry.type(Species.TRICERATOPS).create(helper.getLevel());
        if (animal == null || loaded == null) { helper.fail("could not create dinosaurs"); return; }
        var traits = new DinosaurAttributes(1, 1, 1.2f, 1);
        double baseHealth = DinosaurProfile.of(Species.TRICERATOPS).adultHealth();
        animal.setGeneticAttributes(traits);
        animal.setHealth(animal.getMaxHealth());
        CompoundTag legacy = animal.saveWithoutId(new CompoundTag());
        legacy.remove("GeneticAttributes");
        loaded.load(legacy);
        require(helper, Math.abs(loaded.getMaxHealth() - baseHealth) < .001f
                        && Math.abs(loaded.getHealth() - baseHealth) < .001f,
                "legacy health must be clamped after restoring the default maximum");

        animal.setHealth(10);
        CompoundTag injured = animal.saveWithoutId(new CompoundTag());
        loaded.load(injured);
        require(helper, Math.abs(loaded.getMaxHealth() - baseHealth * traits.health()) < .001f
                        && loaded.getHealth() == 10,
                "valid injured health and inherited maximum must survive loading");
        injured.remove("GeneticAttributes");
        loaded.load(injured);
        require(helper, loaded.getHealth() == 10 && Math.abs(loaded.getMaxHealth() - baseHealth) < .001f,
                "lowering the maximum must not heal an injured dinosaur");

        loaded.setGeneticAttributes(traits);
        loaded.setHealth(loaded.getMaxHealth());
        CompoundTag sparseCarcass = new CompoundTag();
        sparseCarcass.putBoolean("IsCarcass", true);
        loaded.readAdditionalSaveData(sparseCarcass);
        require(helper, loaded.isCarcass() && Math.abs(loaded.getMaxHealth() - baseHealth) < .001f
                        && Math.abs(loaded.getHealth() - baseHealth) < .001f,
                "sparse summon data must not retain health above the restored maximum");
        helper.succeed();
    }

    @GameTest(template = "jurassicraft:feeder_test")
    public static void growthTimingAndTotalAgeMigrateAndPersist(GameTestHelper helper) {
        var animal = new DinosaurEntity(DinosaurRegistry.type(Species.MUSSAURUS), helper.getLevel()) {
            @Override boolean growthEnabled() { return true; }
        };
        int maturity = DinosaurProfile.of(Species.MUSSAURUS).growthTicks();
        require(helper, maturity == 30 * 24000 && animal.totalAgeTicks() == maturity,
                "wild adults start at the original thirty-day maturity");
        animal.setBaby(true);
        require(helper, animal.getAge() == -maturity && animal.totalAgeTicks() == 0, "a hatchling starts at age zero");
        CompoundTag legacy = animal.saveWithoutId(new CompoundTag());
        legacy.remove("GrowthScaleVersion");
        legacy.remove("TotalAgeTicks");
        legacy.putInt("Age", -maturity / 16);
        animal.load(legacy);
        require(helper, animal.getAge() == -maturity / 2 && animal.growth() == .5f
                && animal.totalAgeTicks() == maturity / 2,
                "old juvenile saves must keep their growth fraction under the corrected timing");
        animal.aiStep();
        require(helper, animal.getAge() == -maturity / 2 + 1 && animal.totalAgeTicks() == maturity / 2 + 1,
                "age must advance by one loaded game tick");
        CompoundTag migrated = animal.saveWithoutId(new CompoundTag());
        animal.load(migrated);
        require(helper, animal.getAge() == -maturity / 2 + 1 && animal.totalAgeTicks() == maturity / 2 + 1,
                "migrated age must persist without being rescaled twice");
        animal.setBaby(false);
        animal.aiStep();
        require(helper, animal.getAge() == 0 && animal.totalAgeTicks() == maturity + 1,
                "adult age must continue after vanilla growth stops");
        helper.succeed();
    }

    @GameTest(template = "jurassicraft:vehicle_test")
    public static void inheritedTraitsSurviveEggPickupAndHatching(GameTestHelper helper) {
        var level = helper.getLevel();
        var egg = DinosaurRegistry.EGG.get().create(level);
        if (egg == null) { helper.fail("could not create egg"); return; }
        var traits = new DinosaurAttributes(1.15f, .9f, 1.1f, .95f);
        egg.setEmbryo(Species.MUSSAURUS, "ACGTACGTAC", 80, true, traits);
        CompoundTag saved = egg.saveWithoutId(new CompoundTag());
        var restored = DinosaurRegistry.EGG.get().create(level);
        if (restored == null) { helper.fail("could not restore egg"); return; }
        restored.load(saved);
        var player = helper.makeMockPlayer();
        player.getAbilities().mayBuild = true;
        restored.interact(player, InteractionHand.MAIN_HAND);
        ItemStack pickedUp = player.getInventory().items.stream().filter(stack -> stack.is(HatcheryRegistry.hatchedEgg(Species.MUSSAURUS)))
                .findFirst().orElse(ItemStack.EMPTY);
        require(helper, !pickedUp.isEmpty() && DinosaurAttributes.read(pickedUp.getTag().getCompound("GeneticAttributes")).filter(traits::equals).isPresent(),
                "picking up a reloaded egg must retain the embryo's traits");
        BlockPos floorRelative = new BlockPos(3, 1, 3);
        clearPreviousDinosaurs(helper, floorRelative.above());
        helper.setBlock(floorRelative, Blocks.STONE);
        BlockPos floor = helper.absolutePos(floorRelative);
        AABB area = new AABB(floor.above()).inflate(3);
        Set<UUID> previous = entityIds(helper, DinosaurEntity.class, area);
        player.setItemInHand(InteractionHand.MAIN_HAND, pickedUp);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(floor), Direction.UP, floor, false);
        require(helper, pickedUp.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit)).consumesAction(),
                "a picked-up egg must hatch");
        var babies = newEntities(helper, DinosaurEntity.class, area, previous);
        require(helper, babies.size() == 1 && babies.get(0).isBaby() && babies.get(0).geneticAttributes().equals(traits)
                && babies.get(0).getGenetics().equals("ACGTACGTAC") && babies.get(0).isMale(),
                "hatching must apply the saved traits, DNA, and sex");
        babies.forEach(Entity::discard);
        helper.succeed();
    }
    @GameTest(template = "jurassicraft:feeder_test")
    public static void feedingRespectsDietAndPersists(GameTestHelper helper) {
        DinosaurEntity dino = DinosaurRegistry.type(Species.TRICERATOPS).create(helper.getLevel());
        if (dino == null) { helper.fail("unable to construct dinosaur"); return; }
        CompoundTag state = new CompoundTag();
        dino.addAdditionalSaveData(state);
        state.putInt("Energy", 0);
        state.putInt("DigestingFood", 0);
        dino.readAdditionalSaveData(state);
        require(helper, dino.needsFood() && !dino.feed(new ItemStack(Items.BEEF)), "herbivore must reject meat");
        ItemStack carrots = new ItemStack(Items.CARROT, 2);
        require(helper, dino.feed(carrots) && carrots.getCount() == 2, "feeding must succeed without mutating caller inventory");
        CompoundTag saved = new CompoundTag();
        dino.addAdditionalSaveData(saved);
        require(helper, saved.getInt("DigestingFood") > 0 && saved.getInt("Energy") > 0, "feeding must be saved");
        DinosaurEntity fish = DinosaurRegistry.type(Species.COELACANTH).create(helper.getLevel());
        if (fish == null) { helper.fail("unable to construct coelacanth"); return; }
        require(helper, fish.canBreatheUnderwater() && fish.isFood(new ItemStack(Items.COD)), "coelacanth must breathe water and eat fish");
        require(helper, fish.isFood(new ItemStack(BugRegistry.KRILL.get())), "coelacanth must eat filter food");
        DinosaurEntity raptor = DinosaurRegistry.type(Species.VELOCIRAPTOR).create(helper.getLevel());
        if (raptor == null) { helper.fail("unable to construct raptor"); return; }
        raptor.setBaby(true);
        require(helper, raptor.isFood(new ItemStack(BugRegistry.CRICKETS.get())), "young carnivores eat insects");
        raptor.setBaby(false);
        require(helper, !raptor.isFood(new ItemStack(BugRegistry.CRICKETS.get())), "adult carnivores do not eat insects");
        helper.succeed();
    }

    @GameTest(template = "jurassicraft:feeder_test")
    public static void speciesDietsDistinguishFishMeatInsectsAndFilterFood(GameTestHelper helper) {
        var fishFoods = new net.minecraft.world.item.Item[] {Items.COD, Items.COOKED_COD, Items.SALMON,
                Items.COOKED_SALMON, Items.TROPICAL_FISH, Items.PUFFERFISH};
        for (Species species : Species.values()) {
            DinosaurEntity animal = DinosaurRegistry.type(species).create(helper.getLevel());
            if (animal == null) { helper.fail("could not create " + species.id()); return; }
            animal.setBaby(false);
            boolean aquatic = species == Species.COELACANTH;
            boolean carnivore = DinosaurProfile.of(species).carnivore();
            for (var fish : fishFoods) {
                require(helper, animal.isFood(new ItemStack(fish)) == aquatic,
                        species.id() + " must classify raw and cooked fish separately from meat and plants: " + fish);
            }
            require(helper, animal.isFood(new ItemStack(Items.BEEF)) == carnivore
                    && animal.isFood(new ItemStack(Items.CARROT)) == (!aquatic && !carnivore),
                    species.id() + " must accept the appropriate meat or plant diet");
            require(helper, animal.isFood(new ItemStack(BugRegistry.KRILL.get())) == aquatic
                    && animal.isFood(new ItemStack(BugRegistry.PLANKTON.get())) == aquatic,
                    species.id() + " must accept filter food only when aquatic");
            require(helper, animal.isFood(new ItemStack(BugRegistry.CRICKETS.get())) == (species == Species.MICRORAPTOR),
                    species.id() + " must use the adult insect diet");
            animal.setBaby(true);
            require(helper, animal.isFood(new ItemStack(BugRegistry.CRICKETS.get()))
                            == (species == Species.MICRORAPTOR || carnivore || species == Species.GALLIMIMUS),
                    species.id() + " must use the infant insect diet");
        }
        helper.succeed();
    }

    @GameTest(template = "jurassicraft:feeder_test")
    public static void hungryDinosaursSeekAndEatDroppedFood(GameTestHelper helper) {
        DinosaurEntity dinosaur = DinosaurRegistry.type(Species.TRICERATOPS).create(helper.getLevel());
        if (dinosaur == null) { helper.fail("unable to construct dinosaur"); return; }
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        dinosaur.moveTo(pos.getX() + .5, pos.getY(), pos.getZ() + .5, 0, 0);
        // Clear food left at this fixture by older test runs before selecting the nearest item.
        helper.getLevel().getEntitiesOfClass(ItemEntity.class, dinosaur.getBoundingBox().inflate(1),
                item -> item.getItem().is(Items.CARROT) || item.getItem().is(Items.BEEF)).forEach(Entity::discard);
        var food = new ItemEntity(helper.getLevel(), dinosaur.getX(), dinosaur.getY(), dinosaur.getZ(), new ItemStack(Items.CARROT, 3));
        var meat = new ItemEntity(helper.getLevel(), dinosaur.getX(), dinosaur.getY(), dinosaur.getZ(), new ItemStack(Items.BEEF, 2));
        helper.getLevel().addFreshEntity(food);
        helper.getLevel().addFreshEntity(meat);
        try {
            var goal = dinosaur.goalSelector.getAvailableGoals().stream().map(wrapped -> wrapped.getGoal())
                    .filter(FindDroppedFoodGoal.class::isInstance).map(FindDroppedFoodGoal.class::cast).findFirst().orElse(null);
            require(helper, goal != null && !goal.canUse(), "a dinosaur that is not hungry must ignore dropped food");
            dinosaur.reduceEnergy(dinosaur.maximumEnergy());
            dinosaur.setHealth(dinosaur.getMaxHealth() - 5);
            require(helper, goal.canUse(), "a hungry dinosaur should find suitable dropped food");
            int before = dinosaur.energy();
            goal.start();
            goal.tick();
            require(helper, food.getItem().getCount() == 2 && meat.getItem().getCount() == 2
                    && dinosaur.energy() > before && dinosaur.getHealth() == dinosaur.getMaxHealth(),
                    "a herbivore should eat one plant item, heal, and ignore meat");
            dinosaur.setNaturalSleep(true);
            require(helper, !goal.canContinueToUse() && !goal.canUse(), "sleeping animals cannot eat");
            dinosaur.setNaturalSleep(false);
            food.setItem(new ItemStack(Items.CARROT));
            goal.tick();
            require(helper, food.isRemoved() && meat.getItem().getCount() == 2,
                    "the final item should be consumed without touching inedible items");
            goal.stop();
            helper.succeed();
        } finally {
            food.discard();
            meat.discard();
        }
    }

    @GameTest(template = "jurassicraft:feeder_test")
    public static void ownerInventoryIsSizedAuthorizedAndPersistent(GameTestHelper helper) {
        require(helper, DinosaurInventory.capacity(Species.MICRORAPTOR) == 9
                && DinosaurInventory.capacity(Species.VELOCIRAPTOR) == 27
                && DinosaurInventory.capacity(Species.TRICERATOPS) == 36
                && DinosaurInventory.capacity(Species.BRACHIOSAURUS) == 54,
                "species must use their original chest capacities");
        DinosaurEntity dinosaur = DinosaurRegistry.type(Species.VELOCIRAPTOR).create(helper.getLevel());
        DinosaurEntity restored = DinosaurRegistry.type(Species.VELOCIRAPTOR).create(helper.getLevel());
        if (dinosaur == null || restored == null) { helper.fail("could not create dinosaurs"); return; }
        var owner = helper.makeMockPlayer();
        var stranger = helper.makeMockPlayer();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        dinosaur.moveTo(pos.getX() + .5, pos.getY(), pos.getZ() + .5, 0, 0);
        owner.moveTo(pos.getX() + 1.5, pos.getY(), pos.getZ() + .5, 0, 0);
        stranger.moveTo(owner.getX(), owner.getY(), owner.getZ(), 0, 0);
        dinosaur.setOwner(owner.getUUID());
        var inventory = dinosaur.inventory();
        var menu = inventory.menu(0, owner.getInventory());
        require(helper, menu.getRowCount() == 3 && menu.stillValid(owner) && !menu.stillValid(stranger),
                "only the owner may access the correctly sized chest");
        dinosaur.setBaby(true);
        require(helper, !menu.stillValid(owner), "young dinosaurs cannot carry items");
        CompoundTag age = dinosaur.saveWithoutId(new CompoundTag());
        int growthTicks = DinosaurProfile.of(Species.VELOCIRAPTOR).growthTicks();
        age.putInt("Age", -growthTicks / 4);
        dinosaur.load(age);
        require(helper, !menu.stillValid(owner), "dinosaurs exactly 75 percent grown are still too young");
        age.putInt("Age", -growthTicks / 4 + growthTicks / 100);
        dinosaur.load(age);
        require(helper, menu.stillValid(owner), "dinosaurs over 75 percent grown may carry items");
        owner.moveTo(pos.getX() + 10.5, pos.getY(), pos.getZ() + .5, 0, 0);
        require(helper, !menu.stillValid(owner), "inventory access must end when the owner moves away");
        owner.moveTo(pos.getX() + 1.5, pos.getY(), pos.getZ() + .5, 0, 0);
        dinosaur.setOwner(stranger.getUUID());
        require(helper, !menu.stillValid(owner), "a changed owner invalidates an open chest");
        dinosaur.setOwner(owner.getUUID());
        owner.getInventory().setItem(0, new ItemStack(Items.BRICK, 4));
        menu.quickMoveStack(owner, inventory.getContainerSize() + 27);
        require(helper, inventory.getItem(0).is(Items.BRICK) && inventory.getItem(0).getCount() == 4
                && owner.getInventory().getItem(0).isEmpty(), "the vanilla chest menu must transfer items");
        inventory.setItem(0, ItemStack.EMPTY);
        ItemStack first = new ItemStack(Items.DIAMOND, 3);
        first.getOrCreateTag().putString("Fixture", "preserve item data");
        inventory.setItem(3, first.copy());
        inventory.setItem(26, new ItemStack(Items.EMERALD, 7));
        CompoundTag saved = dinosaur.saveWithoutId(new CompoundTag());
        restored.load(saved);
        require(helper, restored.inventory().getItem(0).isEmpty()
                && ItemStack.isSameItemSameTags(restored.inventory().getItem(3), first)
                && restored.inventory().getItem(3).getCount() == 3
                && restored.inventory().getItem(26).is(Items.EMERALD)
                && restored.inventory().getItem(26).getCount() == 7,
                "save and reload must retain slot positions, quantities, and item metadata");
        restored.inventory().setItem(0, new ItemStack(Items.STICK));
        restored.load(saved);
        require(helper, restored.inventory().getItem(0).isEmpty(), "loading must clear old inventory contents");
        helper.succeed();
    }

    @GameTest(template = "jurassicraft:feeder_test")
    public static void ownerInventoryDropsOnceAndRespectsCanceledDeaths(GameTestHelper helper) {
        clearPreviousDinosaurs(helper, new BlockPos(1, 1, 1));
        clearPreviousDinosaurs(helper, new BlockPos(3, 1, 3));
        AABB area = new AABB(helper.absolutePos(new BlockPos(2, 1, 2))).inflate(5);
        Set<UUID> existing = entityIds(helper, ItemEntity.class, area);
        DinosaurEntity body = spawnWithCarcassPolicy(helper, new BlockPos(1, 1, 1), true);
        DinosaurEntity normal = spawnWithCarcassPolicy(helper, new BlockPos(3, 1, 3), false);
        if (body == null || normal == null) { helper.fail("could not create dinosaurs"); return; }
        try (TestPlatform.DeathWatch listener = TestPlatform.get().watchDeaths(normal)) {
            ItemStack cargo = new ItemStack(Items.NETHER_STAR, 2);
            cargo.getOrCreateTag().putString("Fixture", "cargo metadata");
            body.inventory().setItem(2, cargo.copy());
            normal.inventory().setItem(1, cargo.copy());
            body.hurt(DamageSource.GENERIC, 1000);
            var drops = newEntities(helper, ItemEntity.class, area, existing).stream()
                    .filter(item -> item.getItem().is(Items.NETHER_STAR)).toList();
            require(helper, body.isCarcass() && body.inventory().isEmpty() && drops.size() == 1
                    && drops.get(0).getItem().getCount() == 2 && ItemStack.isSameItemSameTags(drops.get(0).getItem(), cargo),
                    "becoming a carcass must drop all cargo once with its metadata");
            listener.cancel(true);
            normal.hurt(DamageSource.GENERIC, 1000);
            require(helper, !normal.inventory().isEmpty() && newEntities(helper, ItemEntity.class, area, existing).stream()
                    .filter(item -> item.getItem().is(Items.NETHER_STAR)).count() == 1,
                    "a canceled ordinary death must leave cargo on the dinosaur");
            normal.setHealth(normal.getMaxHealth());
            normal.invulnerableTime = 0;
            listener.cancel(false);
            normal.hurt(DamageSource.GENERIC, 1000);
            drops = newEntities(helper, ItemEntity.class, area, existing).stream()
                    .filter(item -> item.getItem().is(Items.NETHER_STAR)).toList();
            require(helper, normal.inventory().isEmpty() && drops.size() == 2
                    && drops.stream().allMatch(item -> item.getItem().getCount() == 2
                    && ItemStack.isSameItemSameTags(item.getItem(), cargo)),
                    "a later ordinary death must drop cargo exactly once");
            body.invulnerableTime = 0;
            body.hurt(DamageSource.GENERIC, 1);
            require(helper, newEntities(helper, ItemEntity.class, area, existing).stream()
                    .filter(item -> item.getItem().is(Items.NETHER_STAR)).count() == 2,
                    "harvesting a carcass must not duplicate cargo");
            helper.succeed();
        } finally {
            body.discard();
            normal.discard();
            newEntities(helper, ItemEntity.class, area, existing).forEach(Entity::discard);
        }
    }

    @GameTest(template = "jurassicraft:feeder_test")
    public static void dilophosaurusWaitsBeforeSpitting(GameTestHelper helper) {
        DinosaurEntity spitter = DinosaurRegistry.type(Species.DILOPHOSAURUS).create(helper.getLevel());
        var target = net.minecraft.world.entity.EntityType.ZOMBIE.create(helper.getLevel());
        if (spitter == null || target == null) { helper.fail("could not create test animals"); return; }
        for (int x = 1; x <= 4; x++) for (int y = 1; y <= 3; y++) helper.setBlock(new BlockPos(x, y, 1), Blocks.AIR);
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        spitter.moveTo(pos.getX() + .5, pos.getY(), pos.getZ() + .5, 0, 0);
        target.moveTo(pos.getX() + 3.5, pos.getY(), pos.getZ() + .5, 0, 0);
        spitter.setTarget(target);
        AABB area = new AABB(pos).inflate(8);
        Set<UUID> existing = entityIds(helper, VenomEntity.class, area);
        DilophosaurusSpitGoal goal = new DilophosaurusSpitGoal(spitter);
        try {
            require(helper, goal.canUse(), "an unblinded target should trigger the spitting goal");
            goal.start();
            for (int i = 0; i < 40; i++) goal.tick();
            require(helper, spitter.combatAnimation() == DinosaurEntity.CombatAnimation.SPIT
                    && newEntities(helper, VenomEntity.class, area, existing).isEmpty(),
                    "after 40 ticks the dinosaur must start its spit windup");
            for (int i = 0; i < 19; i++) goal.tick();
            require(helper, newEntities(helper, VenomEntity.class, area, existing).isEmpty(), "the spit has a 20-tick windup");
            goal.tick();
            var shots = newEntities(helper, VenomEntity.class, area, existing);
            require(helper, shots.size() == 1 && shots.get(0).getOwner() == spitter
                    && shots.get(0).getDeltaMovement().length() > 1.49,
                    "the windup must launch a correctly attributed venom projectile");
            helper.succeed();
        } finally {
            goal.stop();
            newEntities(helper, VenomEntity.class, area, existing).forEach(Entity::discard);
        }
    }

    @GameTest(template = "jurassicraft:feeder_test")
    public static void dilophosaurusSpitAndBiteHaveOriginalEffects(GameTestHelper helper) {
        DinosaurEntity spitter = DinosaurRegistry.type(Species.DILOPHOSAURUS).create(helper.getLevel());
        DinosaurEntity ally = DinosaurRegistry.type(Species.DILOPHOSAURUS).create(helper.getLevel());
        // Undead reject poison in vanilla; a villager can survive both the spit and bite.
        var target = net.minecraft.world.entity.EntityType.VILLAGER.create(helper.getLevel());
        if (spitter == null || ally == null || target == null) { helper.fail("could not create test animals"); return; }
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        spitter.moveTo(pos.getX() + .5, pos.getY(), pos.getZ() + .5, 0, 0);
        target.moveTo(pos.getX() + 4.5, pos.getY(), pos.getZ() + .5, 0, 0);
        ally.moveTo(pos.getX() + 2.5, pos.getY(), pos.getZ() + .5, 0, 0);
        spitter.setTarget(target);
        VenomEntity venom = new VenomEntity(helper.getLevel(), spitter);
        require(helper, !venom.canHitEntity(ally), "venom must pass through another untargeted dilophosaurus");
        float health = target.getHealth();
        venom.onHitEntity(new net.minecraft.world.phys.EntityHitResult(target));
        var blindness = target.getEffect(net.minecraft.world.effect.MobEffects.BLINDNESS);
        var nausea = target.getEffect(net.minecraft.world.effect.MobEffects.CONFUSION);
        require(helper, venom.isRemoved() && target.getHealth() < health && blindness != null && nausea != null
                && blindness.getDuration() == 300 && nausea.getDuration() == 300
                && blindness.getAmplifier() == 1 && nausea.getAmplifier() == 1,
                "venom must hurt, blind, and disorient its target for 300 ticks");
        require(helper, DilophosaurusSpitGoal.shouldBite(target), "a wounded and blinded victim should be bitten");
        target.invulnerableTime = 0;
        require(helper, spitter.doHurtTarget(target) && target.hasEffect(net.minecraft.world.effect.MobEffects.POISON)
                && target.getEffect(net.minecraft.world.effect.MobEffects.POISON).getDuration() == 300
                && target.getEffect(net.minecraft.world.effect.MobEffects.POISON).getAmplifier() == 1,
                "dilophosaurus bites must apply the original poison");
        helper.succeed();
    }

    @GameTest(template = "jurassicraft:feeder_test")
    public static void raptorLeapPreparesLaunchesAndHitsOnLanding(GameTestHelper helper) {
        DinosaurEntity raptor = DinosaurRegistry.type(Species.VELOCIRAPTOR).create(helper.getLevel());
        var target = net.minecraft.world.entity.EntityType.ZOMBIE.create(helper.getLevel());
        if (raptor == null || target == null) { helper.fail("could not create test animals"); return; }
        for (int x = 1; x <= 4; x++) for (int y = 1; y <= 3; y++) for (int z = 1; z <= 4; z++)
            helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        raptor.moveTo(pos.getX() + .1, pos.getY(), pos.getZ() + .1, 0, 0);
        target.moveTo(pos.getX() + 3.8, pos.getY(), pos.getZ() + 3.8, 0, 0);
        raptor.setOnGround(true);
        raptor.setTarget(target);
        DinosaurLeapGoal leap = new DinosaurLeapGoal(raptor);
        require(helper, leap.canUse(), "a grounded raptor should prepare to leap at a target 5 to 6 blocks away");
        leap.start();
        for (int i = 0; i < 14; i++) leap.tick();
        require(helper, raptor.combatAnimation() == DinosaurEntity.CombatAnimation.PREPARE_LEAP,
                "the raptor must prepare before launching");
        leap.tick();
        require(helper, raptor.combatAnimation() == DinosaurEntity.CombatAnimation.LEAP
                && raptor.getDeltaMovement().x > 0 && raptor.getDeltaMovement().y >= .6,
                "the leap must launch toward its target");
        raptor.setDeltaMovement(0, -.2, 0);
        leap.tick();
        require(helper, raptor.combatAnimation() == DinosaurEntity.CombatAnimation.LEAP_LAND
                && !raptor.causeFallDamage(20, 1, DamageSource.FALL), "the landing pose prevents fall damage");
        raptor.moveTo(target.getX() - 1, target.getY(), target.getZ(), 0, 0);
        raptor.setOnGround(true);
        float health = target.getHealth();
        leap.tick();
        require(helper, target.getHealth() < health && !leap.canContinueToUse(), "landing near the victim deals one hit");
        leap.stop();
        require(helper, raptor.combatAnimation() == DinosaurEntity.CombatAnimation.NONE, "ending the leap clears its animation");
        helper.succeed();
    }

    @GameTest(template = "jurassicraft:feeder_test")
    public static void ownerOrdersAreAuthorizedAndPersist(GameTestHelper helper) {
        DinosaurEntity dinosaur = DinosaurRegistry.type(Species.VELOCIRAPTOR).create(helper.getLevel());
        DinosaurEntity large = DinosaurRegistry.type(Species.BRACHIOSAURUS).create(helper.getLevel());
        if (dinosaur == null || large == null) { helper.fail("could not create dinosaurs"); return; }
        var owner = helper.makeMockPlayer();
        var stranger = helper.makeMockPlayer();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        dinosaur.moveTo(pos.getX() + .5, pos.getY(), pos.getZ() + .5, 0, 0);
        owner.moveTo(pos.getX() + 1.5, pos.getY(), pos.getZ() + .5, 0, 0);
        stranger.moveTo(owner.getX(), owner.getY(), owner.getZ(), 0, 0);
        dinosaur.setOwner(owner.getUUID());
        large.setOwner(owner.getUUID());
        require(helper, large.getOwnerUUID() == null && dinosaur.getOrder() == DinosaurEntity.Order.WANDER,
                "only imprintable species may have an owner; the initial order is wander");
        var menu = new DinosaurOrderMenu(0, owner.getInventory(), dinosaur);
        require(helper, menu.stillValid(owner) && !menu.stillValid(stranger)
                && !menu.clickMenuButton(stranger, DinosaurEntity.Order.SIT.ordinal())
                && !menu.clickMenuButton(owner, -1) && !menu.clickMenuButton(owner, 3),
                "only a nearby owner may choose a valid order");
        require(helper, menu.clickMenuButton(owner, DinosaurEntity.Order.SIT.ordinal())
                && dinosaur.isOrderedToSit() && dinosaur.isNoAi(), "the sit order must stop AI movement");
        CompoundTag saved = dinosaur.saveWithoutId(new CompoundTag());
        DinosaurEntity loaded = DinosaurRegistry.type(Species.VELOCIRAPTOR).create(helper.getLevel());
        if (loaded == null) { helper.fail("could not restore dinosaur"); return; }
        loaded.load(saved);
        require(helper, owner.getUUID().equals(loaded.getOwnerUUID()) && loaded.isOrderedToSit() && loaded.isNoAi()
                && !saved.getBoolean("NoAI"), "owner and order persist without making NoAI permanent");
        loaded.setOrder(DinosaurEntity.Order.FOLLOW);
        require(helper, !loaded.isNoAi(), "changing the order must restore normal AI");
        saved.putInt("Order", 123);
        loaded.load(saved);
        require(helper, loaded.getOrder() == DinosaurEntity.Order.WANDER, "unknown saved orders must fall back to wander");
        helper.succeed();
    }

    @GameTest(template = "jurassicraft:feeder_test")
    public static void sittingMicroraptorsRideAndDismountOnlyTheirOwner(GameTestHelper helper) {
        var owner = helper.makeMockPlayer();
        var stranger = helper.makeMockPlayer();
        var first = DinosaurRegistry.type(Species.MICRORAPTOR).create(helper.getLevel());
        var second = DinosaurRegistry.type(Species.MICRORAPTOR).create(helper.getLevel());
        var third = DinosaurRegistry.type(Species.MICRORAPTOR).create(helper.getLevel());
        if (first == null || second == null || third == null) { helper.fail("could not create microraptors"); return; }
        owner.setShiftKeyDown(true);
        stranger.setShiftKeyDown(true);
        for (var dinosaur : List.of(first, second, third)) {
            dinosaur.setOwner(owner.getUUID());
            dinosaur.setOrder(DinosaurEntity.Order.SIT);
        }
        first.mobInteract(stranger, net.minecraft.world.InteractionHand.MAIN_HAND);
        require(helper, !first.isPassenger(), "a stranger cannot carry another player's raptor");
        first.mobInteract(owner, net.minecraft.world.InteractionHand.MAIN_HAND);
        second.mobInteract(owner, net.minecraft.world.InteractionHand.MAIN_HAND);
        third.mobInteract(owner, net.minecraft.world.InteractionHand.MAIN_HAND);
        require(helper, first.getVehicle() == owner && second.getVehicle() == owner && !third.isPassenger()
                && owner.getPassengers().size() == 2, "a sitting owner's microraptors can ride two shoulders");
        require(helper, !DinosaurNetwork.dismount(stranger, first) && first.getVehicle() == owner,
                "a dismount request from another player must be rejected");
        require(helper, DinosaurNetwork.dismount(owner, first) && !first.isPassenger()
                && !DinosaurNetwork.dismount(owner, first), "the carrier can dismount only a current passenger");
        second.stopRiding();
        helper.succeed();
    }

    @GameTest(template = "jurassicraft:feeder_test")
    public static void sneakUsingToolsReachesTheItemInteraction(GameTestHelper helper) {
        var dinosaur = DinosaurRegistry.type(Species.VELOCIRAPTOR).create(helper.getLevel());
        if (dinosaur == null) { helper.fail("could not create dinosaur"); return; }
        var player = helper.makeMockPlayer();
        player.setShiftKeyDown(true);
        ItemStack scanner = new ItemStack(DinoScannerRegistry.SCANNER.get());
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, scanner);
        int energy = dinosaur.energy();
        player.interactOn(dinosaur, net.minecraft.world.InteractionHand.MAIN_HAND);
        require(helper, dinosaur.energy() == Math.max(0, energy - 5000),
                "sneak-using the scanner must reach the item and drain energy");
        helper.succeed();
    }

    @GameTest(template = "jurassicraft:feeder_test")
    public static void thirstPersistsAndPotionsRestoreWater(GameTestHelper helper) {
        var dinosaur = DinosaurRegistry.type(Species.MUSSAURUS).create(helper.getLevel());
        var restored = DinosaurRegistry.type(Species.MUSSAURUS).create(helper.getLevel());
        if (dinosaur == null || restored == null) { helper.fail("could not create dinosaur"); return; }
        CompoundTag data = dinosaur.saveWithoutId(new CompoundTag());
        data.putInt("Water", 20);
        restored.load(data);
        require(helper, restored.isThirsty() && restored.isDehydrated() && restored.water() == 20,
                "low saved water should make an animal thirsty and dehydrated");
        var player = helper.makeMockPlayer();
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.POTION));
        restored.mobInteract(player, net.minecraft.world.InteractionHand.MAIN_HAND);
        require(helper, restored.water() == 1020 && restored.saveWithoutId(new CompoundTag()).getInt("Water") == 1020,
                "a potion restores and persists one thousand water");
        data.remove("Water");
        restored.load(data);
        require(helper, restored.water() == restored.maximumWater(), "saves from before hydration start full");
        helper.succeed();
    }

    @GameTest(template = "jurassicraft:feeder_test")
    public static void disabledGrowthAndMetabolismPreserveAgeAndNeeds(GameTestHelper helper) {
        var dinosaur = new DinosaurEntity(DinosaurRegistry.type(Species.MUSSAURUS), helper.getLevel()) {
            @Override boolean metabolismEnabled() { return false; }
            @Override boolean growthEnabled() { return false; }
        };
        dinosaur.setBaby(true);
        CompoundTag data = dinosaur.saveWithoutId(new CompoundTag());
        data.putInt("Energy", 100);
        data.putInt("Water", 25);
        dinosaur.load(data);
        int age = dinosaur.getAge();
        float health = dinosaur.getHealth();
        dinosaur.aiStep();
        require(helper, dinosaur.getAge() == age && dinosaur.energy() == 100 && dinosaur.water() == 25
                && dinosaur.getHealth() == health, "disabled rules stop growth, metabolism and starvation damage");
        require(helper, !new DrinkFromWaterGoal(dinosaur).canUse() && !new GrazePlantsGoal(dinosaur).canUse(),
                "disabled metabolism also stops drinking and grazing AI");
        helper.succeed();
    }

    @GameTest(template = "jurassicraft:feeder_test")
    public static void enabledMetabolismDehydratesAndRainRestoresWater(GameTestHelper helper) {
        var dinosaur = new DinosaurEntity(DinosaurRegistry.type(Species.MUSSAURUS), helper.getLevel()) {
            @Override boolean metabolismEnabled() { return true; }
        };
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        dinosaur.moveTo(pos.getX() + .5, pos.getY(), pos.getZ() + .5, 0, 0);
        CompoundTag data = dinosaur.saveWithoutId(new CompoundTag());
        data.putInt("Water", 25);
        dinosaur.load(data);
        dinosaur.tickCount = 40;
        int energy = dinosaur.energy();
        float health = dinosaur.getHealth();
        dinosaur.aiStep();
        require(helper, dinosaur.energy() == energy - 1 && dinosaur.water() == 24 && dinosaur.getHealth() < health,
                "enabled metabolism consumes food and water and dehydration causes damage");
        var wet = new DinosaurEntity(DinosaurRegistry.type(Species.MUSSAURUS), helper.getLevel()) {
            @Override boolean metabolismEnabled() { return true; }
            @Override public boolean isInWaterRainOrBubble() { return true; }
        };
        data = wet.saveWithoutId(new CompoundTag());
        data.putInt("Water", 100);
        wet.load(data);
        wet.aiStep();
        require(helper, wet.water() == wet.maximumWater(), "rain and water refill the original hydration meter");
        helper.succeed();
    }

    @GameTest(template = "jurassicraft:feeder_test")
    public static void hungryHerbivoresGrazeAndWaterBanksAreRecognized(GameTestHelper helper) {
        var dinosaur = DinosaurRegistry.type(Species.MUSSAURUS).create(helper.getLevel());
        var carnivore = DinosaurRegistry.type(Species.VELOCIRAPTOR).create(helper.getLevel());
        if (dinosaur == null || carnivore == null) { helper.fail("could not create dinosaurs"); return; }
        BlockPos plant = helper.absolutePos(new BlockPos(2, 1, 2));
        BlockPos shore = helper.absolutePos(new BlockPos(1, 1, 1));
        dinosaur.moveTo(plant.getX() + .5, plant.getY(), plant.getZ() + .5, 0, 0);
        carnivore.moveTo(dinosaur.getX(), dinosaur.getY(), dinosaur.getZ(), 0, 0);
        helper.getLevel().setBlock(plant, Blocks.OAK_LEAVES.defaultBlockState(), 3);
        helper.getLevel().setBlock(shore, Blocks.STONE.defaultBlockState(), 3);
        helper.getLevel().setBlock(shore.east(), Blocks.WATER.defaultBlockState(), 3);
        dinosaur.reduceEnergy(dinosaur.maximumEnergy());
        carnivore.reduceEnergy(carnivore.maximumEnergy());
        var grazers = new GrazePlantsGoal(dinosaur);
        require(helper, new DrinkFromWaterGoal(dinosaur).isValidTarget(helper.getLevel(), shore),
                "a solid bank adjacent to water is a drinking target");
        require(helper, !new GrazePlantsGoal(carnivore).graze(plant), "carnivores cannot graze plants");
        require(helper, grazers.graze(plant) && helper.getLevel().isEmptyBlock(plant) && dinosaur.energy() == 200,
                "a hungry herbivore consumes nearby leaves for the original food value");
        helper.getLevel().setBlock(plant, PlantRegistry.plant(PlantSpecies.SMALL_CYCAD).get().defaultBlockState(), 3);
        require(helper, grazers.graze(plant) && dinosaur.energy() == 400
                && dinosaur.hasEffect(net.minecraft.world.effect.MobEffects.POISON),
                "the original ancient-plant nutrition and poison effects apply to grazing");
        helper.getLevel().setBlock(shore.east(), Blocks.AIR.defaultBlockState(), 3);
        helper.getLevel().setBlock(shore, Blocks.AIR.defaultBlockState(), 3);
        helper.succeed();
    }

    @GameTest(template = "jurassicraft:feeder_test")
    public static void onlyFullAggressiveHerdsAttackOutcasts(GameTestHelper helper) {
        var leader = DinosaurRegistry.type(Species.VELOCIRAPTOR).create(helper.getLevel());
        var outcast = DinosaurRegistry.type(Species.VELOCIRAPTOR).create(helper.getLevel());
        if (leader == null || outcast == null) { helper.fail("could not create raptors"); return; }
        DinosaurHerd herd = leader.herd();
        for (int i = 1; i < DinosaurHerd.maxSize(Species.VELOCIRAPTOR); i++) {
            var member = DinosaurRegistry.type(Species.VELOCIRAPTOR).create(helper.getLevel());
            if (member == null || !herd.merge(member.herd())) { helper.fail("could not fill herd"); return; }
        }
        herd.considerOutcast(outcast, false);
        require(helper, !herd.hasEnemies(), "outcast attacks must respect the disabled gamerule");
        herd.considerOutcast(outcast, true);
        require(helper, herd.hasEnemies() && leader.getTarget() == outcast, "a full aggressive pack targets an outcast");
        helper.succeed();
    }

    @GameTest(template = "jurassicraft:feeder_test")
    public static void fenceShockInterruptsHerdingAndSignalsEscape(GameTestHelper helper) {
        var dinosaur = DinosaurRegistry.type(Species.MUSSAURUS).create(helper.getLevel());
        if (dinosaur == null) { helper.fail("could not create dinosaur"); return; }
        dinosaur.onFenceShock();
        dinosaur.onFenceShock();
        dinosaur.onFenceShock();
        require(helper, dinosaur.wireTicks() == 2 && dinosaur.disableHerdingTicks() == 200
                && new EscapeDinosaurFenceGoal(dinosaur).canUse(), "wire contact must signal escape and pause herding");
        dinosaur.aiStep();
        require(helper, dinosaur.wireTicks() == 1 && dinosaur.disableHerdingTicks() == 199,
                "shock and herding delays count down independently");
        helper.succeed();
    }

    @GameTest(template = "jurassicraft:feeder_test")
    public static void animateCommandSelectsSupportedVariants(GameTestHelper helper) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        clearPreviousDinosaurs(helper, new BlockPos(1, 1, 1));
        var dinosaur = helper.spawnWithNoFreeWill(DinosaurRegistry.type(Species.TYRANNOSAURUS), new BlockPos(1, 1, 1));
        String tag = "animation_test_" + dinosaur.getUUID().toString().replace("-", "");
        dinosaur.addTag(tag);
        var dispatcher = new com.mojang.brigadier.CommandDispatcher<net.minecraft.commands.CommandSourceStack>();
        AnimateCommand.register(dispatcher);
        var source = helper.getLevel().getServer().createCommandSourceStack().withLevel(helper.getLevel())
                .withPosition(dinosaur.position()).withPermission(0).withSuppressedOutput();
        try {
            require(helper, dispatcher.execute("anim roaring @e[tag=" + tag + "] 2", source) == 1
                    && dinosaur.isForcedPose() && dinosaur.poseAnimation().equals("ROARING:2"),
                    "the alias must allow non-operator selectors and play the requested original variant");
            boolean rejected = false;
            try { dispatcher.execute("animate roaring @e[tag=" + tag + "] 99", source); }
            catch (com.mojang.brigadier.exceptions.CommandSyntaxException expected) { rejected = true; }
            require(helper, rejected && dinosaur.poseAnimation().equals("ROARING:2"),
                    "unavailable variants must not replace the current animation");
            int duration = DinosaurAnimationData.duration(Species.TYRANNOSAURUS, "ROARING:2");
            require(helper, duration > 0, "variant duration must come from imported frames");
            for (int tick = 0; tick < duration; tick++) dinosaur.aiStep();
            require(helper, dinosaur.poseAnimation().isEmpty(), "forced animations end at their imported duration");
        } finally {
            dinosaur.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "jurassicraft:feeder_test")
    public static void ambientCallsAnimateOnlyWhenIdle(GameTestHelper helper) {
        var dinosaur = DinosaurRegistry.type(Species.VELOCIRAPTOR).create(helper.getLevel());
        var fish = DinosaurRegistry.type(Species.COELACANTH).create(helper.getLevel());
        if (dinosaur == null || fish == null) { helper.fail("could not create dinosaurs"); return; }
        dinosaur.playAmbientSound();
        require(helper, dinosaur.poseAnimation().equals("SPEAK"), "idle calls must play the species speaking pose");
        require(helper, dinosaur.playPose("EATING", 0, true), "test requires an eating pose");
        dinosaur.playAmbientSound();
        require(helper, dinosaur.isForcedPose() && dinosaur.poseAnimation().equals("EATING"),
                "ambient calls must not interrupt an active pose");
        fish.playAmbientSound();
        require(helper, fish.poseAnimation().isEmpty(), "silent aquatic animals must not start a speaking pose");
        helper.succeed();
    }

    @GameTest(template = "jurassicraft:feeder_test")
    public static void naturalRestPersistsAndEndsForNeedsOrOrders(GameTestHelper helper) {
        var dinosaur = DinosaurRegistry.type(Species.MUSSAURUS).create(helper.getLevel());
        var threat = net.minecraft.world.entity.EntityType.ZOMBIE.create(helper.getLevel());
        var fish = DinosaurRegistry.type(Species.COELACANTH).create(helper.getLevel());
        if (dinosaur == null || threat == null || fish == null) { helper.fail("could not create test animals"); return; }
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        dinosaur.moveTo(pos.getX() + .5, pos.getY(), pos.getZ() + .5, 0, 0);
        dinosaur.setOnGround(true);
        dinosaur.setBaby(false);
        require(helper, dinosaur.beginNaturalRest() && dinosaur.isNaturallyResting()
                && !dinosaur.isOrderedToSit() && !dinosaur.isDinosaurSleeping() && !dinosaur.isNoAi(),
                "healthy grounded animals may rest without being ordered or disabling AI");
        CompoundTag resting = dinosaur.saveWithoutId(new CompoundTag());
        var restored = DinosaurRegistry.type(Species.MUSSAURUS).create(helper.getLevel());
        if (restored == null) { helper.fail("could not restore resting animal"); return; }
        restored.load(resting);
        require(helper, restored.isNaturallyResting() && !restored.isOrderedToSit() && !restored.isDinosaurSleeping(),
                "natural rest must persist without changing the owner order or sleep state");
        restored.setOnGround(true);
        restored.setTarget(threat);
        restored.updateNaturalRest();
        require(helper, !restored.isNaturallyResting() && !restored.beginNaturalRest(),
                "a target must interrupt rest and prevent immediate re-entry");
        restored.setTarget(null);
        require(helper, restored.beginNaturalRest(), "rest should resume when the threat is gone");
        restored.reduceEnergy(restored.maximumEnergy());
        restored.updateNaturalRest();
        require(helper, !restored.isNaturallyResting() && !restored.beginNaturalRest(),
                "hunger must interrupt rest");
        restored.load(resting);
        restored.setOnGround(false);
        restored.updateNaturalRest();
        require(helper, !restored.isNaturallyResting(), "rest must end when the animal is no longer grounded");
        restored.setOnGround(true);
        require(helper, restored.beginNaturalRest(), "rest should be available again on the ground");
        restored.setOwner(UUID.randomUUID());
        restored.setOrder(DinosaurEntity.Order.SIT);
        require(helper, !restored.isNaturallyResting() && restored.isOrderedToSit(),
                "an owner sit order replaces natural rest");
        CompoundTag ordered = restored.saveWithoutId(new CompoundTag());
        restored.load(ordered);
        require(helper, restored.isOrderedToSit() && !restored.isNaturallyResting(),
                "ordered sitting must persist independently of natural rest");
        fish.setOnGround(true);
        require(helper, !fish.beginNaturalRest(), "aquatic animals must not use land resting poses");
        helper.succeed();
    }

    @GameTest(template = "jurassicraft:feeder_test")
    public static void followAndOwnerDefenseRespectSpeciesAndAge(GameTestHelper helper) {
        DinosaurEntity dinosaur = DinosaurRegistry.type(Species.VELOCIRAPTOR).create(helper.getLevel());
        DinosaurEntity timid = DinosaurRegistry.type(Species.MUSSAURUS).create(helper.getLevel());
        var attacker = net.minecraft.world.entity.EntityType.ZOMBIE.create(helper.getLevel());
        if (dinosaur == null || timid == null || attacker == null) { helper.fail("could not construct test entities"); return; }
        var owner = helper.makeMockPlayer();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        dinosaur.moveTo(pos.getX() + .5, pos.getY(), pos.getZ() + .5, 0, 0);
        owner.moveTo(pos.getX() + 12.5, pos.getY(), pos.getZ() + .5, 0, 0);
        dinosaur.setOwner(owner.getUUID());
        timid.setOwner(owner.getUUID());
        dinosaur.setOrder(DinosaurEntity.Order.FOLLOW);
        timid.setOrder(DinosaurEntity.Order.FOLLOW);
        var follow = new FollowDinosaurOwnerGoal(dinosaur);
        require(helper, follow.canFollow(owner), "a distant owner should be followed");
        owner.moveTo(pos.getX() + 1.5, pos.getY(), pos.getZ() + .5, 0, 0);
        require(helper, !follow.canFollow(owner), "a nearby owner does not need to be followed");
        owner.setLastHurtByMob(attacker);
        var defend = new DefendDinosaurOwnerGoal(dinosaur, true);
        var timidDefense = new DefendDinosaurOwnerGoal(timid, true);
        require(helper, defend.consider(owner) && !timidDefense.consider(owner),
                "only species marked to defend their owner should respond");
        defend.start();
        require(helper, dinosaur.getTarget() == attacker, "a defending dinosaur should target the owner's attacker");
        defend.stop();
        owner.setLastHurtMob(attacker);
        var assist = new DefendDinosaurOwnerGoal(dinosaur, false);
        require(helper, assist.consider(owner), "a defender should assist the owner's attack");
        dinosaur.setBaby(true);
        require(helper, !assist.consider(owner), "juveniles below half-grown must not defend");
        dinosaur.setBaby(false);
        dinosaur.setOrder(DinosaurEntity.Order.WANDER);
        require(helper, !defend.consider(owner), "owner defense requires the follow order");
        helper.succeed();
    }

    @GameTest(template = "jurassicraft:feeder_test")
    public static void reproductionPreservesEmbryosAndLaysEggs(GameTestHelper helper) {
        DinosaurEntity mother = DinosaurRegistry.type(Species.MUSSAURUS).create(helper.getLevel());
        DinosaurEntity father = DinosaurRegistry.type(Species.MUSSAURUS).create(helper.getLevel());
        if (mother == null || father == null) { helper.fail("could not create breeding pair"); return; }
        BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 2));
        AABB area = new AABB(pos).inflate(5);
        Set<UUID> existing = entityIds(helper, DinosaurEggEntity.class, area);
        mother.moveTo(pos.getX() + .5, pos.getY(), pos.getZ() + .5, 0, 0);
        father.moveTo(pos.getX() + 1.2, pos.getY(), pos.getZ() + .5, 0, 0);
        mother.setMale(false); father.setMale(true);
        mother.setBaby(false); father.setBaby(false);
        mother.setGeneticAttributes(new DinosaurAttributes(1.1f, .9f, 1.05f, .95f));
        father.setGeneticAttributes(new DinosaurAttributes(.9f, 1.1f, .95f, 1.05f));
        mother.setGenetics("AAAAAAAAAA"); father.setGenetics("CCCCCCCCCC");
        mother.setDNAQuality(30); father.setDNAQuality(40);
        CompoundTag m = new CompoundTag(); mother.addAdditionalSaveData(m); m.putInt("BreedCooldown", 0); mother.readAdditionalSaveData(m);
        CompoundTag f = new CompoundTag(); father.addAdditionalSaveData(f); f.putInt("BreedCooldown", 0); father.readAdditionalSaveData(f);
        require(helper, mother.beginBreeding(father), "healthy adults should mate");
        require(helper, mother.isPregnant() && !father.isPregnant(), "pregnancy must belong to the mother");
        CompoundTag pregnant = mother.saveWithoutId(new CompoundTag());
        ListTag children = pregnant.getList("Embryos", Tag.TAG_COMPOUND);
        require(helper, children.size() >= 2 && children.size() <= 8 && pregnant.getInt("GestationTicks") == 9600, "pregnancy and clutch must persist");
        for (Tag child : children) {
            CompoundTag embryo = (CompoundTag) child;
            require(helper, embryo.getInt("DNAQuality") == 70 && embryo.getString("Genetics").matches("[AC]{10}")
                    && DinosaurAttributes.read(embryo.getCompound("GeneticAttributes")).isPresent(),
                    "embryo must inherit parental DNA and genetic attributes");
        }
        DinosaurEntity restored = DinosaurRegistry.type(Species.MUSSAURUS).create(helper.getLevel());
        if (restored == null) { helper.fail("could not restore pregnancy"); return; }
        pregnant.putInt("GestationTicks", 1);
        restored.load(pregnant);
        restored.aiStep();
        int duration = DinosaurAnimationData.duration(Species.MUSSAURUS, "LAYING_EGG");
        int midpoint = duration / 2;
        require(helper, restored.isBirthing() && restored.poseAnimation().equals("LAYING_EGG")
                && newEntities(helper, DinosaurEggEntity.class, area, existing).isEmpty(),
                "gestation must begin the egg-laying pose without immediately producing eggs");
        for (int tick = 0; tick < midpoint - 1; tick++) restored.aiStep();
        require(helper, newEntities(helper, DinosaurEggEntity.class, area, existing).isEmpty(),
                "eggs must not appear before the animation midpoint");
        CompoundTag inProgress = restored.saveWithoutId(new CompoundTag());
        require(helper, inProgress.getInt("BirthElapsed") == midpoint - 1,
                "in-progress birth timing must persist");
        DinosaurEntity resumed = DinosaurRegistry.type(Species.MUSSAURUS).create(helper.getLevel());
        if (resumed == null) { helper.fail("could not restore laying animation"); return; }
        resumed.load(inProgress);
        resumed.aiStep();
        require(helper, resumed.isBirthing() && resumed.birthAnimationTime(resumed.tickCount) == midpoint - 1
                && newEntities(helper, DinosaurEggEntity.class, area, existing).isEmpty(),
                "restoring must resume the visible pose without delivering early");
        resumed.aiStep();
        var eggs = newEntities(helper, DinosaurEggEntity.class, area, existing);
        require(helper, eggs.size() == children.size() && !resumed.isPregnant(),
                "the midpoint must produce the complete clutch exactly once");
        for (DinosaurEggEntity egg : eggs) {
            CompoundTag data = egg.saveWithoutId(new CompoundTag());
            require(helper, egg.species() == Species.MUSSAURUS && data.getInt("DNAQuality") == 70
                    && data.getString("Genetics").matches("[AC]{10}")
                    && children.stream().map(tag -> ((CompoundTag) tag).getCompound("GeneticAttributes"))
                            .anyMatch(data.getCompound("GeneticAttributes")::equals),
                    "laid eggs must retain the embryo's inherited DNA and attributes");
        }
        CompoundTag afterDelivery = resumed.saveWithoutId(new CompoundTag());
        DinosaurEntity afterReload = DinosaurRegistry.type(Species.MUSSAURUS).create(helper.getLevel());
        if (afterReload == null) { helper.fail("could not restore completed delivery"); return; }
        afterReload.load(afterDelivery);
        for (int tick = 0; tick <= duration; tick++) afterReload.aiStep();
        require(helper, !afterReload.isBirthing() && newEntities(helper, DinosaurEggEntity.class, area, existing).size() == children.size(),
                "reloading after delivery must finish the pose without duplicating eggs");
        eggs.forEach(Entity::discard);
        helper.succeed();
    }

    @GameTest(template = "jurassicraft:vehicle_test")
    public static void coelacanthBirthRetriesWhenWaterIsUnavailable(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(7, 2, 7));
        AABB area = new AABB(origin).inflate(4);
        // Other tests can leave animals in the reused world; they would block random birth positions.
        level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, area,
                entity -> !(entity instanceof net.minecraft.world.entity.player.Player))
                .forEach(Entity::discard);
        Set<UUID> existing = entityIds(helper, DinosaurEntity.class, area);
        var originalBlocks = new java.util.HashMap<BlockPos, net.minecraft.world.level.block.state.BlockState>();
        // Keep the untracked fixture in the prepared volume while manually advancing its AI.
        DinosaurEntity mother = new DinosaurEntity(DinosaurRegistry.type(Species.COELACANTH), level) {
            @Override public void travel(Vec3 input) { setDeltaMovement(Vec3.ZERO); }
        };
        try {
            for (int x = -2; x <= 2; x++) for (int y = -1; y <= 1; y++) for (int z = -2; z <= 2; z++) {
                BlockPos pos = origin.offset(x, y, z);
                originalBlocks.put(pos, level.getBlockState(pos));
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
            }
            mother.moveTo(origin.getX() + .5, origin.getY(), origin.getZ() + .5, 0, 0);
            mother.setMale(false);
            mother.setBaby(false);
            CompoundTag data = mother.saveWithoutId(new CompoundTag());
            CompoundTag child = new CompoundTag();
            child.putString("Genetics", "ACGTACGTAC");
            child.putInt("DNAQuality", 77);
            child.putBoolean("Male", true);
            var traits = new DinosaurAttributes(1.1f, .9f, 1.05f, .95f);
            CompoundTag inherited = new CompoundTag();
            traits.write(inherited);
            child.put("GeneticAttributes", inherited);
            ListTag embryos = new ListTag();
            embryos.add(child);
            data.put("Embryos", embryos);
            data.putInt("GestationTicks", 1);
            mother.load(data);
            mother.aiStep();
            int duration = DinosaurAnimationData.duration(Species.COELACANTH, "GIVING_BIRTH");
            require(helper, mother.isBirthing() && mother.poseAnimation().equals("GIVING_BIRTH"),
                    "coelacanth must use the live-birth pose");
            for (int tick = 0; tick < duration / 2; tick++) mother.aiStep();
            require(helper, mother.isPregnant() && newEntities(helper, DinosaurEntity.class, area, existing).isEmpty(),
                    "a blocked delivery must retain its embryo");
            originalBlocks.keySet().forEach(pos -> level.setBlock(pos, Blocks.WATER.defaultBlockState(), 2));
            for (int tick = 0; tick < 3 * duration && mother.isPregnant(); tick++) mother.aiStep();
            var babies = newEntities(helper, DinosaurEntity.class, area, existing);
            require(helper, !mother.isPregnant() && babies.size() == 1,
                    "retry must deliver one baby (pregnant=" + mother.isPregnant() + ", babies=" + babies.size() + ")");
            DinosaurEntity baby = babies.get(0);
            require(helper, baby.isBaby() && baby.isMale() && baby.getGenetics().equals("ACGTACGTAC")
                    && baby.getDNAQuality() == 77 && baby.geneticAttributes().equals(traits),
                    "retry must preserve age, sex, DNA and attributes (baby=" + baby.isBaby() + ", male=" + baby.isMale()
                            + ", genetics=" + baby.getGenetics() + ", quality=" + baby.getDNAQuality()
                            + ", attributes=" + baby.geneticAttributes() + ", expected=" + traits + ")");
        } finally {
            newEntities(helper, DinosaurEntity.class, area, existing).forEach(Entity::discard);
            originalBlocks.forEach((pos, state) -> level.setBlock(pos, state, 2));
        }
        helper.succeed();
    }

    @GameTest(template = "jurassicraft:feeder_test")
    public static void birthPausesForForcedPosesAndTranquilization(GameTestHelper helper) {
        var mother = DinosaurRegistry.type(Species.MUSSAURUS).create(helper.getLevel());
        var father = DinosaurRegistry.type(Species.MUSSAURUS).create(helper.getLevel());
        if (mother == null || father == null) { helper.fail("could not create breeding pair"); return; }
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        // The mother may stroll between recovering and resuming, so eggs are sought around her.
        AABB area = new AABB(pos).inflate(16);
        Set<UUID> existing = entityIds(helper, DinosaurEggEntity.class, area);
        mother.moveTo(pos.getX() + .5, pos.getY(), pos.getZ() + .5, 0, 0);
        mother.setMale(false); father.setMale(true);
        mother.setBaby(false); father.setBaby(false);
        require(helper, mother.forceBreedWith(father), "test pair must breed");
        CompoundTag data = mother.saveWithoutId(new CompoundTag());
        data.putInt("GestationTicks", 1);
        mother.load(data);
        mother.aiStep();
        for (int tick = 0; tick < 5; tick++) mother.aiStep();
        int progress = mother.saveWithoutId(new CompoundTag()).getInt("BirthElapsed");
        require(helper, mother.playPose("EATING", 0, true), "test requires a forced pose");
        mother.tranquilize(1);
        for (int tick = 0; tick < 10; tick++) mother.aiStep();
        require(helper, !mother.isBirthing() && mother.saveWithoutId(new CompoundTag()).getInt("BirthElapsed") == progress
                && newEntities(helper, DinosaurEggEntity.class, area, existing).isEmpty(),
                "interruptions must pause birth without losing or delivering embryos");
        for (int tick = 0; tick < 120 && !mother.isBirthing(); tick++) mother.aiStep();
        require(helper, mother.isBirthing() && mother.saveWithoutId(new CompoundTag()).getInt("BirthElapsed") == progress,
                "birth must resume at the saved frame when interruptions end");
        int midpoint = DinosaurAnimationData.duration(Species.MUSSAURUS, "LAYING_EGG") / 2;
        for (int tick = progress; tick < midpoint; tick++) mother.aiStep();
        var eggs = newEntities(helper, DinosaurEggEntity.class, mother.getBoundingBox().inflate(2), existing);
        require(helper, !mother.isPregnant() && !eggs.isEmpty(), "resumed birth must deliver the clutch at its midpoint");
        eggs.forEach(Entity::discard);
        helper.succeed();
    }

    @GameTest(template = "jurassicraft:feeder_test")
    public static void dartsPersistAndRecover(GameTestHelper helper) {
        DinosaurEntity dino = DinosaurRegistry.type(Species.VELOCIRAPTOR).create(helper.getLevel());
        if (dino == null) { helper.fail("could not create dinosaur"); return; }
        dino.tranquilize(100);
        require(helper, dino.isTranquilized() && dino.isNoAi() && dino.tranquilizerTicks() >= 150, "dart should disable AI");
        CompoundTag saved = dino.saveWithoutId(new CompoundTag());
        require(helper, !saved.getBoolean("NoAI"), "temporary sedation must not persist as permanent NoAI");
        DinosaurEntity loaded = DinosaurRegistry.type(Species.VELOCIRAPTOR).create(helper.getLevel());
        if (loaded == null) { helper.fail("could not restore dinosaur"); return; }
        loaded.load(saved);
        require(helper, loaded.isTranquilized() && loaded.tranquilizerTicks() == dino.tranquilizerTicks(), "tranquilizer must persist");
        saved.putInt("TranquilizerTicks", 1);
        loaded.load(saved);
        loaded.aiStep();
        require(helper, !loaded.isTranquilized() && !loaded.isNoAi(), "AI must resume after recovery");
        loaded.setNoAi(true);
        loaded.tranquilize(100);
        CompoundTag disabled = loaded.saveWithoutId(new CompoundTag());
        disabled.putInt("TranquilizerTicks", 1);
        loaded.load(disabled);
        loaded.aiStep();
        require(helper, !loaded.isTranquilized() && loaded.isNoAi(), "explicit NoAI must survive sedation");
        loaded.setNoAi(false);
        loaded.setDeathIn(200);
        saved = loaded.saveWithoutId(new CompoundTag());
        require(helper, saved.getInt("TicksUntilDeath") == 200, "lethal dart timer must persist");
        saved.putInt("TicksUntilDeath", 1);
        loaded.load(saved);
        loaded.aiStep();
        require(helper, !loaded.isAlive(), "lethal dart must eventually kill");
        helper.succeed();
    }

    @GameTest(template = "jurassicraft:feeder_test")
    public static void sleepSchedulesAndWake(GameTestHelper helper) {
        require(helper, !DinosaurEntity.isSleepTime(Species.VELOCIRAPTOR, 16000)
                && DinosaurEntity.isSleepTime(Species.VELOCIRAPTOR, 16001)
                && !DinosaurEntity.isSleepTime(Species.VELOCIRAPTOR, 24000), "diurnal sleep window");
        require(helper, DinosaurEntity.isSleepTime(Species.DILOPHOSAURUS, 22481)
                && DinosaurEntity.isSleepTime(Species.DILOPHOSAURUS, 6479)
                && !DinosaurEntity.isSleepTime(Species.DILOPHOSAURUS, 6480), "crepuscular sleep window");
        require(helper, !DinosaurEntity.isSleepTime(Species.COELACANTH, 20000), "coelacanth does not sleep");
        DinosaurEntity dinosaur = DinosaurRegistry.type(Species.MUSSAURUS).create(helper.getLevel());
        if (dinosaur == null) { helper.fail("could not create dinosaur"); return; }
        dinosaur.setNaturalSleep(true);
        CompoundTag saved = dinosaur.saveWithoutId(new CompoundTag());
        require(helper, dinosaur.isNoAi() && !saved.getBoolean("NoAI"), "sleep pauses AI without saving permanent NoAI");
        DinosaurEntity loaded = DinosaurRegistry.type(Species.MUSSAURUS).create(helper.getLevel());
        if (loaded == null) { helper.fail("could not restore dinosaur"); return; }
        loaded.load(saved);
        require(helper, loaded.isNaturallySleeping() && loaded.isNoAi(), "natural sleep must persist");
        loaded.hurt(DamageSource.GENERIC, 1);
        require(helper, !loaded.isNaturallySleeping() && !loaded.isNoAi(), "damage must wake natural sleep");
        helper.succeed();
    }

    @GameTest(template = "jurassicraft:feeder_test")
    public static void microraptorGlidesAndLands(GameTestHelper helper) {
        DinosaurEntity raptor = DinosaurRegistry.type(Species.MICRORAPTOR).create(helper.getLevel());
        if (raptor == null) { helper.fail("could not create microraptor"); return; }
        BlockPos pos = helper.absolutePos(new BlockPos(1, 12, 1));
        raptor.moveTo(pos.getX() + .5, pos.getY(), pos.getZ() + .5, 0, 0);
        raptor.setOnGround(false);
        for (int i = 0; i < 5; i++) raptor.updateGlidingState();
        require(helper, raptor.isGliding(), "airborne microraptor must glide");
        raptor.fallDistance = 8;
        raptor.setDeltaMovement(0, -2, 0);
        raptor.travel(Vec3.ZERO);
        require(helper, raptor.fallDistance < .3f && raptor.getDeltaMovement().y > -.3, "gliding must slow descent");
        raptor.setOnGround(true);
        raptor.updateGlidingState();
        require(helper, !raptor.isGliding(), "landing must end gliding");
        helper.succeed();
    }

    @GameTest(template = "jurassicraft:feeder_test")
    public static void herdDefenseAndFleeing(GameTestHelper helper) {
        DinosaurEntity first = DinosaurRegistry.type(Species.TRICERATOPS).create(helper.getLevel());
        DinosaurEntity second = DinosaurRegistry.type(Species.TRICERATOPS).create(helper.getLevel());
        DinosaurEntity predator = DinosaurRegistry.type(Species.VELOCIRAPTOR).create(helper.getLevel());
        DinosaurEntity timid = DinosaurRegistry.type(Species.MUSSAURUS).create(helper.getLevel());
        DinosaurEntity timidMate = DinosaurRegistry.type(Species.MUSSAURUS).create(helper.getLevel());
        if (first == null || second == null || predator == null || timid == null || timidMate == null) {
            helper.fail("could not create herd members"); return;
        }
        DinosaurHerd defenders = first.herd();
        require(helper, defenders.merge(second.herd()) && defenders.size() == 2, "same species should form a herd");
        first.setNaturalSleep(true); second.setNaturalSleep(true);
        first.hurt(DamageSource.mobAttack(predator), 1);
        require(helper, !first.isNaturallySleeping() && !second.isNaturallySleeping(), "an attack should wake the whole herd");
        require(helper, defenders.hasEnemies() && !defenders.isFleeing() && second.getTarget() == predator,
                "strong triceratops herd should defend against the attacker");
        DinosaurEntity joining = DinosaurRegistry.type(Species.TRICERATOPS).create(helper.getLevel());
        if (joining == null) { helper.fail("could not create joining member"); return; }
        joining.setNaturalSleep(true);
        require(helper, defenders.merge(joining.herd()) && !joining.isNaturallySleeping() && joining.getTarget() == predator,
                "new members must share existing threats");
        DinosaurHerd fleeing = timid.herd();
        require(helper, fleeing.merge(timidMate.herd()), "timid pair should join");
        timid.hurt(DamageSource.mobAttack(predator), 1);
        require(helper, fleeing.isFleeing() && timidMate.getTarget() == null, "mussaurus herd should flee");
        timidMate.setTarget(predator);
        require(helper, timidMate.getTarget() == null, "fleeing herd must not resume attacking");
        defenders.remove(first);
        require(helper, defenders.leader() == second, "surviving herd member must become leader");
        helper.succeed();
    }

    @GameTest(template = "jurassicraft:feeder_test")
    public static void herdLimitsAndPredatorPrey(GameTestHelper helper) {
        DinosaurEntity brachiosaurus = DinosaurRegistry.type(Species.BRACHIOSAURUS).create(helper.getLevel());
        DinosaurEntity raptor = DinosaurRegistry.type(Species.VELOCIRAPTOR).create(helper.getLevel());
        DinosaurEntity micro = DinosaurRegistry.type(Species.MICRORAPTOR).create(helper.getLevel());
        DinosaurEntity triceratops = DinosaurRegistry.type(Species.TRICERATOPS).create(helper.getLevel());
        if (brachiosaurus == null || raptor == null || micro == null || triceratops == null) {
            helper.fail("could not create species"); return;
        }
        DinosaurHerd group = brachiosaurus.herd();
        for (int i = 0; i < 3; i++) {
            DinosaurEntity another = DinosaurRegistry.type(Species.BRACHIOSAURUS).create(helper.getLevel());
            if (another == null) { helper.fail("could not create herd member"); return; }
            require(helper, group.merge(another.herd()), "brachiosaurus herd should accept up to four");
        }
        DinosaurEntity extra = DinosaurRegistry.type(Species.BRACHIOSAURUS).create(helper.getLevel());
        if (extra == null) { helper.fail("could not create extra member"); return; }
        require(helper, !group.merge(extra.herd()) && group.size() == 4, "species herd limit must be enforced");
        require(helper, DinosaurHerd.maxSize(Species.COELACANTH) == 1 && DinosaurHerd.maxSize(Species.VELOCIRAPTOR) == 18,
                "solitary fish and raptor pack limits");
        require(helper, raptor.canHunt(triceratops) && !raptor.canHunt(brachiosaurus)
                && !micro.canHunt(triceratops), "predator awareness must follow upstream prey lists");
        helper.succeed();
    }

    @GameTest(template = "jurassicraft:feeder_test")
    public static void debugBreedingAndBirth(GameTestHelper helper) {
        DinosaurEntity mother = DinosaurRegistry.type(Species.MUSSAURUS).create(helper.getLevel());
        DinosaurEntity father = DinosaurRegistry.type(Species.MUSSAURUS).create(helper.getLevel());
        if (mother == null || father == null) { helper.fail("could not create pair"); return; }
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        AABB area = new AABB(pos).inflate(5);
        Set<UUID> existing = entityIds(helper, DinosaurEggEntity.class, area);
        mother.moveTo(pos.getX() + .5, pos.getY(), pos.getZ() + .5, 0, 0);
        father.moveTo(pos.getX() + 10, pos.getY(), pos.getZ() + .5, 0, 0);
        mother.setMale(false); father.setMale(true);
        require(helper, mother.forceBreedWith(father) && mother.isPregnant(), "debug breeding bypasses distance and cooldown");
        int count = mother.saveWithoutId(new CompoundTag()).getList("Embryos", Tag.TAG_COMPOUND).size();
        require(helper, !father.deliverPregnancyNow() && mother.deliverPregnancyNow(), "only pregnant animals can give birth");
        var eggs = newEntities(helper, DinosaurEggEntity.class, area, existing);
        require(helper, !mother.isPregnant() && eggs.size() == count, "debug birth must deliver the clutch");
        eggs.forEach(Entity::discard);
        helper.succeed();
    }

}
