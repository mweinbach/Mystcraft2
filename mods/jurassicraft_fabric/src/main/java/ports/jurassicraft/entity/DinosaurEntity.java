package ports.jurassicraft.entity;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.SmoothSwimmingMoveControl;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.navigation.WaterBoundPathNavigation;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.animal.Rabbit;
import net.minecraft.world.entity.animal.Squid;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.Vec3;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import ports.jurassicraft.content.Species;
import ports.jurassicraft.config.JurassicConfig;
import ports.jurassicraft.assembly.AssemblyRegistry;
import ports.jurassicraft.bugs.BugRegistry;
import ports.jurassicraft.genetics.DNAData;
import ports.jurassicraft.genetics.DinosaurAttributes;
import ports.jurassicraft.genetics.Genetics;

/** Living dinosaurs share growth, genetics, and basic behavior; each has its own entity type. */
public class DinosaurEntity extends Animal {
    private static final EntityDataAccessor<Optional<UUID>> OWNER = SynchedEntityData.defineId(DinosaurEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Integer> ORDER = SynchedEntityData.defineId(DinosaurEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> MALE = SynchedEntityData.defineId(DinosaurEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> GROWTH = SynchedEntityData.defineId(DinosaurEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> GENETIC_SCALE = SynchedEntityData.defineId(DinosaurEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> HUNGRY = SynchedEntityData.defineId(DinosaurEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> THIRSTY = SynchedEntityData.defineId(DinosaurEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> TRANQUILIZED = SynchedEntityData.defineId(DinosaurEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> NATURAL_SLEEP = SynchedEntityData.defineId(DinosaurEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> NATURAL_REST = SynchedEntityData.defineId(DinosaurEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> GLIDING = SynchedEntityData.defineId(DinosaurEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> CLIMBING = SynchedEntityData.defineId(DinosaurEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> CARCASS = SynchedEntityData.defineId(DinosaurEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> RUNNING_ANIMATION = SynchedEntityData.defineId(DinosaurEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> COMBAT_ANIMATION = SynchedEntityData.defineId(DinosaurEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> COMBAT_POSE_INTERRUPTED = SynchedEntityData.defineId(DinosaurEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<String> POSE_ANIMATION = SynchedEntityData.defineId(DinosaurEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> POSE_SERIAL = SynchedEntityData.defineId(DinosaurEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> BIRTH_STARTED = SynchedEntityData.defineId(DinosaurEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> BIRTH_ANIMATING = SynchedEntityData.defineId(DinosaurEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> INJURY_ANIMATING = SynchedEntityData.defineId(DinosaurEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> INJURY_STARTED = SynchedEntityData.defineId(DinosaurEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> ATTACK_ANIMATING = SynchedEntityData.defineId(DinosaurEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> ATTACK_STARTED = SynchedEntityData.defineId(DinosaurEntity.class, EntityDataSerializers.INT);
    private static final TagKey<Item> FEEDER_MEAT = TagKey.create(Registry.ITEM_REGISTRY, new ResourceLocation("jurassicraft", "feeder_meat"));
    private static final TagKey<Item> FEEDER_PLANTS = TagKey.create(Registry.ITEM_REGISTRY, new ResourceLocation("jurassicraft", "feeder_plants"));
    private static final TagKey<Item> DINOSAUR_FISH = TagKey.create(Registry.ITEM_REGISTRY, new ResourceLocation("jurassicraft", "dinosaur_fish"));
    private String genetics = "AAAAAAAAAA";
    private static final DinosaurAttributes DEFAULT_ATTRIBUTES = new DinosaurAttributes(1, 1, 1, 1);
    private DinosaurAttributes geneticAttributes = DEFAULT_ATTRIBUTES;
    private long totalAgeTicks;
    private int energy;
    private int water;
    private int digestingFood;
    private int breedCooldown;
    private int gestationTicks;
    private int birthElapsed = -1;
    private int birthRetryTicks;
    private int tranquilizerTicks;
    private int ticksUntilDeath;
    private int stayAwakeTicks;
    private int airborneTicks;
    private int carcassHealth;
    private int carcassDecayTicks;
    private int carcassAnimationStart;
    private int combatAnimationStart;
    private int poseAnimationStart;
    private int poseTicks;
    private int injuryTicks;
    private int attackTicks;
    private int wireTicks;
    private int disableHerdingTicks;
    private boolean carcassLootExhausted;
    private final DinosaurInventory inventory;
    private final DinosaurFamilyBrain familyBrain = new DinosaurFamilyBrain(this);
    private DinosaurHerd herd;
    private ListTag embryos = new ListTag();
    private int dnaQuality = 100;

    public DinosaurEntity(EntityType<? extends DinosaurEntity> type, Level level) {
        super(type, level);
        inventory = new DinosaurInventory(this);
        totalAgeTicks = DinosaurProfile.of(species()).growthTicks();
        if (!level.isClientSide) setGeneticAttributes(DinosaurAttributes.random(random));
        String bases = "ACGT";
        StringBuilder genome = new StringBuilder(10);
        for (int i = 0; i < 10; i++) genome.append(bases.charAt(random.nextInt(4)));
        genetics = genome.toString();
        energy = maxEnergy();
        water = maxEnergy();
        breedCooldown = 1000 + random.nextInt(1000);
        if (isAquatic()) moveControl = new SmoothSwimmingMoveControl(this, 85, 10, .1F, .5F, false);
    }

    public Species species() {
        ResourceLocation id = Registry.ENTITY_TYPE.getKey(getType());
        if (id == null) throw new IllegalStateException("Dinosaur entity type is not registered");
        return Species.valueOf(id.getPath().toUpperCase(java.util.Locale.ROOT));
    }
    public boolean isAquatic() { return species() == Species.COELACANTH; }
    public boolean isImprintable() {
        return switch (species()) {
            case VELOCIRAPTOR, MICRORAPTOR, MUSSAURUS, DILOPHOSAURUS, GALLIMIMUS, PARASAUROLOPHUS, TRICERATOPS -> true;
            default -> false;
        };
    }
    @Nullable public UUID getOwnerUUID() { return entityData.get(OWNER).orElse(null); }
    /** Imprinting is limited to the species that support it in the upstream mod. */
    public void setOwner(@Nullable UUID owner) {
        if (owner == null || isImprintable()) {
            entityData.set(OWNER, Optional.ofNullable(owner));
            if (owner == null) entityData.set(ORDER, Order.WANDER.ordinal());
        }
    }
    public enum Order { WANDER, FOLLOW, SIT }
    public enum CombatAnimation { NONE, SPIT, PREPARE_LEAP, LEAP, LEAP_LAND }
    public CombatAnimation combatAnimation() {
        int value = entityData.get(COMBAT_ANIMATION);
        return value >= 0 && value < CombatAnimation.values().length ? CombatAnimation.values()[value] : CombatAnimation.NONE;
    }
    public int combatAnimationStart() { return combatAnimationStart; }
    public boolean isCombatPoseInterrupted() { return entityData.get(COMBAT_POSE_INTERRUPTED); }
    public boolean isAnimationRunning() { return entityData.get(RUNNING_ANIMATION); }
    void setAnimationRunning(boolean running) { entityData.set(RUNNING_ANIMATION, running); }
    void setCombatAnimation(CombatAnimation animation) {
        if (!level.isClientSide) {
            if (animation != CombatAnimation.NONE) {
                finishInjuryAnimation();
                finishAttackAnimation();
            }
            entityData.set(COMBAT_POSE_INTERRUPTED, false);
            entityData.set(COMBAT_ANIMATION, animation.ordinal());
        }
    }
    public String poseAnimation() {
        String value = entityData.get(POSE_ANIMATION);
        return value.startsWith("!") ? value.substring(1) : value;
    }
    public boolean isForcedPose() { return entityData.get(POSE_ANIMATION).startsWith("!"); }
    public int poseAnimationStart() { return poseAnimationStart; }
    public boolean isBirthing() { return entityData.get(BIRTH_ANIMATING); }
    public boolean isInjuryAnimating() { return entityData.get(INJURY_ANIMATING); }
    public boolean isAttackAnimating() { return entityData.get(ATTACK_ANIMATING); }
    /** Use the shared world clock so late-tracking clients seek to the current attack frame. */
    public int attackAnimationStart() {
        return tickCount - ((int) level.getGameTime() - entityData.get(ATTACK_STARTED));
    }
    public float attackAnimationTime(float age) {
        return Math.max(0, (float) ((int) level.getGameTime() - entityData.get(ATTACK_STARTED)) + age - tickCount);
    }
    /** The start is expressed in local entity ticks, for the pose engine's action token. */
    public int injuryAnimationStart() {
        return tickCount - ((int) level.getGameTime() - entityData.get(INJURY_STARTED));
    }
    public float injuryAnimationTime(float age) {
        return Math.max(0, (float) ((int) level.getGameTime() - entityData.get(INJURY_STARTED)) + age - tickCount);
    }
    /** Birth pose time uses the shared world clock so resumed and newly tracked entities stay in sync. */
    public float birthAnimationTime(float age) {
        return Math.max(0, (float) ((int) level.getGameTime() - entityData.get(BIRTH_STARTED)) + age - tickCount);
    }
    /** Play an original species pose once, with the imported frame length. */
    public boolean playPose(String animation, int variant, boolean force) {
        if (level.isClientSide || animation == null || variant < 0
                || (!force && (isForcedPose() || isBirthing() || isInjuryAnimating() || isAttackAnimating()))) return false;
        String name = animation.toUpperCase(java.util.Locale.ROOT);
        if (variant != 0) name += ":" + variant;
        int duration = DinosaurAnimationData.duration(species(), name);
        if (duration <= 0) return false;
        if (force) {
            finishInjuryAnimation();
            finishAttackAnimation();
        }
        setNaturalRest(false);
        entityData.set(POSE_ANIMATION, (force ? "!" : "") + name);
        if (force) entityData.set(POSE_SERIAL, entityData.get(POSE_SERIAL) + 1);
        poseTicks = duration;
        if (force) DinosaurPresentation.playForcedPose(this, animation, variant);
        return true;
    }
    public Order getOrder() {
        int value = entityData.get(ORDER);
        return value >= 0 && value < Order.values().length ? Order.values()[value] : Order.WANDER;
    }
    void setOrder(Order order) {
        if (level.isClientSide || getOwnerUUID() == null || order == null) return;
        entityData.set(ORDER, order.ordinal());
        if (order != Order.WANDER) setNaturalRest(false);
        if (order == Order.SIT) {
            finishAttackAnimation();
            getNavigation().stop();
            setTarget(null);
            setClimbingTree(false);
            setDeltaMovement(0, getDeltaMovement().y, 0);
        }
    }
    public boolean isOrderedToSit() { return getOrder() == Order.SIT; }
    boolean defendsOwner() {
        return switch (species()) {
            case VELOCIRAPTOR, DILOPHOSAURUS, MICRORAPTOR, TRICERATOPS -> true;
            default -> false;
        };
    }
    boolean isOwnerDefenseTarget(LivingEntity target) {
        Player owner = getOwnerUUID() == null ? null : level.getPlayerByUUID(getOwnerUUID());
        return defendsOwner() && getOrder() == Order.FOLLOW && owner != null
                && (owner.getLastHurtByMob() == target || owner.getLastHurtMob() == target);
    }
    @Override public boolean canAttack(net.minecraft.world.entity.LivingEntity target) {
        return !target.getUUID().equals(getOwnerUUID()) && super.canAttack(target);
    }
    @Override public boolean doHurtTarget(Entity target) {
        if (!level.isClientSide) {
            finishInjuryAnimation();
            if (isAlive() && !isCarcass() && !isDinosaurSleeping() && !isOrderedToSit()
                    && !isBirthing() && !isForcedPose() && combatAnimation() == CombatAnimation.NONE
                    && !(target instanceof DinosaurEntity dinosaur && dinosaur.isCarcass())) startAttackAnimation();
        }
        boolean hit = super.doHurtTarget(target);
        if (hit && !level.isClientSide && species() == Species.DILOPHOSAURUS && target instanceof LivingEntity victim)
            victim.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.POISON, 300, 1, false, false));
        return hit;
    }
    void spitVenom(LivingEntity target) {
        if (level.isClientSide || !target.isAlive() || target instanceof Player player && player.isCreative()) return;
        VenomEntity venom = new VenomEntity(level, this);
        double dx = target.getX() - venom.getX(), dz = target.getZ() - venom.getZ();
        double dy = target.getEyeY() - 1.1 - venom.getY();
        venom.shoot(dx, dy + Math.sqrt(dx * dx + dz * dz) * .2, dz, 1.5f, 0);
        level.addFreshEntity(venom);
    }
    /** Original species-specific prey classes; same-species herd members are never prey. */
    boolean canHunt(LivingEntity prey) {
        Species hunter = species();
        if (prey instanceof DinosaurEntity dinosaur) {
            return switch (hunter) {
                case VELOCIRAPTOR -> switch (dinosaur.species()) {
                    case DILOPHOSAURUS, GALLIMIMUS, PARASAUROLOPHUS, TRICERATOPS, MICRORAPTOR, MUSSAURUS -> true;
                    default -> false;
                };
                case DILOPHOSAURUS, TYRANNOSAURUS -> switch (dinosaur.species()) {
                    case GALLIMIMUS, PARASAUROLOPHUS, TRICERATOPS, VELOCIRAPTOR, MUSSAURUS -> true;
                    case DILOPHOSAURUS -> hunter == Species.TYRANNOSAURUS;
                    default -> false;
                };
                default -> false;
            };
        }
        return switch (hunter) {
            case COELACANTH -> prey instanceof Squid;
            case MICRORAPTOR -> prey instanceof Chicken || prey instanceof Rabbit;
            case VELOCIRAPTOR, DILOPHOSAURUS -> prey instanceof Player || prey instanceof Villager || prey instanceof Animal;
            case TYRANNOSAURUS -> prey instanceof Player || prey instanceof Villager || prey instanceof Animal || prey instanceof Monster;
            default -> false;
        };
    }
    DinosaurHerd herdOrNull() { return herd; }
    void setHerd(@Nullable DinosaurHerd herd) { this.herd = herd; }
    DinosaurHerd herd() { return herd == null ? new DinosaurHerd(this) : herd; }
    @Override public void setTarget(@Nullable LivingEntity target) {
        if (target != null && herd != null && herd.isFleeing()) return;
        super.setTarget(target);
    }
    public boolean isMale() { return entityData.get(MALE); }
    public void setMale(boolean male) { entityData.set(MALE, male); }
    public String getGenetics() { return genetics; }
    public int getDNAQuality() { return dnaQuality; }
    public DinosaurAttributes geneticAttributes() { return geneticAttributes; }
    public float geneticScale() { return entityData.get(GENETIC_SCALE); }
    /** Server-side age in growth-enabled loaded ticks, including time spent as an adult. */
    public long totalAgeTicks() { return totalAgeTicks; }
    public void setGeneticAttributes(DinosaurAttributes attributes) { applyGeneticAttributes(attributes, true); }
    private void applyGeneticAttributes(DinosaurAttributes attributes, boolean adjustHealth) {
        geneticAttributes = java.util.Objects.requireNonNull(attributes);
        entityData.set(GENETIC_SCALE, attributes.scale());
        if (!level.isClientSide) updateGrowth(adjustHealth);
    }
    public void setGenetics(String genome) {
        if (genome == null || !genome.matches("[ACGT]{10}")) throw new IllegalArgumentException("Genome must contain ten A/C/G/T bases");
        genetics = genome;
    }
    public void setDNAQuality(int quality) { dnaQuality = Mth.clamp(quality, 0, 100); }
    public float growth() { return entityData.get(GROWTH) / 1000f; }

    @Override protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(OWNER, Optional.empty());
        entityData.define(ORDER, Order.WANDER.ordinal());
        entityData.define(MALE, false);
        entityData.define(GROWTH, 1000);
        entityData.define(GENETIC_SCALE, 1.0F);
        entityData.define(HUNGRY, false);
        entityData.define(THIRSTY, false);
        entityData.define(TRANQUILIZED, false);
        entityData.define(NATURAL_SLEEP, false);
        entityData.define(NATURAL_REST, false);
        entityData.define(GLIDING, false);
        entityData.define(CLIMBING, false);
        entityData.define(CARCASS, false);
        entityData.define(RUNNING_ANIMATION, false);
        entityData.define(COMBAT_ANIMATION, CombatAnimation.NONE.ordinal());
        entityData.define(COMBAT_POSE_INTERRUPTED, false);
        entityData.define(POSE_ANIMATION, "");
        entityData.define(POSE_SERIAL, 0);
        entityData.define(BIRTH_STARTED, 0);
        entityData.define(BIRTH_ANIMATING, false);
        entityData.define(INJURY_ANIMATING, false);
        entityData.define(INJURY_STARTED, 0);
        entityData.define(ATTACK_ANIMATING, false);
        entityData.define(ATTACK_STARTED, 0);
    }
    @Override public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (GROWTH.equals(key) || GENETIC_SCALE.equals(key)) refreshDimensions();
        if (CARCASS.equals(key) && isCarcass()) carcassAnimationStart = tickCount;
        if (COMBAT_ANIMATION.equals(key)) combatAnimationStart = tickCount;
        if (POSE_ANIMATION.equals(key) || POSE_SERIAL.equals(key)) poseAnimationStart = tickCount;
    }
    @Override public EntityDimensions getDimensions(Pose pose) {
        DinosaurProfile p = DinosaurProfile.of(species());
        float t = growth();
        return EntityDimensions.scalable(Mth.lerp(t, p.babyWidth(), p.adultWidth()) * geneticScale(),
                Mth.lerp(t, p.babyHeight(), p.adultHeight()) * geneticScale());
    }
    @Override public void setBaby(boolean baby) {
        setAge(baby ? -DinosaurProfile.of(species()).growthTicks() : 0);
        totalAgeTicks = baby ? 0 : Math.max(totalAgeTicks, DinosaurProfile.of(species()).growthTicks());
        if (!level.isClientSide) updateGrowth(true);
    }
    private void updateGrowth(boolean adjustHealth) {
        DinosaurProfile p = DinosaurProfile.of(species());
        int value = getAge() < 0 ? Mth.clamp(1000 + (int) (1000L * getAge() / p.growthTicks()), 0, 1000) : 1000;
        float oldHealthFraction = getMaxHealth() > 0 ? getHealth() / getMaxHealth() : 1;
        entityData.set(GROWTH, value);
        float t = value / 1000f;
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(Mth.lerp(t, p.babyHealth(), p.adultHealth()) * geneticAttributes.health());
        getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(Mth.lerp(t, p.babyDamage(), p.adultDamage()) * geneticAttributes.damage());
        getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(p.speed() * geneticAttributes.speed());
        if (adjustHealth) setHealth(Math.min(getMaxHealth(), oldHealthFraction * getMaxHealth()));
    }
    @Override public void aiStep() {
        int ageBeforeTick = getAge();
        super.aiStep();
        if (!level.isClientSide && !growthEnabled()
                && ageBeforeTick < 0 && getAge() != ageBeforeTick) setAge(ageBeforeTick);
        if (isCarcass()) {
            // Vanilla ages babies in aiStep; a carcass must retain the age it died at.
            if (!level.isClientSide) {
                if (ageBeforeTick != getAge()) setAge(ageBeforeTick);
                setDeltaMovement(0, getDeltaMovement().y, 0);
                if (++carcassDecayTicks >= 1000) {
                    carcassDecayTicks = 0;
                    harvestCarcass(DamageSource.GENERIC);
                }
            }
            return;
        }
        if (!level.isClientSide) {
            DinosaurPresentation.serverTick(this);
            if (isAlive() && growthEnabled() && totalAgeTicks < Long.MAX_VALUE) totalAgeTicks++;
            if (injuryTicks > 0 && --injuryTicks == 0) finishInjuryAnimation();
            if (attackTicks > 0 && --attackTicks == 0) finishAttackAnimation();
            if (poseTicks > 0 && --poseTicks == 0) entityData.set(POSE_ANIMATION, "");
            if (wireTicks > 0) wireTicks--;
            if (disableHerdingTicks > 0) disableHerdingTicks--;
            if (tranquilizerTicks > 0 && --tranquilizerTicks == 0) entityData.set(TRANQUILIZED, false);
            if (ticksUntilDeath > 0 && --ticksUntilDeath == 0) {
                setHealth(0);
                die(DamageSource.MAGIC);
                return;
            }
            if (isStarving() || isDehydrated()) {
                if (herd != null) herd.remove(this);
            } else {
                DinosaurHerd group = herd();
                if (tickCount % 20 == 0 && (group.leader() == this || group.leader() == null || group.leader().isRemoved())) group.refresh();
            }
            familyBrain.tick();
            if (stayAwakeTicks > 0) stayAwakeTicks--;
            if (tickCount % 20 == 0 && isNaturallySleeping() && !shouldNaturallySleep()) setNaturalSleep(false);
            if (species() == Species.MICRORAPTOR) updateGlidingState();
            if (tickCount % 100 == 0) updateGrowth(true);
            if (breedCooldown > 0) breedCooldown--;
            if (gestationTicks > 0) gestationTicks--;
            tickBirth();
            if (metabolismEnabled()) {
                energy = Math.max(0, energy - 1);
                water = Math.max(0, water - 1);
                if (isInWaterRainOrBubble()) {
                    if (isThirsty()) playPose("DRINKING", 0, false);
                    water = maxEnergy();
                }
                if (digestingFood > 0) {
                    energy = Math.min(maxEnergy(), energy + 10);
                    digestingFood--;
                }
                if (tickCount % 40 == 0) {
                    if (isStarving()) hurt(DamageSource.STARVE, 1);
                    if (isDehydrated()) hurt(DamageSource.STARVE, 1);
                }
            }
            if (tickCount % 20 == 0) {
                entityData.set(HUNGRY, hungryOnServer());
                entityData.set(THIRSTY, thirstyOnServer());
            }
            updateNaturalRest();
        }
    }
    @Override public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, net.minecraft.world.DifficultyInstance difficulty,
            MobSpawnType reason, @Nullable SpawnGroupData data, @Nullable CompoundTag tag) {
        setMale(random.nextBoolean());
        SpawnGroupData result = super.finalizeSpawn(level, difficulty, reason, data, tag);
        // The original mod starts wild dinosaurs at full size, even in a spawn group.
        if (reason == MobSpawnType.NATURAL || reason == MobSpawnType.CHUNK_GENERATION) setBaby(false);
        updateGrowth(true);
        return result;
    }
    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        // Mob writes NoAI through our overridden isNoAi(); don't persist temporary sedation as a permanent flag.
        if (super.isNoAi()) tag.putBoolean("NoAI", true);
        else tag.remove("NoAI");
        tag.putBoolean("Male", isMale());
        if (getOwnerUUID() != null) tag.putUUID("Owner", getOwnerUUID());
        tag.putInt("Order", getOrder().ordinal());
        tag.putString("Genetics", genetics);
        tag.putInt("DNAQuality", dnaQuality);
        CompoundTag inherited = new CompoundTag();
        geneticAttributes.write(inherited);
        tag.put("GeneticAttributes", inherited);
        tag.putInt("GrowthScaleVersion", 2);
        tag.putLong("TotalAgeTicks", totalAgeTicks);
        tag.putInt("Energy", energy);
        tag.putInt("Water", water);
        tag.putInt("DigestingFood", digestingFood);
        tag.putInt("BreedCooldown", breedCooldown);
        tag.putInt("GestationTicks", gestationTicks);
        if (birthElapsed >= 0) tag.putInt("BirthElapsed", birthElapsed);
        else tag.remove("BirthElapsed");
        tag.putInt("BirthRetryTicks", birthRetryTicks);
        tag.putInt("InjuryTicks", injuryTicks);
        tag.putInt("AttackTicks", attackTicks);
        tag.putInt("TranquilizerTicks", tranquilizerTicks);
        tag.putInt("TicksUntilDeath", ticksUntilDeath);
        tag.putBoolean("NaturalSleeping", isNaturallySleeping());
        tag.putBoolean("NaturalResting", isNaturallyResting());
        tag.putInt("StayAwakeTicks", stayAwakeTicks);
        tag.putBoolean("IsCarcass", isCarcass());
        tag.putInt("CarcassHealth", carcassHealth);
        tag.putInt("CarcassDecayTicks", carcassDecayTicks);
        tag.putBoolean("CarcassLootExhausted", carcassLootExhausted);
        tag.put("Embryos", embryos.copy());
        familyBrain.save(tag);
        inventory.save(tag);
    }
    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (!tag.contains("GrowthScaleVersion") && getAge() < 0) {
            // Earlier port builds used one eighth of the original growth duration.
            int oldDuration = DinosaurProfile.of(species()).growthTicks() / 8;
            int oldAge = Mth.clamp(getAge(), -oldDuration, 0);
            setAge(oldAge * 8);
        }
        totalAgeTicks = tag.contains("TotalAgeTicks", Tag.TAG_ANY_NUMERIC) ? Math.max(0, tag.getLong("TotalAgeTicks"))
                : Math.max(0L, (long) DinosaurProfile.of(species()).growthTicks() + Math.min(0, getAge()));
        applyGeneticAttributes(DinosaurAttributes.read(tag.getCompound("GeneticAttributes")).orElse(DEFAULT_ATTRIBUTES), false);
        setMale(tag.getBoolean("Male"));
        setOwner(tag.hasUUID("Owner") ? tag.getUUID("Owner") : null);
        int order = tag.getInt("Order");
        entityData.set(ORDER, getOwnerUUID() != null && order >= 0 && order < Order.values().length ? order : Order.WANDER.ordinal());
        if (tag.getString("Genetics").matches("[ACGT]{10}")) genetics = tag.getString("Genetics");
        if (tag.contains("DNAQuality")) setDNAQuality(tag.getInt("DNAQuality"));
        if (tag.contains("Energy")) energy = Mth.clamp(tag.getInt("Energy"), 0, maxEnergy());
        water = tag.contains("Water") ? Mth.clamp(tag.getInt("Water"), 0, maxEnergy()) : maxEnergy();
        if (tag.contains("DigestingFood")) digestingFood = Mth.clamp(tag.getInt("DigestingFood"), 0, 3000);
        entityData.set(HUNGRY, hungryOnServer());
        entityData.set(THIRSTY, thirstyOnServer());
        if (tag.contains("BreedCooldown")) breedCooldown = Math.max(0, tag.getInt("BreedCooldown"));
        gestationTicks = Math.max(0, tag.getInt("GestationTicks"));
        int birthDuration = DinosaurAnimationData.duration(species(), birthPose());
        birthElapsed = tag.contains("BirthElapsed") && gestationTicks == 0 && birthDuration > 0
                ? Mth.clamp(tag.getInt("BirthElapsed"), 0, birthDuration - 1) : -1;
        birthRetryTicks = Math.max(0, tag.getInt("BirthRetryTicks"));
        entityData.set(BIRTH_ANIMATING, false);
        tranquilizerTicks = Math.max(0, tag.getInt("TranquilizerTicks"));
        ticksUntilDeath = Math.max(0, tag.getInt("TicksUntilDeath"));
        entityData.set(TRANQUILIZED, tranquilizerTicks > 0);
        entityData.set(NATURAL_SLEEP, tag.getBoolean("NaturalSleeping") && !isAquatic());
        stayAwakeTicks = Math.max(0, tag.getInt("StayAwakeTicks"));
        carcassHealth = Mth.clamp(tag.getInt("CarcassHealth"), 0, 10);
        carcassDecayTicks = Mth.clamp(tag.getInt("CarcassDecayTicks"), 0, 999);
        carcassLootExhausted = tag.getBoolean("CarcassLootExhausted");
        entityData.set(CARCASS, tag.getBoolean("IsCarcass"));
        entityData.set(NATURAL_REST, tag.getBoolean("NaturalResting") && !isAquatic()
                && !isCarcass() && !isDinosaurSleeping() && getOrder() == Order.WANDER);
        int injuryDuration = DinosaurAnimationData.duration(species(), "INJURED");
        injuryTicks = isCarcass() || !isAlive() ? 0 : Mth.clamp(tag.getInt("InjuryTicks"), 0, injuryDuration);
        entityData.set(INJURY_STARTED, (int) level.getGameTime() - (injuryDuration - injuryTicks));
        entityData.set(INJURY_ANIMATING, injuryTicks > 0);
        int attackDuration = DinosaurAnimationData.duration(species(), "ATTACKING");
        attackTicks = isCarcass() || !isAlive() || isDinosaurSleeping() || isOrderedToSit() || injuryTicks > 0
                ? 0 : Mth.clamp(tag.getInt("AttackTicks"), 0, attackDuration);
        entityData.set(ATTACK_STARTED, (int) level.getGameTime() - (attackDuration - attackTicks));
        entityData.set(ATTACK_ANIMATING, attackTicks > 0);
        if (attackTicks > 0) entityData.set(NATURAL_REST, false);
        embryos = tag.getList("Embryos", Tag.TAG_COMPOUND).copy();
        familyBrain.load(tag);
        inventory.load(tag);
        if (!level.isClientSide) {
            updateGrowth(false);
            // Recalculated genetics or growth can lower the max after vanilla loads health.
            setHealth(getHealth());
        }
    }
    @Override protected void registerGoals() {
        DinosaurProfile p = DinosaurProfile.of(species());
        goalSelector.addGoal(0, new EscapeDinosaurFenceGoal(this));
        goalSelector.addGoal(1, new FleeHerdThreatGoal(this));
        goalSelector.addGoal(5, new StayWithDinosaurFamilyGoal(this));
        if (isAquatic()) {
            goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.1, true));
            goalSelector.addGoal(5, new RandomSwimmingGoal(this, 1.0, 40));
        } else {
            goalSelector.addGoal(0, new FloatGoal(this));
            if (p.timid()) goalSelector.addGoal(1, new PanicGoal(this, 1.35));
            if (!p.timid() || defendsOwner()) {
                if (species() == Species.DILOPHOSAURUS) {
                    goalSelector.addGoal(2, new DilophosaurusBiteGoal(this));
                    goalSelector.addGoal(2, new DilophosaurusSpitGoal(this));
                } else goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.1, true));
                targetSelector.addGoal(1, new HurtByTargetGoal(this));
            } else goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.1, true));
            if (species() == Species.VELOCIRAPTOR || species() == Species.MICRORAPTOR || species() == Species.GALLIMIMUS)
                goalSelector.addGoal(1, new DinosaurLeapGoal(this));
            if (p.carnivore()) goalSelector.addGoal(2, new ScavengeCarcassGoal(this));
            goalSelector.addGoal(3, new FindFeederGoal(this));
            goalSelector.addGoal(3, new DrinkFromWaterGoal(this));
            if (!p.carnivore()) goalSelector.addGoal(3, new GrazePlantsGoal(this));
            goalSelector.addGoal(3, new DinosaurSleepGoal(this));
            if (species() == Species.MICRORAPTOR) goalSelector.addGoal(4, new RaptorClimbGoal(this));
            goalSelector.addGoal(5, new FollowDinosaurHerdGoal(this));
            goalSelector.addGoal(5, new RandomStrollGoal(this, 1.0));
            goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8));
        }
        goalSelector.addGoal(3, new FindDroppedFoodGoal(this));
        if (isImprintable()) {
            goalSelector.addGoal(4, new FollowDinosaurOwnerGoal(this));
            if (defendsOwner()) {
                targetSelector.addGoal(0, new DefendDinosaurOwnerGoal(this, true));
                targetSelector.addGoal(0, new DefendDinosaurOwnerGoal(this, false));
            }
        }
        goalSelector.addGoal(4, new TemptJuvenileDinosaurGoal(this));
        if (p.carnivore() || isAquatic()) targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this,
                LivingEntity.class, 20, true, false, target -> growth() > .5f && (herd == null || !herd.isFleeing())
                        && (getOwnerUUID() == null || isStarving())
                        && (!JurassicConfig.HUNT_WHEN_HUNGRY.get() || needsFood()) && canHunt(target)));
        goalSelector.addGoal(4, new DinosaurMateGoal(this));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
    }
    @Override public boolean isFood(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (stack.is(DINOSAUR_FISH) || stack.is(BugRegistry.KRILL.get()) || stack.is(BugRegistry.PLANKTON.get())) return isAquatic();
        if (stack.is(BugRegistry.CRICKETS.get()) || stack.is(BugRegistry.COCKROACHES.get()) || stack.is(BugRegistry.MEALWORM_BEETLES.get()))
            return species() == Species.MICRORAPTOR || (growth() < .25f && (DinosaurProfile.of(species()).carnivore() || species() == Species.GALLIMIMUS));
        if (isAquatic()) return false;
        var food = stack.getItem().getFoodProperties();
        if (DinosaurProfile.of(species()).carnivore())
            return stack.is(FEEDER_MEAT) || (food != null && food.isMeat());
        return stack.is(FEEDER_PLANTS) || stack.is(ItemTags.LEAVES) || stack.is(ItemTags.SAPLINGS)
                || stack.is(Items.WHEAT_SEEDS) || stack.is(Items.BEETROOT_SEEDS)
                || stack.is(Items.MELON_SEEDS) || stack.is(Items.PUMPKIN_SEEDS)
                || (food != null && !food.isMeat() && !stack.is(FEEDER_MEAT));
    }
    private int maxEnergy() { return (int)(DinosaurProfile.of(species()).adultHealth() / 10 * 24000); }
    public int energy() { return energy; }
    public int maximumEnergy() { return maxEnergy(); }
    public int water() { return water; }
    public int maximumWater() { return maxEnergy(); }
    boolean metabolismEnabled() { return level.getGameRules().getBoolean(DinosaurGameRules.METABOLISM); }
    boolean growthEnabled() { return level.getGameRules().getBoolean(DinosaurGameRules.GROWTH); }
    boolean isStarving() { return energy < 200 && digestingFood <= 50; }
    boolean isDehydrated() { return water < 50; }
    public void onFenceShock() {
        if (!level.isClientSide && isAlive() && wireTicks < 2) {
            setNaturalRest(false);
            wireTicks++;
            disableHerdingTicks = 200;
        }
    }
    int wireTicks() { return wireTicks; }
    int disableHerdingTicks() { return disableHerdingTicks; }
    private boolean thirstyOnServer() { return water < maxEnergy() * .5; }
    public boolean isThirsty() { return level.isClientSide ? entityData.get(THIRSTY) : thirstyOnServer(); }
    void drink(int amount) {
        if (!level.isClientSide && amount > 0) {
            water = (int) Math.min(maxEnergy(), (long) water + amount);
            entityData.set(THIRSTY, thirstyOnServer());
            playPose("DRINKING", 0, false);
        }
    }
    public void reduceEnergy(int amount) {
        if (level.isClientSide || amount <= 0) return;
        energy = Math.max(0, energy - amount);
        entityData.set(HUNGRY, hungryOnServer());
    }
    private boolean hungryOnServer() { return energy < maxEnergy() * .8 && digestingFood + 500 < 3000; }
    public boolean needsFood() { return level.isClientSide ? entityData.get(HUNGRY) : hungryOnServer(); }
    /** Feed one item without changing the supplied stack; caller removes it after success. */
    public boolean feed(ItemStack stack) {
        if (level.isClientSide || !isAlive() || !needsFood() || !isFood(stack)) return false;
        var food = stack.getItem().getFoodProperties();
        int amount = food == null ? 500 : Math.max(500, food.getNutrition() * 650);
        eatNutrition(amount);
        return true;
    }
    void eatNutrition(int amount) {
        energy = Math.min(maxEnergy(), energy + amount / 10);
        digestingFood = Math.min(3000, digestingFood + amount);
        heal(10.0f);
        entityData.set(HUNGRY, hungryOnServer());
        playPose("EATING", 0, false);
    }
    @Override public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (hand == InteractionHand.MAIN_HAND && player.isShiftKeyDown() && species() == Species.MICRORAPTOR
                && isAlive() && isOrderedToSit() && player.getUUID().equals(getOwnerUUID()) && player.getPassengers().size() < 2) {
            if (!level.isClientSide) startRiding(player, true);
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (hand == InteractionHand.MAIN_HAND && player.isShiftKeyDown() && stack.isEmpty() && isAlive()) {
            if (getOwnerUUID() == null || !getOwnerUUID().equals(player.getUUID())) {
                if (!level.isClientSide) player.displayClientMessage(Component.translatable("message.jurassicraft.not_owned"), true);
            } else if (growth() <= .75f) {
                if (!level.isClientSide) player.displayClientMessage(Component.translatable("message.jurassicraft.too_young"), true);
            } else if (player instanceof ServerPlayer server && inventory.stillValid(player)) {
                server.openMenu(new SimpleMenuProvider((id, playerInventory, who) ->
                        inventory.stillValid(who) ? inventory.menu(id, playerInventory) : null, getDisplayName()));
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (hand == InteractionHand.MAIN_HAND && !player.isShiftKeyDown() && stack.isEmpty() && isAlive()) {
            if (getOwnerUUID() != null && getOwnerUUID().equals(player.getUUID())) {
                if (player instanceof ServerPlayer server) {
                    server.openMenu(new ExtendedScreenHandlerFactory() {
                        @Override public Component getDisplayName() { return Component.translatable("gui.jurassicraft.select_order"); }
                        @Override public net.minecraft.world.inventory.AbstractContainerMenu createMenu(int id,
                                net.minecraft.world.entity.player.Inventory inventory, Player who) {
                            return new DinosaurOrderMenu(id, inventory, DinosaurEntity.this);
                        }
                        @Override public void writeScreenOpeningData(ServerPlayer who, FriendlyByteBuf buffer) {
                            buffer.writeVarInt(getId());
                        }
                    });
                }
            } else if (!level.isClientSide) player.displayClientMessage(Component.translatable("message.jurassicraft.not_owned"), true);
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (isAlive() && stack.is(Items.POTION) && (isThirsty() || needsFood())) {
            if (!level.isClientSide) {
                drink(1000);
                if (!player.getAbilities().instabuild) {
                    stack.shrink(1);
                    ItemStack bottle = new ItemStack(Items.GLASS_BOTTLE);
                    if (!player.addItem(bottle)) player.drop(bottle, false);
                }
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (isFood(stack) && needsFood()) {
            if (!level.isClientSide && feed(stack)) {
                if (!player.getAbilities().instabuild) stack.shrink(1);
                playSound(net.minecraft.sounds.SoundEvents.GENERIC_EAT, 1, 1);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return InteractionResult.PASS;
    }
    @Override public void rideTick() {
        super.rideTick();
        if (species() != Species.MICRORAPTOR || !(getVehicle() instanceof Player player)) return;
        if (!player.isAlive() || player.isFallFlying()) {
            if (!level.isClientSide) stopRiding();
            return;
        }
        int seat = player.getPassengers().indexOf(this);
        if (seat < 0) return;
        float angle = (float) Math.toRadians(player.yBodyRot + (seat == 1 ? -90 : 90));
        setPos(player.getX() + .35 * Math.sin(Math.PI + angle), player.getY() + (player.isShiftKeyDown() ? 1.2 : 1.38),
                player.getZ() + .35 * Math.cos(angle + Math.PI));
        setYRot(player.getYRot());
        yBodyRot = player.yBodyRot;
        yHeadRot = player.getYHeadRot();
    }
    /** True when healthy and sufficiently mature to reproduce. */
    public boolean readyToBreed() {
        return isAlive() && !isDinosaurSleeping() && growth() > .75f && breedCooldown <= 0 && embryos.isEmpty()
                && (herd == null || !herd.hasEnemies()) && getHealth() >= getMaxHealth() && getTarget() == null && !needsFood() && !isThirsty();
    }
    public boolean isPregnant() { return !embryos.isEmpty(); }
    public boolean isTranquilized() { return entityData.get(TRANQUILIZED); }
    public int tranquilizerTicks() { return tranquilizerTicks; }
    public boolean isNaturallySleeping() { return entityData.get(NATURAL_SLEEP); }
    public boolean isNaturallyResting() { return entityData.get(NATURAL_REST); }
    public boolean isDinosaurSleeping() { return isNaturallySleeping() || isTranquilized(); }
    public boolean isCarcass() { return entityData.get(CARCASS); }
    public int carcassAnimationStart() { return carcassAnimationStart; }
    public boolean isGliding() { return entityData.get(GLIDING); }
    public boolean isClimbingTree() { return entityData.get(CLIMBING); }
    void setClimbingTree(boolean climbing) { if (!level.isClientSide) entityData.set(CLIMBING, climbing); }
    void updateGlidingState() {
        if (isOnGround() || isInWaterOrBubble() || isInLava() || isPassenger() || isDinosaurSleeping() || isClimbingTree()) {
            airborneTicks = 0;
            entityData.set(GLIDING, false);
        } else if (++airborneTicks > 4) entityData.set(GLIDING, true);
    }
    @Override public boolean isAlive() { return !isCarcass() && super.isAlive(); }
    @Override public boolean isNoAi() { return super.isNoAi() || isDinosaurSleeping() || isCarcass() || isOrderedToSit() || isBirthing(); }
    @Override public boolean isPushable() { return !isCarcass() && super.isPushable(); }
    boolean canRestNaturally() {
        return isAlive() && !isAquatic() && !isDinosaurSleeping() && !super.isNoAi()
                && getOrder() == Order.WANDER && isOnGround() && !isPassenger()
                && !isInWaterOrBubble() && !isInLava() && !needsFood() && !isThirsty()
                && getTarget() == null && getNavigation().isDone()
                && (herd == null || !herd.isBusyForRest()) && !isBirthing() && !isInjuryAnimating() && !isAttackAnimating()
                && combatAnimation() == CombatAnimation.NONE && poseAnimation().isEmpty();
    }
    boolean beginNaturalRest() {
        if (level.isClientSide || !canRestNaturally()) return false;
        setNaturalRest(true);
        return true;
    }
    void updateNaturalRest() {
        if (level.isClientSide) return;
        if (isNaturallyResting()) {
            if (!canRestNaturally()) setNaturalRest(false);
        } else if (canRestNaturally() && random.nextInt(800) == 0) setNaturalRest(true);
    }
    private void setNaturalRest(boolean resting) {
        if (level.isClientSide) return;
        entityData.set(NATURAL_REST, resting);
        if (resting) {
            getNavigation().stop();
            setDeltaMovement(0, getDeltaMovement().y, 0);
        }
    }
    /** Time windows match upstream's wake time and sixteen-hour active period. */
    static boolean isSleepTime(Species species, long dayTime) {
        if (species == Species.COELACANTH) return false;
        int wake = species == Species.DILOPHOSAURUS ? 6480 : 0;
        return Math.floorMod(dayTime - wake, 24000) > 16000;
    }
    boolean shouldNaturallySleep() {
        if (!isSleepTime(species(), level.getDayTime()) || stayAwakeTicks > 0 || !isAlive()
                || isInWaterOrBubble() || isInLava() || isStarving() || isDehydrated() || getTarget() != null
                || herd != null && herd.hasEnemies()) return false;
        return level.getEntitiesOfClass(Mob.class, getBoundingBox().inflate(10, 5, 10),
                mob -> mob != this && mob.isAlive() && (mob == getLastHurtMob() || mob.getTarget() == this
                        || herd != null && mob.getTarget() instanceof DinosaurEntity d && d.herdOrNull() == herd
                        || mob instanceof DinosaurEntity d && d.canHunt(this))).isEmpty();
    }
    void setNaturalSleep(boolean sleeping) {
        if (level.isClientSide) return;
        entityData.set(NATURAL_SLEEP, sleeping);
        if (sleeping) {
            finishAttackAnimation();
            setNaturalRest(false);
            getNavigation().stop();
            setClimbingTree(false);
            setDeltaMovement(0, getDeltaMovement().y, 0);
        }
    }
    @Override public boolean hurt(DamageSource source, float amount) {
        if (isCarcass()) {
            if (level.isClientSide || amount <= 0 || source == DamageSource.DROWN || isRemoved()) return false;
            if (source.isBypassInvul()) {
                finishCarcass(source);
                return true;
            }
            if (invulnerableTime > 10 || isInvulnerableTo(source)) return false;
            invulnerableTime = 20;
            harvestCarcass(source);
            return true;
        }
        int previousInvulnerability = invulnerableTime;
        boolean hurt = super.hurt(source, amount);
        if (hurt && !level.isClientSide) {
            setNaturalRest(false);
            if (!isCarcass()) {
                disturbSleep();
                if (isAlive() && previousInvulnerability <= 10) startInjuryAnimation();
                if (source.getEntity() instanceof LivingEntity attacker) {
                    familyBrain.attackedBy(attacker, amount);
                    DinosaurHerd group = herd();
                    group.refresh();
                    group.alert(attacker);
                    DinosaurFamilyBrain.defendYoung(this, attacker);
                }
            }
        }
        return hurt;
    }

    boolean allowsCarcass() { return JurassicConfig.ALLOW_CARCASS.get(); }
    DinosaurInventory inventory() { return inventory; }

    private void startInjuryAnimation() {
        int duration = DinosaurAnimationData.duration(species(), "INJURED");
        if (duration <= 0) return;
        finishAttackAnimation();
        // Keep the combat goal's state intact, but don't visually resume its old action.
        if (combatAnimation() != CombatAnimation.NONE) entityData.set(COMBAT_POSE_INTERRUPTED, true);
        // Birth keeps its saved phase; other interrupted poses should not resume after injury.
        if (!isBirthing() || !birthPose().equals(poseAnimation())) {
            entityData.set(POSE_ANIMATION, "");
            poseTicks = 0;
        }
        entityData.set(BIRTH_ANIMATING, false);
        injuryTicks = duration;
        entityData.set(INJURY_STARTED, (int) level.getGameTime());
        entityData.set(INJURY_ANIMATING, true);
    }

    private void finishInjuryAnimation() {
        injuryTicks = 0;
        entityData.set(INJURY_ANIMATING, false);
    }

    private void startAttackAnimation() {
        int duration = DinosaurAnimationData.duration(species(), "ATTACKING");
        // Upstream does not restart the same animation on a second strike.
        if (duration <= 0 || isAttackAnimating()) return;
        setNaturalRest(false);
        entityData.set(POSE_ANIMATION, "");
        poseTicks = 0;
        attackTicks = duration;
        entityData.set(ATTACK_STARTED, (int) level.getGameTime());
        entityData.set(ATTACK_ANIMATING, true);
    }

    private void finishAttackAnimation() {
        attackTicks = 0;
        entityData.set(ATTACK_ANIMATING, false);
    }

    @Override public void die(DamageSource source) {
        if (level.isClientSide || isCarcass() || carcassLootExhausted || source.isBypassInvul() || !allowsCarcass()) {
            super.die(source);
            if (!level.isClientSide && dead) {
                finishInjuryAnimation();
                finishAttackAnimation();
            }
            if (!level.isClientSide && dead && level instanceof ServerLevel server) DinosaurFamilyData.get(server).remove(getUUID());
            if (!level.isClientSide && dead) inventory.dropContents();
            if (!level.isClientSide && carcassLootExhausted && !dead && getHealth() <= 0) setHealth(1);
            return;
        }
        finishInjuryAnimation();
        finishAttackAnimation();
        inventory.dropContents();
        if (herd != null && source.getEntity() instanceof LivingEntity attacker) herd.alert(attacker);
        if (herd != null) herd.remove(this);
        setHealth(getMaxHealth()); // Keep the entity ticking and saveable while its carcass is present.
        carcassHealth = Math.min(10, Math.max(1, (int) Math.sqrt(getBbWidth() * getBbHeight()) * 2));
        carcassDecayTicks = 0;
        tranquilizerTicks = ticksUntilDeath = 0;
        entityData.set(TRANQUILIZED, false);
        entityData.set(NATURAL_SLEEP, false);
        entityData.set(NATURAL_REST, false);
        entityData.set(GLIDING, false);
        entityData.set(CLIMBING, false);
        entityData.set(CARCASS, true);
        if (level instanceof ServerLevel server) DinosaurFamilyData.get(server).remove(getUUID());
        getNavigation().stop();
        setTarget(null);
        setDeltaMovement(0, getDeltaMovement().y, 0);
    }

    private void harvestCarcass(DamageSource source) {
        if (level.isClientSide || !isCarcass() || isRemoved()) return;
        if (level.getGameRules().getBoolean(GameRules.RULE_DOMOBLOOT)) {
            int looting = source.getEntity() instanceof LivingEntity attacker ? EnchantmentHelper.getMobLooting(attacker) : 0;
            boolean cooked = isOnFire();
            ItemStack meat = new ItemStack((cooked ? DinosaurRegistry.cookedMeat(species()) : DinosaurRegistry.rawMeat(species())).get(),
                    1 + random.nextInt(2) + looting);
            if (!cooked) withGenetics(meat);
            if (source.getEntity() instanceof DinosaurEntity scavenger && scavenger.feed(meat)) meat.shrink(1);
            if (!meat.isEmpty()) spawnAtLocation(meat);
        }
        if (carcassHealth-- <= 0) finishCarcass(source);
    }

    private void finishCarcass(DamageSource source) {
        setHealth(0);
        super.die(source);
        if (dead) { inventory.dropContents(); discard(); }
        else {
            // A mod canceled death. Revive without allowing the same body to be harvested repeatedly.
            carcassLootExhausted = true;
            entityData.set(CARCASS, false);
            if (getHealth() <= 0) setHealth(1);
        }
    }
    void disturbSleep() {
        if (!level.isClientSide && !isTranquilized()) {
            setNaturalSleep(false);
            stayAwakeTicks = 400;
        }
    }
    @Override public void remove(net.minecraft.world.entity.Entity.RemovalReason reason) {
        if (herd != null) herd.remove(this);
        if (level instanceof ServerLevel server && (reason == Entity.RemovalReason.KILLED || reason == Entity.RemovalReason.DISCARDED
                || reason == Entity.RemovalReason.CHANGED_DIMENSION))
            DinosaurFamilyData.get(server).remove(getUUID());
        super.remove(reason);
    }
    /** Tranquilizer darts add the same 50–99 tick recovery margin as the original. */
    public void tranquilize(int ticks) {
        if (level.isClientSide || isCarcass() || ticks <= 0) return;
        tranquilizerTicks = (int) Math.min(Integer.MAX_VALUE, (long) ticks + 50 + random.nextInt(50));
        entityData.set(TRANQUILIZED, true);
        finishAttackAnimation();
        setNaturalRest(false);
        setClimbingTree(false);
        getNavigation().stop();
        setTarget(null);
        setDeltaMovement(0, getDeltaMovement().y, 0);
    }
    /** Poison now and kill after the requested number of loaded ticks. */
    public void setDeathIn(int ticks) {
        if (level.isClientSide || isCarcass() || ticks <= 0) return;
        ticksUntilDeath = ticks;
        addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.POISON, ticks));
    }
    public boolean canBreedWith(DinosaurEntity other) {
        return other != null && other != this && other.level == level && species() == other.species()
                && isMale() != other.isMale() && readyToBreed() && other.readyToBreed()
                && (!(level instanceof ServerLevel server) || DinosaurFamilyData.get(server).mayBreed(this, other));
    }
    void delayFamilyBreeding(int ticks) { breedCooldown = Math.max(breedCooldown, ticks); }
    /** A mature animal responds to an attack on a nearby infant of its own species. */
    void defendInfant(LivingEntity attacker) {
        disturbSleep();
        setNaturalRest(false);
        super.setTarget(attacker);
    }
    /** Starts a persisted pregnancy when opposite-sex, compatible adults have met. */
    public boolean beginBreeding(DinosaurEntity partner) {
        if (level.isClientSide || !canBreedWith(partner)
                || distanceToSqr(partner) > Math.max(9, getBbWidth() * getBbWidth())) return false;
        createPregnancy(partner);
        return true;
    }
    /** Debug breeding bypasses cooldown and distance, but requires two living mature animals of the same species. */
    public boolean forceBreedWith(DinosaurEntity partner) {
        if (level.isClientSide || partner == null || partner == this || partner.level != level
                || !isAlive() || !partner.isAlive() || species() != partner.species() || isMale() == partner.isMale()
                || growth() <= .75f || partner.growth() <= .75f || isPregnant() || partner.isPregnant()) return false;
        createPregnancy(partner);
        return true;
    }
    private void createPregnancy(DinosaurEntity partner) {
        DinosaurEntity mother = isMale() ? partner : this;
        DinosaurEntity father = isMale() ? this : partner;
        int[] clutch = clutch(species());
        int count = clutch[0] + random.nextInt(clutch[1] - clutch[0] + 1);
        for (int i = 0; i < count; i++) {
            StringBuilder genome = new StringBuilder(10);
            for (int base = 0; base < 10; base++) genome.append(random.nextBoolean() ? mother.genetics.charAt(base) : father.genetics.charAt(base));
            CompoundTag embryo = new CompoundTag();
            embryo.putString("Genetics", genome.toString());
            embryo.putInt("DNAQuality", Math.min(100, mother.dnaQuality + father.dnaQuality));
            embryo.putBoolean("Male", random.nextBoolean());
            CompoundTag inherited = new CompoundTag();
            DinosaurAttributes.combine(random, mother.geneticAttributes, father.geneticAttributes).write(inherited);
            embryo.put("GeneticAttributes", inherited);
            mother.embryos.add(embryo);
        }
        mother.gestationTicks = 9600;
        mother.breedCooldown = father.breedCooldown = clutch[2] * 1200;
    }
    /** Accelerate an existing pregnancy; undelivered embryos remain queued if a spawn is blocked. */
    public boolean deliverPregnancyNow() {
        if (level.isClientSide || !isAlive() || embryos.isEmpty()) return false;
        gestationTicks = 0;
        deliverYoung();
        return true;
    }
    private static int[] clutch(Species species) {
        return switch (species) {
            case VELOCIRAPTOR -> new int[]{1, 7, 28};
            case COELACANTH, MICRORAPTOR -> new int[]{1, species == Species.COELACANTH ? 3 : 5, 15};
            case BRACHIOSAURUS -> new int[]{4, 8, 72};
            case MUSSAURUS -> new int[]{2, 8, 15};
            case DILOPHOSAURUS -> new int[]{2, 4, 24};
            case GALLIMIMUS -> new int[]{2, 6, 20};
            case PARASAUROLOPHUS -> new int[]{4, 6, 40};
            case TRICERATOPS -> new int[]{2, 6, 48};
            case TYRANNOSAURUS -> new int[]{2, 4, 60};
        };
    }
    private String birthPose() { return isAquatic() ? "GIVING_BIRTH" : "LAYING_EGG"; }
    private void tickBirth() {
        if (birthRetryTicks > 0) birthRetryTicks--;
        boolean paused = isDinosaurSleeping() || isOrderedToSit() || isForcedPose()
                || isInjuryAnimating() || isAttackAnimating() || combatAnimation() != CombatAnimation.NONE;
        if (birthElapsed < 0) {
            if (gestationTicks != 0 || embryos.isEmpty() || birthRetryTicks > 0 || paused) return;
            birthElapsed = 0;
        }
        if (paused) {
            entityData.set(BIRTH_ANIMATING, false);
            return;
        }
        String pose = birthPose();
        int duration = DinosaurAnimationData.duration(species(), pose);
        if (duration <= 0) {
            deliverYoung();
            birthElapsed = -1;
            birthRetryTicks = 20;
            return;
        }
        // The ordinary pose timer clears the pose at the start of its final tick.
        if (isBirthing() && poseAnimation().isEmpty() && birthElapsed == duration - 1) {
            birthElapsed = -1;
            birthRetryTicks = 20;
            entityData.set(BIRTH_ANIMATING, false);
            return;
        }
        if (!isBirthing() || !pose.equals(poseAnimation())) {
            entityData.set(BIRTH_ANIMATING, false);
            if (!playPose(pose, 0, false)) return;
            poseTicks = duration - birthElapsed;
            entityData.set(BIRTH_STARTED, (int) level.getGameTime() - birthElapsed);
            entityData.set(BIRTH_ANIMATING, true);
            getNavigation().stop();
            setDeltaMovement(0, getDeltaMovement().y, 0);
            // A new phase starts at frame zero; a restored phase resumes at its saved frame.
            return;
        }
        if (++birthElapsed == duration / 2) deliverYoung();
        if (birthElapsed >= duration) {
            birthElapsed = -1;
            birthRetryTicks = 20;
            entityData.set(BIRTH_ANIMATING, false);
            if (pose.equals(poseAnimation()) && !isForcedPose()) {
                entityData.set(POSE_ANIMATION, "");
                poseTicks = 0;
            }
        }
    }
    private void deliverYoung() {
        if (!(level instanceof ServerLevel server)) return;
        DinosaurFamilyData families = DinosaurFamilyData.get(server);
        DinosaurFamilyData.Family family = families.family(getUUID());
        if (family != null && family.parents.contains(getUUID())) families.setHome(family, blockPosition(), server.getGameTime() + 6000);
        for (int i = embryos.size() - 1; i >= 0; i--) {
            CompoundTag embryo = embryos.getCompound(i);
            if (!DNAData.validGenetics(embryo.getString("Genetics"))) { embryos.remove(i); continue; }
            if (isAquatic()) {
                BlockPos pos = blockPosition().offset(random.nextInt(5) - 2, random.nextInt(3) - 1, random.nextInt(5) - 2);
                if (!level.getFluidState(pos).is(net.minecraft.tags.FluidTags.WATER)) continue;
                DinosaurAttributes inherited = DinosaurAttributes.read(embryo.getCompound("GeneticAttributes")).orElse(null);
                DinosaurEntity baby = DinosaurRegistry.spawn(species(), server, pos, true, embryo.getBoolean("Male"), inherited);
                if (baby == null) continue;
                baby.setGenetics(embryo.getString("Genetics"));
                baby.setDNAQuality(embryo.getInt("DNAQuality"));
                if (family != null && family.parents.contains(getUUID())) families.addChild(family.id, baby);
            } else {
                DinosaurEggEntity egg = DinosaurRegistry.EGG.get().create(level);
                if (egg == null) continue;
                egg.setEmbryo(species(), embryo.getString("Genetics"), embryo.getInt("DNAQuality"), embryo.getBoolean("Male"),
                        DinosaurAttributes.read(embryo.getCompound("GeneticAttributes")).orElse(null));
                if (family != null && family.parents.contains(getUUID())) egg.setFamily(family.id);
                egg.moveTo(getX() + (random.nextDouble() - .5), getY() + .5, getZ() + (random.nextDouble() - .5), 0, 0);
                if (!server.addFreshEntity(egg)) continue;
            }
            embryos.remove(i);
        }
    }
    @Nullable @Override public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob other) {
        // Reproduction is handled by gestation and laid eggs, not vanilla's instant offspring.
        return null;
    }
    @Override protected PathNavigation createNavigation(Level level) {
        return isAquatic() ? new WaterBoundPathNavigation(this, level) : super.createNavigation(level);
    }
    @Override public boolean canBreatheUnderwater() { return isAquatic() || super.canBreatheUnderwater(); }
    @Override public boolean isPushedByFluid() { return !isAquatic() && super.isPushedByFluid(); }
    @Override public boolean checkSpawnObstruction(LevelReader level) {
        // WaterAnimal permits liquid in its spawn volume; Mob's default rejects it.
        return isAquatic() ? level.isUnobstructed(this) : super.checkSpawnObstruction(level);
    }
    @Override public void baseTick() {
        int air = getAirSupply();
        super.baseTick();
        if (isAquatic() && isAlive()) {
            if (isInWaterOrBubble()) setAirSupply(getMaxAirSupply());
            else {
                setAirSupply(air - 1);
                if (getAirSupply() <= -20) { setAirSupply(0); hurt(DamageSource.DROWN, 2); }
            }
        }
    }
    @Override public void travel(Vec3 input) {
        if (isDinosaurSleeping() || isOrderedToSit() || isNaturallyResting() || isBirthing()) {
            setDeltaMovement(0, getDeltaMovement().y, 0);
            super.travel(Vec3.ZERO);
        } else if (isGliding()) {
            Vec3 heading = Vec3.directionFromRotation(0, getYRot());
            Vec3 velocity = getDeltaMovement();
            double x = velocity.x * .98 + heading.x * .025, z = velocity.z * .98 + heading.z * .025;
            double speed = Math.sqrt(x * x + z * z);
            if (speed > .6) { x *= .6 / speed; z *= .6 / speed; }
            setDeltaMovement(x, Math.max(velocity.y, -.1), z);
            fallDistance = 0;
            super.travel(Vec3.ZERO);
        } else if (isAquatic() && isEffectiveAi() && isInWater()) {
            moveRelative(.1F, input);
            move(net.minecraft.world.entity.MoverType.SELF, getDeltaMovement());
            setDeltaMovement(getDeltaMovement().scale(.9));
        } else super.travel(input);
    }
    @Override public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        if (combatAnimation() == CombatAnimation.LEAP_LAND) return false;
        return super.causeFallDamage(species() == Species.MICRORAPTOR ? distance / 2 : distance, multiplier, source);
    }
    @Override public void playAmbientSound() {
        // Upstream only speaks from its idle animation; don't interrupt an action or sleep.
        if (level.isClientSide || !isAlive() || isDinosaurSleeping() || isOrderedToSit() || isNaturallyResting()
                || combatAnimation() != CombatAnimation.NONE || swingTime > 0 || hurtTime > 0
                || !poseAnimation().isEmpty() || getAmbientSound() == null) return;
        if (playPose("SPEAK", 0, false)) super.playAmbientSound();
    }
    @Nullable @Override protected SoundEvent getAmbientSound() { return DinosaurRegistry.sound(species(), "ambient"); }
    @Nullable @Override protected SoundEvent getHurtSound(DamageSource source) { return DinosaurRegistry.sound(species(), "hurt"); }
    @Nullable @Override protected SoundEvent getDeathSound() { return DinosaurRegistry.sound(species(), "death"); }
    @Override protected void playStepSound(BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        super.playStepSound(pos, state);
        DinosaurPresentation.playStep(this);
    }
    @Override protected void dropCustomDeathLoot(DamageSource source, int looting, boolean recentlyHit) {
        super.dropCustomDeathLoot(source, looting, recentlyHit);
        if (carcassLootExhausted) return;
        if (!isCarcass()) {
            boolean cooked = isOnFire();
            ItemStack meat = new ItemStack((cooked ? DinosaurRegistry.cookedMeat(species()) : DinosaurRegistry.rawMeat(species())).get(),
                    1 + random.nextInt(2) + looting);
            spawnAtLocation(cooked ? meat : withGenetics(meat));
        }
        for (String bone : species().bones()) {
            if (random.nextInt(10) != 0) {
                spawnAtLocation(withGenetics(new ItemStack(AssemblyRegistry.FRESH_BONES.get(species()).get(bone).get())));
            }
        }
    }
    private ItemStack withGenetics(ItemStack stack) {
        Genetics.write(stack.getOrCreateTag(), DNAData.dinosaur(species(), dnaQuality, genetics));
        return stack;
    }
}
