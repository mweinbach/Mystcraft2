package ports.jurassicraft.goat;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.feeder.FeederFood;

public final class JurassicGoat extends Animal {
    private static final EntityDataAccessor<Boolean> BILLY = SynchedEntityData.defineId(JurassicGoat.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> VARIANT = SynchedEntityData.defineId(JurassicGoat.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> MILKED = SynchedEntityData.defineId(JurassicGoat.class, EntityDataSerializers.BOOLEAN);
    public static final TagKey<Item> FOOD = TagKey.create(Registry.ITEM_REGISTRY, new ResourceLocation(PortMod.MOD_ID, "goat_food"));
    private static final String[] VARIANTS = {"jurassic_world", "jurassic_park", "jpog"};
    private EatBlockGoal eatGoal;
    private int eatingTicks;

    public JurassicGoat(EntityType<? extends JurassicGoat> type, Level level) { super(type, level); maxUpStep = 1F; }
    @Override protected void defineSynchedData() {
        super.defineSynchedData(); entityData.define(BILLY, false); entityData.define(VARIANT, 1); entityData.define(MILKED, false);
    }
    @Override protected void registerGoals() {
        eatGoal = new EatBlockGoal(this);
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new PanicGoal(this, 2));
        goalSelector.addGoal(1, eatGoal);
        goalSelector.addGoal(2, new BreedGoal(this, 1));
        goalSelector.addGoal(3, new TemptGoal(this, 1.25, Ingredient.of(FOOD), false));
        goalSelector.addGoal(4, new FollowParentGoal(this, 1.25));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 1));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 6));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        goalSelector.addGoal(8, new AvoidEntityGoal<>(this, Wolf.class, 6F, 1, 1.6));
    }
    public boolean isBilly() { return entityData.get(BILLY); }
    public void setBilly(boolean male) { entityData.set(BILLY, male); }
    public String variant() { return VARIANTS[Math.floorMod(entityData.get(VARIANT), VARIANTS.length)]; }
    public int eatingTicks() { return eatingTicks; }
    public boolean isMilked() { return entityData.get(MILKED); }
    @Override protected void customServerAiStep() { eatingTicks = eatGoal.getEatAnimationTick(); super.customServerAiStep(); }
    @Override public void aiStep() { if (level.isClientSide && eatingTicks > 0) eatingTicks--; super.aiStep(); }
    @Override public void handleEntityEvent(byte event) { if (event == 10) eatingTicks = 40; else super.handleEntityEvent(event); }
    @Override public void ate() { super.ate(); entityData.set(MILKED, false); }
    @Override public boolean isFood(ItemStack stack) { return stack.is(FOOD) || FeederFood.plant(stack); }
    @Override public boolean canMate(Animal other) { return other instanceof JurassicGoat goat && isBilly() != goat.isBilly() && super.canMate(other); }
    @Nullable @Override public JurassicGoat getBreedOffspring(ServerLevel level, AgeableMob mate) {
        JurassicGoat child = GoatRegistry.GOAT.get().create(level);
        if (child != null) child.randomize();
        return child;
    }
    private void randomize() { setBilly(random.nextBoolean()); entityData.set(VARIANT, random.nextInt(VARIANTS.length)); }
    @Nullable @Override public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
            MobSpawnType reason, @Nullable SpawnGroupData group, @Nullable CompoundTag tag) {
        randomize();
        return super.finalizeSpawn(level, difficulty, reason, group, tag);
    }
    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag); tag.putBoolean("Billy", isBilly()); tag.putInt("Variant", entityData.get(VARIANT)); tag.putBoolean("Milked", isMilked());
    }
    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag); setBilly(tag.getBoolean("Billy")); entityData.set(VARIANT, Math.floorMod(tag.getInt("Variant"), VARIANTS.length)); entityData.set(MILKED, tag.getBoolean("Milked"));
    }
    @Override public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (stack.is(Items.BUCKET) && !isBaby() && !isBilly() && !isMilked()) {
            if (!level.isClientSide) {
                playSound(SoundEvents.COW_MILK, 1, 1);
                player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, new ItemStack(Items.MILK_BUCKET)));
                entityData.set(MILKED, true);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return super.mobInteract(player, hand);
    }
    @Override protected float getJumpPower() { return .62F; }
    @Override public int getAmbientSoundInterval() { return 300; }
    @Override public int getMaxSpawnClusterSize() { return 3; }
    @Override protected float getSoundVolume() { return super.getSoundVolume() * .8F; }
    @Nullable @Override protected SoundEvent getAmbientSound() { return GoatRegistry.AMBIENT.get(); }
    @Nullable @Override protected SoundEvent getHurtSound(DamageSource source) { return GoatRegistry.HURT.get(); }
    @Nullable @Override protected SoundEvent getDeathSound() { return GoatRegistry.DEATH.get(); }
}
