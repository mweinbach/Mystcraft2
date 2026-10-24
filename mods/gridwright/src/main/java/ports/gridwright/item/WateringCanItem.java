package ports.gridwright.item;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.CactusBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.SugarCaneBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Holds water. Right-click a water source to fill it (the source stays). Hold right-click on the ground
 * to water a 3x3x3 area around the targeted block every {@link #PULSE_TICKS} ticks for one unit of
 * water: each plant there that grows by random ticks gets one with chance {@link #GROWTH_CHANCE},
 * farmland is soaked and fire is put out. Plants that ignore random ticks, and things that are not
 * plants (leaves, ice, farmland itself), are never ticked.
 */
public class WateringCanItem extends Item {

    public static final int CAPACITY = 400;
    public static final int PULSE_TICKS = 4;
    public static final float GROWTH_CHANCE = 0.25F;
    private static final String WATER = "Water";

    public WateringCanItem(Properties properties) {
        super(properties);
    }

    public static int water(ItemStack stack) {
        return stack.hasTag() ? Mth.clamp(stack.getOrCreateTag().getInt(WATER), 0, CAPACITY) : 0;
    }

    public static void setWater(ItemStack stack, int water) {
        stack.getOrCreateTag().putInt(WATER, Mth.clamp(water, 0, CAPACITY));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        BlockHitResult hit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.SOURCE_ONLY);
        if (hit.getType() == HitResult.Type.BLOCK && level.getFluidState(hit.getBlockPos()).is(FluidTags.WATER)
                && level.getFluidState(hit.getBlockPos()).isSource() && water(stack) < CAPACITY) {
            if (!level.isClientSide) {
                setWater(stack, CAPACITY);
                level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BUCKET_FILL, SoundSource.PLAYERS, 1.0F, 1.0F);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        if (water(stack) <= 0 && !player.getAbilities().instabuild) return InteractionResultHolder.fail(stack);
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void onUseTick(Level level, LivingEntity user, ItemStack stack, int remaining) {
        if (!(level instanceof ServerLevel server) || !(user instanceof Player player)) return;
        if ((getUseDuration(stack) - remaining) % PULSE_TICKS != 0) return;
        boolean creative = player.getAbilities().instabuild;
        if (water(stack) <= 0 && !creative) {
            user.releaseUsingItem();
            return;
        }
        BlockHitResult hit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.NONE);
        if (hit.getType() != HitResult.Type.BLOCK) return;
        pour(server, hit.getBlockPos(), server.random);
        if (!creative) setWater(stack, water(stack) - 1);
    }

    /** One pulse of water on the 3x3x3 area around {@code center}; returns how many plants were ticked. */
    public static int pour(ServerLevel level, BlockPos center, RandomSource random) {
        int ticked = 0;
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-1, -1, -1), center.offset(1, 1, 1))) {
            BlockState state = level.getBlockState(pos);
            Block block = state.getBlock();
            if (block instanceof FarmBlock) {
                if (state.getValue(FarmBlock.MOISTURE) < FarmBlock.MAX_MOISTURE) {
                    level.setBlock(pos, state.setValue(FarmBlock.MOISTURE, FarmBlock.MAX_MOISTURE), Block.UPDATE_CLIENTS);
                }
            } else if (block instanceof BaseFireBlock) {
                level.removeBlock(pos, false);
            } else if (plant(state) && random.nextFloat() < GROWTH_CHANCE) {
                state.randomTick(level, pos.immutable(), random);
                ticked++;
            }
        }
        level.sendParticles(ParticleTypes.SPLASH, center.getX() + 0.5, center.getY() + 1.1, center.getZ() + 0.5, 6, 0.6, 0.1, 0.6, 0.0);
        return ticked;
    }

    /**
     * Plants that grow by random ticks. Leaves (decay), farmland (drying) and grass, mycelium and nylium
     * (spreading) also tick randomly but are not plants.
     */
    public static boolean plant(BlockState state) {
        if (!state.isRandomlyTicking() || state.is(BlockTags.LEAVES) || state.is(Blocks.FARMLAND)
                || state.is(BlockTags.DIRT) || state.is(BlockTags.NYLIUM)) return false;
        Block block = state.getBlock();
        return block instanceof BushBlock || block instanceof BonemealableBlock || block instanceof CactusBlock
                || block instanceof SugarCaneBlock || state.is(BlockTags.SAPLINGS);
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return 72000;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.NONE;
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return true;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13.0F * water(stack) / CAPACITY);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return 0x3F76E4;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.gridwright.watering_can", water(stack), CAPACITY));
    }
}
