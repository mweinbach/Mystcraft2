package ports.jurassicraft.village;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import ports.jurassicraft.content.FossilBlock;
import ports.jurassicraft.platform.Platform;

/** Adapt the original fossil-research task to the 1.19 villager brain. */
public final class PaleontologistDigGoal extends Goal {
    private static final String ANCHOR = "JurassicraftDigsiteOrigin";
    private final Villager villager;
    private int nextSearch, elapsed, duration, travelTicks;
    private BlockPos sand, stand;
    private boolean dug;

    public PaleontologistDigGoal(Villager villager) {
        this.villager = villager;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    /** Install on all villagers because an unemployed villager can later take this profession. */
    public static void onJoin(Entity entity) {
        if (entity.level.isClientSide || !(entity instanceof Villager villager)) return;
        if (villager.goalSelector.getAvailableGoals().stream().noneMatch(goal -> goal.getGoal() instanceof PaleontologistDigGoal))
            villager.goalSelector.addGoal(6, new PaleontologistDigGoal(villager));
    }

    private boolean allowed() {
        if (villager.getVillagerData().getProfession() != VillageRegistry.PALEONTOLOGIST.get()
                || villager.isBaby() || villager.isTrading() || villager.isSleeping()
                || villager.getBrain().isActive(Activity.PANIC) || villager.getBrain().isActive(Activity.RAID)
                || villager.getBrain().isActive(Activity.PRE_RAID)
                || !Platform.mobGriefing(villager.level, villager)) return false;
        var data = Platform.persistentData(villager);
        if (!data.contains(ANCHOR)) data.putLong(ANCHOR, villager.blockPosition().asLong());
        BlockPos anchor = BlockPos.of(data.getLong(ANCHOR));
        double dx = villager.getX() - anchor.getX(), dz = villager.getZ() - anchor.getZ();
        return dx * dx + dz * dz < 100.0;
    }

    @Override public boolean canUse() {
        if (!allowed()) return false;
        if (nextSearch > 0) { nextSearch--; return false; }
        nextSearch = 20 + villager.getRandom().nextInt(10);
        Level level = villager.level;
        BlockPos here = villager.blockPosition();
        List<BlockPos> candidates = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(here.offset(-3, -3, -3), here.offset(3, 3, 3)))
            if (level.hasChunkAt(pos) && level.getBlockState(pos).is(Blocks.SAND)) candidates.add(pos.immutable());
        while (!candidates.isEmpty()) {
            BlockPos top = candidates.remove(villager.getRandom().nextInt(candidates.size()));
            while (top.getY() < level.getMaxBuildHeight() - 1 && level.getBlockState(top.above()).is(Blocks.SAND)) top = top.above();
            BlockPos west = top.west(), east = top.east();
            if (east.distSqr(here) < west.distSqr(here)) { BlockPos temp = west; west = east; east = temp; }
            if (canStand(level, west)) { sand = top; stand = west; return true; }
            if (canStand(level, east)) { sand = top; stand = east; return true; }
        }
        return false;
    }

    private static boolean canStand(Level level, BlockPos pos) {
        return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
                && level.getFluidState(pos).isEmpty()
                && level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()
                && level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP);
    }
    @Override public boolean canContinueToUse() {
        return sand != null && allowed() && travelTicks < 360
                && (dug ? villager.level.getBlockState(sand).isAir() : villager.level.getBlockState(sand).is(Blocks.SAND));
    }
    @Override public void start() {
        elapsed = 0; travelTicks = 0; dug = false;
        duration = 60 + villager.getRandom().nextInt(40);
        if (villager.getMainHandItem().isEmpty()) villager.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SHOVEL));
        holdStand();
    }
    /**
     * Goal flags do not stop the villager brain: idle strolling and socialising pick a new walk target
     * whenever none is set, which includes the moment the villager reaches its stand. Re-asserting the
     * stand every tick keeps the villager at the dig, as upstream's move-to-block task did.
     */
    private void holdStand() {
        boolean held = villager.getBrain().getMemory(MemoryModuleType.WALK_TARGET)
                .map(target -> target.getTarget().currentBlockPosition().equals(stand)).orElse(false);
        if (!held) villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(stand, 0.4F, 0));
    }
    @Override public boolean requiresUpdateEveryTick() { return true; }
    @Override public void tick() {
        if (!canContinueToUse()) return;
        holdStand();
        BlockPos here = villager.blockPosition();
        if (here.getX() != stand.getX() || here.getZ() != stand.getZ() || Math.abs(here.getY() - stand.getY()) > 2) {
            travelTicks++;
            return;
        }
        villager.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(sand));
        villager.getLookControl().setLookAt(sand.getX() + .5, sand.getY() + 1, sand.getZ() + .5);
        elapsed++;
        if (elapsed == 20) {
            villager.level.destroyBlock(sand, false, villager);
            dug = true;
        } else if (elapsed >= duration) {
            if (!(villager.level.getBlockState(sand.below()).getBlock() instanceof FossilBlock)) {
                villager.level.setBlockAndUpdate(sand, Blocks.SAND.defaultBlockState());
                villager.level.playSound(null, sand, SoundEvents.SAND_PLACE, SoundSource.BLOCKS, 1F, .8F);
            }
            sand = null;
        }
    }
    @Override public void stop() {
        if (stand != null && villager.getBrain().getMemory(MemoryModuleType.WALK_TARGET)
                .map(target -> target.getTarget().currentBlockPosition().equals(stand)).orElse(false)) {
            villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            villager.getNavigation().stop();
        }
        sand = null; stand = null;
    }
}
