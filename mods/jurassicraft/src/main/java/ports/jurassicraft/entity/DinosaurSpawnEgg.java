package ports.jurassicraft.entity;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import ports.jurassicraft.platform.SpawnEggBehavior;

/** Sneak-use in the air cycles gender; sneak-use on a block spawns a baby. */
public final class DinosaurSpawnEgg implements SpawnEggBehavior {
    public static final DinosaurSpawnEgg INSTANCE = new DinosaurSpawnEgg();

    private DinosaurSpawnEgg() {}

    public static int genderMode(ItemStack stack) {
        return stack.hasTag() ? Math.floorMod(stack.getTag().getInt("GenderMode"), 3) : 0;
    }

    private static Component modeName(int mode) {
        return Component.translatable("mode.jurassicraft." + (mode == 1 ? "male" : mode == 2 ? "female" : "random"));
    }

    private static void configure(Entity entity, ItemStack stack, boolean baby) {
        if (entity instanceof DinosaurEntity dinosaur) {
            dinosaur.setDNAQuality(100);
            int mode = genderMode(stack);
            if (mode != 0) dinosaur.setMale(mode == 1);
            if (baby) dinosaur.setBaby(true);
        }
    }

    private static Entity spawn(SpawnEggItem egg, ServerLevel level, ItemStack stack, Player player, BlockPos pos, boolean offset, boolean offsetUp, boolean baby) {
        Entity entity = egg.getType(stack.getTag()).create(level, stack.getTag(),
                stack.hasCustomHoverName() ? stack.getHoverName() : null, player, pos, MobSpawnType.SPAWN_EGG, offset, offsetUp);
        if (entity != null) {
            configure(entity, stack, baby);
            level.addFreshEntityWithPassengers(entity);
        }
        return entity;
    }

    @Override public InteractionResult useOn(SpawnEggItem egg, UseOnContext context) {
        Level level = context.getLevel();
        if (!(level instanceof ServerLevel server)) return InteractionResult.SUCCESS;
        ItemStack stack = context.getItemInHand();
        BlockPos clicked = context.getClickedPos();
        Direction face = context.getClickedFace();
        Player player = context.getPlayer();
        if (player != null && (!level.mayInteract(player, clicked)
                || !player.mayUseItemAt(clicked.relative(face), face, stack))) return InteractionResult.FAIL;

        var state = level.getBlockState(clicked);
        if (state.is(Blocks.SPAWNER) && level.getBlockEntity(clicked) instanceof SpawnerBlockEntity spawner) {
            spawner.getSpawner().setEntityId(egg.getType(stack.getTag()));
            spawner.setChanged();
            level.sendBlockUpdated(clicked, state, state, 3);
            level.gameEvent(player, GameEvent.BLOCK_CHANGE, clicked);
            if (player == null || !player.getAbilities().instabuild) stack.shrink(1);
            return InteractionResult.CONSUME;
        }

        BlockPos spawnPos = state.getCollisionShape(level, clicked).isEmpty() ? clicked : clicked.relative(face);
        Entity entity = spawn(egg, server, stack, player, spawnPos, true, !spawnPos.equals(clicked) && face == Direction.UP,
                player != null && player.isShiftKeyDown());
        if (entity != null) {
            if (player == null || !player.getAbilities().instabuild) stack.shrink(1);
            level.gameEvent(player, GameEvent.ENTITY_PLACE, clicked);
        }
        return InteractionResult.CONSUME;
    }

    @Override public InteractionResultHolder<ItemStack> use(SpawnEggItem egg, Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown()) {
            if (!level.isClientSide) {
                int mode = (genderMode(stack) + 1) % 3;
                stack.getOrCreateTag().putInt("GenderMode", mode);
                player.displayClientMessage(Component.translatable("message.jurassicraft.spawn_egg_gender", modeName(mode))
                        .withStyle(ChatFormatting.GOLD), true);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }

        // Keep the vanilla water-source interaction, including selected gender for aquatic species.
        BlockHitResult hit = Item.getPlayerPOVHitResult(level, player, ClipContext.Fluid.SOURCE_ONLY);
        if (hit.getType() != HitResult.Type.BLOCK) return InteractionResultHolder.pass(stack);
        if (!(level instanceof ServerLevel server)) return InteractionResultHolder.success(stack);
        BlockPos pos = hit.getBlockPos();
        if (!(level.getBlockState(pos).getBlock() instanceof LiquidBlock)) return InteractionResultHolder.pass(stack);
        if (!level.mayInteract(player, pos) || !player.mayUseItemAt(pos, hit.getDirection(), stack)) return InteractionResultHolder.fail(stack);
        Entity entity = spawn(egg, server, stack, player, pos, false, false, false);
        if (entity == null) return InteractionResultHolder.pass(stack);
        if (!player.getAbilities().instabuild) stack.shrink(1);
        player.awardStat(Stats.ITEM_USED.get(egg));
        level.gameEvent(player, GameEvent.ENTITY_PLACE, entity.position());
        return InteractionResultHolder.consume(stack);
    }

    @Override public void appendHoverText(SpawnEggItem egg, ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("tooltip.jurassicraft.spawn_egg_gender", modeName(genderMode(stack))).withStyle(ChatFormatting.GOLD));
        lines.add(Component.translatable("tooltip.jurassicraft.spawn_egg_change_gender"));
        lines.add(Component.translatable("tooltip.jurassicraft.spawn_egg_baby").withStyle(ChatFormatting.YELLOW));
    }
}
