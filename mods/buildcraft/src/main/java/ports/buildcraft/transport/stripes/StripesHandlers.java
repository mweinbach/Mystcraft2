package ports.buildcraft.transport.stripes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockSource;
import net.minecraft.core.Direction;
import net.minecraft.core.dispenser.DispenseItemBehavior;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShearsItem;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.CactusBlock;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import ports.buildcraft.lib.BlockProtection;
import ports.buildcraft.mixin.DispenserBlockAccessor;
import ports.buildcraft.transport.PipeDefinition;
import ports.buildcraft.transport.PipeItem;

/**
 * Upstream's {@code StripesHandler*} item handlers, each acting on the block or entities in front of the stripes
 * pipe with the item in the fake player's main hand. Forge-only hooks upstream used ({@code IShearable},
 * {@code IPlantable}, {@code onItemUseFirst}) have loader-neutral stand-ins here, described on each handler.
 *
 * <p>Each handler first asks {@link BlockProtection} whether the pipe owner's fake player may act there: shearing
 * removes a block, so it needs the loader's break check; the others use the item, so they need vanilla's checks, and
 * on Forge {@code ItemStack.useOn} posts the place events for the blocks it changes, as it did upstream. A refused
 * handler reports the item handled without using it, so no later handler tries it and the stripes pipe sends it back.
 */
public final class StripesHandlers {
    /** Upstream {@code StripesHandlerUse.ITEMS}: items used on the block in front. Empty upstream too. */
    public static final List<Item> USE_ITEMS = new ArrayList<>();

    private StripesHandlers() {}

    /**
     * Upstream {@code StripesHandlerPlant} with {@code CropHandlerPlantable}: seeds are planted on the block below the
     * front, or failing that on the front block itself. Upstream's seeds were {@code IPlantable} items and blocks,
     * other than sugar cane; here they are block items of bushes (crops, saplings, flowers, mushrooms, nether wart,
     * berries) and cactus, planted where the plant could survive, with air above, and not on its own kind.
     */
    static boolean plant(ServerLevel level, BlockPos pos, Direction direction, ItemStack stack, ServerPlayer player, StripesActivator activator) {
        BlockPos front = pos.relative(direction);
        return plantCrop(level, player, stack, front.below()) || plantCrop(level, player, stack, front);
    }

    private static boolean plantCrop(ServerLevel level, ServerPlayer player, ItemStack seed, BlockPos soil) {
        if (!(seed.getItem() instanceof BlockItem item)) return false;
        Block plant = item.getBlock();
        if (!(plant instanceof BushBlock) && !(plant instanceof CactusBlock)) return false;
        BlockPos above = soil.above();
        if (level.getBlockState(soil).is(plant) || !level.isEmptyBlock(above)) return false;
        if (!plant.defaultBlockState().canSurvive(level, above)) return false;
        if (!BlockProtection.mayUse(level, above, Direction.UP, seed, player)) return true;
        return useItemOnBlock(player, soil, Direction.UP);
    }

    /**
     * Upstream {@code StripesHandlerShears}: shearable blocks in front are sheared, using one durability, and their
     * drops come back through the pipe. Upstream asked Forge's {@code IShearable}; here a block is shearable when its
     * loot with the shears differs from its loot by hand, as for leaves, grass, vines and cobwebs.
     */
    static boolean shears(ServerLevel level, BlockPos pos, Direction direction, ItemStack stack, ServerPlayer player, StripesActivator activator) {
        if (!(stack.getItem() instanceof ShearsItem)) return false;
        BlockPos front = pos.relative(direction);
        BlockState state = level.getBlockState(front);
        if (state.isAir()) return false;
        long seed = level.random.nextLong();
        List<ItemStack> sheared = loot(level, front, state, stack.copy(), player, seed);
        if (sheared.isEmpty() || sameStacks(sheared, loot(level, front, state, ItemStack.EMPTY, player, seed))) return false;
        if (!BlockProtection.mayBreak(level, front, player)) return true;
        if (stack.hurt(1, player.getRandom(), player)) stack.shrink(1);
        level.setBlock(front, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL_IMMEDIATE);
        for (ItemStack drop : sheared) activator.sendItem(drop, direction);
        return true;
    }

    private static List<ItemStack> loot(ServerLevel level, BlockPos pos, BlockState state, ItemStack tool, ServerPlayer player, long seed) {
        BlockEntity tile = level.getBlockEntity(pos);
        return state.getDrops(new LootContext.Builder(level).withRandom(RandomSource.create(seed))
                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
                .withParameter(LootContextParams.TOOL, tool)
                .withOptionalParameter(LootContextParams.THIS_ENTITY, player)
                .withOptionalParameter(LootContextParams.BLOCK_ENTITY, tile));
    }

