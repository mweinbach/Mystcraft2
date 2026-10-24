package ports.gridwright.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Carries one passive creature. Right-click a creature to capture it and a block to let it go.
 * Hostile creatures, bosses, players, creatures in {@code #gridwright:lasso_immune} and other players'
 * pets are refused. The released creature is a fresh copy (new UUID) of the captured one.
 */
public class GoldenLassoItem extends Item {

    /** Creature types the lasso refuses besides hostiles and players: bosses and anything packs add. */
    public static final TagKey<EntityType<?>> IMMUNE = TagKey.create(Registry.ENTITY_TYPE_REGISTRY,
            new ResourceLocation("gridwright", "lasso_immune"));
    private static final String MOB = "Mob";

    /** Why a creature cannot be captured, or {@code null} if it can. */
    public enum Refusal { HOSTILE, BOSS, OWNED, NOT_A_CREATURE }

    public GoldenLassoItem(Properties properties) {
        super(properties);
    }

    public static boolean holdsMob(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.contains(MOB, Tag.TAG_COMPOUND);
    }

    @Nullable
    public static Refusal refusal(LivingEntity target, Player player) {
        if (!(target instanceof Mob) || !target.isAlive() || !target.getType().canSerialize()) return Refusal.NOT_A_CREATURE;
        if (target instanceof EnderDragon || target instanceof WitherBoss || target.getType().is(IMMUNE)) return Refusal.BOSS;
        if (target instanceof Enemy) return Refusal.HOSTILE;
        if (target instanceof OwnableEntity pet) {
            UUID owner = pet.getOwnerUUID();
            if (owner != null && !owner.equals(player.getUUID())) return Refusal.OWNED;
        }
        return null;
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (holdsMob(stack)) return InteractionResult.PASS;
        Refusal refusal = refusal(target, player);
        if (refusal != null) {
            if (!player.level.isClientSide) {
                player.displayClientMessage(Component.translatable("message.gridwright.lasso.refused."
                        + refusal.name().toLowerCase(java.util.Locale.ROOT)), true);
            }
            return InteractionResult.FAIL;
        }
        if (player.level.isClientSide) return InteractionResult.SUCCESS;
        // Creative players' items receive a copy of the held stack; store the creature in the real one.
        ItemStack held = player.getItemInHand(hand);
        ItemStack lasso = held.is(this) && !holdsMob(held) ? held : stack;
        return capture(lasso, player, (Mob) target) ? InteractionResult.CONSUME : InteractionResult.FAIL;
    }

    /** Stores {@code target} in {@code stack} and removes it from the world. */
    public static boolean capture(ItemStack stack, Player player, Mob target) {
        if (holdsMob(stack) || refusal(target, player) != null) return false;
        target.ejectPassengers();
        target.stopRiding();
        if (target.isLeashed()) target.dropLeash(true, !player.getAbilities().instabuild);
        CompoundTag saved = new CompoundTag();
        if (!target.save(saved)) return false;
        saved.remove("UUID");
        stack.getOrCreateTag().put(MOB, saved);
        target.level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.5F, 0.6F);
        target.gameEvent(GameEvent.ENTITY_INTERACT, player);
        target.discard();
        return true;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        ItemStack stack = context.getItemInHand();
        if (!holdsMob(stack)) return InteractionResult.PASS;
        Level level = context.getLevel();
        if (!(level instanceof ServerLevel server)) return InteractionResult.SUCCESS;
        BlockPos clicked = context.getClickedPos();
        Direction face = context.getClickedFace();
        BlockPos spawn = level.getBlockState(clicked).getCollisionShape(level, clicked).isEmpty() ? clicked : clicked.relative(face);
        Player player = context.getPlayer();
        if (player != null && !player.mayUseItemAt(spawn, face, stack)) return InteractionResult.FAIL;
        return release(stack, server, spawn) != null ? InteractionResult.CONSUME : InteractionResult.FAIL;
    }

    /** Lets the creature out at {@code pos}; returns it, or {@code null} if there is no room. */
    @Nullable
    public static Entity release(ItemStack stack, ServerLevel level, BlockPos pos) {
        if (!holdsMob(stack)) return null;
        CompoundTag saved = stack.getOrCreateTag().getCompound(MOB);
        Optional<Entity> created = EntityType.create(saved, level);
        if (created.isEmpty()) return null;
        Entity entity = created.get();
        entity.setUUID(UUID.randomUUID());
        entity.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, entity.getYRot(), entity.getXRot());
        entity.setDeltaMovement(Vec3.ZERO);
        entity.fallDistance = 0;
        if (!level.noCollision(entity) || !level.addFreshEntity(entity)) return null;
        CompoundTag tag = stack.getTag();
        if (tag != null) {
            tag.remove(MOB);
            if (tag.isEmpty()) stack.setTag(null);
        }
        level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.5F, 1.2F);
        return entity;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return holdsMob(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        if (!holdsMob(stack)) {
            tooltip.add(Component.translatable("tooltip.gridwright.golden_lasso.empty").withStyle(ChatFormatting.GRAY));
            return;
        }
        CompoundTag saved = stack.getOrCreateTag().getCompound(MOB);
        Component name = saved.contains("CustomName", Tag.TAG_STRING) ? Component.Serializer.fromJson(saved.getString("CustomName")) : null;
        Component type = EntityType.by(saved).map(EntityType::getDescription).orElse(Component.literal("?"));
        tooltip.add(Component.translatable("tooltip.gridwright.golden_lasso.holding", name != null ? name : type)
                .withStyle(ChatFormatting.GOLD));
    }
}