    private static boolean sameStacks(List<ItemStack> a, List<ItemStack> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            if (!ItemStack.matches(a.get(i), b.get(i))) return false;
        }
        return true;
    }

    /**
     * Upstream {@code StripesHandlerPipes}: an item pipe asks the {@link PipeExtensionManager} to lay itself where the
     * stripes pipe is at the end of the tick, moving the stripes pipe forward (or, for a void pipe, back).
     */
    static boolean pipes(ServerLevel level, BlockPos pos, Direction direction, ItemStack stack, ServerPlayer player, StripesActivator activator) {
        if (!(stack.getItem() instanceof PipeItem item) || item.definition().flow != PipeDefinition.Flow.ITEM) return false;
        if (!PipeExtensionManager.requestPipeExtension(level, pos, direction, stack.copy())) return false;
        // Nothing comes back now; the extension manager returns what it does not use.
        player.getInventory().clearContent();
        return true;
    }

    /** Upstream {@code StripesHandlerHoe}: tills the block in front, or else the one below it. */
    static boolean hoe(ServerLevel level, BlockPos pos, Direction direction, ItemStack stack, ServerPlayer player, StripesActivator activator) {
        if (!(stack.getItem() instanceof HoeItem)) return false;
        BlockPos front = pos.relative(direction);
        if (!BlockProtection.mayUse(level, front, Direction.UP, stack, player)) return true;
        if (useOn(player, front, Direction.UP, Vec3.atLowerCornerOf(front)) != InteractionResult.PASS) return true;
        if (direction == Direction.UP) return false;
        if (!BlockProtection.mayUse(level, front.below(), Direction.UP, stack, player)) return true;
        return useOn(player, front.below(), Direction.UP, Vec3.atLowerCornerOf(front.below())) != InteractionResult.PASS;
    }

    /** Upstream {@code StripesHandlerEntityInteract}: the item is used on a random living entity in front. */
    static boolean entityInteract(ServerLevel level, BlockPos pos, Direction direction, ItemStack stack, ServerPlayer player,
            StripesActivator activator) {
        List<LivingEntity> entities = new ArrayList<>(level.getEntitiesOfClass(LivingEntity.class, new AABB(pos.relative(direction))));
        if (entities.isEmpty()) return false;
        if (!BlockProtection.mayUse(level, pos.relative(direction), direction.getOpposite(), stack, player)) return true;
        Collections.shuffle(entities);
        for (LivingEntity entity : entities) {
            if (player.interactOn(entity, InteractionHand.MAIN_HAND).consumesAction()) return true;
        }
        return false;
    }

    /**
     * Upstream {@code StripesHandlerDispenser}: an item with a dispenser behaviour is dispensed as by a dispenser in
     * the pipe's place facing forward. One item is dispensed (upstream passed the whole stack, which made vanilla's
     * empty bucket behaviour look for a dispenser inventory); the rest stays with the player and comes back.
     */
    static boolean dispenser(ServerLevel level, BlockPos pos, Direction direction, ItemStack stack, ServerPlayer player, StripesActivator activator) {
        if (!DispenserBlockAccessor.buildcraft$getRegistry().containsKey(stack.getItem())) return false;
        if (!BlockProtection.mayUse(level, pos.relative(direction), direction.getOpposite(), stack, player)) return true;
        DispenseItemBehavior behaviour = DispenserBlockAccessor.buildcraft$getRegistry().get(stack.getItem());
        ItemStack single = stack.split(1);
        ItemStack rest = stack.copy();
        ItemStack output = behaviour.dispense(new Source(level, pos, direction), single);
        player.getInventory().clearContent();
        player.setItemInHand(InteractionHand.MAIN_HAND, output);
        if (!rest.isEmpty()) player.getInventory().setItem(1, rest);
        return true;
    }

    /** Upstream {@code StripesHandlerPlaceBlock}: a block item is placed in the empty space in front. */
    static boolean placeBlock(ServerLevel level, BlockPos pos, Direction direction, ItemStack stack, ServerPlayer player, StripesActivator activator) {
        if (!(stack.getItem() instanceof BlockItem)) return false;
        BlockPos front = pos.relative(direction);
        if (!level.isEmptyBlock(front)) return false;
        if (!BlockProtection.mayUse(level, front, direction, stack, player)) return true;
        useOn(player, front, direction, Vec3.atCenterOf(front));
        return true;
    }

    /** Upstream {@code StripesHandlerUse}: items in {@link #USE_ITEMS} are used on the block in front. */
    static boolean use(ServerLevel level, BlockPos pos, Direction direction, ItemStack stack, ServerPlayer player, StripesActivator activator) {
        if (!USE_ITEMS.contains(stack.getItem())) return false;
        if (!BlockProtection.mayUse(level, pos.relative(direction), direction.getOpposite(), stack, player)) return true;
        return useItemOnBlock(player, pos.relative(direction), direction.getOpposite());
    }

    /**
     * Upstream {@code BlockUtil.useItemOnBlock}: uses the held item on the centre of {@code face} of {@code pos}.
     * Upstream first tried Forge's {@code onItemUseFirst}, which has no loader-neutral equivalent.
     */
    private static boolean useItemOnBlock(ServerPlayer player, BlockPos pos, Direction face) {
        return useOn(player, pos, face, Vec3.atCenterOf(pos)).consumesAction();
    }

    private static InteractionResult useOn(ServerPlayer player, BlockPos pos, Direction face, Vec3 hit) {
        ItemStack held = player.getMainHandItem();
        return held.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, new BlockHitResult(hit, face, pos, false)));
    }

    /** Upstream {@code StripesHandlerDispenser.Source}: a dispenser at the pipe, facing the pipe's direction. */
    private static final class Source implements BlockSource {
        private final ServerLevel level;
        private final BlockPos pos;
        private final Direction side;

        Source(ServerLevel level, BlockPos pos, Direction side) {
            this.level = level;
            this.pos = pos;
            this.side = side;
        }

        @Override public double x() { return pos.getX() + 0.5; }
        @Override public double y() { return pos.getY() + 0.5; }
        @Override public double z() { return pos.getZ() + 0.5; }
        @Override public BlockPos getPos() { return pos; }
        @Override public ServerLevel getLevel() { return level; }

        @Override
        public BlockState getBlockState() {
            return Blocks.DISPENSER.defaultBlockState().setValue(DispenserBlock.FACING, side);
        }

        @Nullable
        @Override
        @SuppressWarnings("unchecked")
        public <T extends BlockEntity> T getEntity() {
            return (T) Objects.requireNonNull(level.getBlockEntity(pos));
        }
    }
}
