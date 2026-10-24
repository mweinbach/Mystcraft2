package ports.gridwright.worker;

import ports.gridwright.item.BiomeMarkerItem;
import ports.gridwright.registry.ModBlockEntities;
import ports.gridwright.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;

/**
 * The quantum quarry mines a virtual world ({@link QuarryTable}) instead of real terrain, so it never
 * leaves a hole and never runs out. It needs a quarry actuator on each of its four sides and a lot of
 * Grid Power: {@link #BASE_GP} plus {@link #GP_PER_UPGRADE} for every speed and fortune level and for
 * silk touch. It mines one virtual block every {@link #BASE_TICKS} ticks, divided by one plus the speed
 * level. Drops come from each block's real loot table, mined with {@link Upgrades#tool()}.
 *
 * <p>The sequence of blocks is deterministic: the n-th block a quarry mines depends only on the world
 * seed, the quarry's position, n, its marker and its upgrades. When its output is full it waits,
 * holding the block it mined, and draws no power.
 */
public class QuantumQuarryBlockEntity extends WorkerBlockEntity {

    public static final double BASE_GP = 32;
    public static final double GP_PER_UPGRADE = 16;
    public static final int BASE_TICKS = 40;
    /** How often the table is rebuilt to pick up tag changes from datapack reloads. */
    private static final int TABLE_REFRESH_TICKS = 1200;

    private long mined;
    private int progress;
    /** Drops of a block mined while the output was full, waiting for room. */
    @Nullable private List<ItemStack> pending;
    @Nullable private QuarryTable table;
    @Nullable private ResourceLocation tableBiome;
    private long tableBuilt;

    public QuantumQuarryBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.QUANTUM_QUARRY.get(), pos, state, WorkerLayout.QUANTUM_QUARRY);
    }

    public static double gp(Upgrades upgrades) {
        return BASE_GP + GP_PER_UPGRADE * (upgrades.speed() + upgrades.fortune() + (upgrades.silkTouch() ? 1 : 0));
    }

    public static int ticksPerBlock(Upgrades upgrades) {
        return Math.max(1, BASE_TICKS / (1 + upgrades.speed()));
    }

    /** Virtual blocks mined so far. */
    public long mined() {
        return mined;
    }

    @Override
    public List<ItemStack> pendingDrops() {
        return pending == null ? List.of() : pending;
    }

    @Override
    protected int counter() {
        return (int) Math.min(Integer.MAX_VALUE, mined);
    }

    @Override
    protected int progressPerMille() {
        return progress * 1000 / ticksPerBlock(Upgrades.read(this, layout()));
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, BlockEntity blockEntity) {
        if (level instanceof ServerLevel server && blockEntity instanceof QuantumQuarryBlockEntity quarry) quarry.tick(server);
    }

    /** Whether a quarry actuator stands on each of the four horizontal sides. */
    public boolean complete(Level level) {
        for (Direction side : Direction.Plane.HORIZONTAL) {
            if (!level.getBlockState(worldPosition.relative(side)).is(ModBlocks.QUARRY_ACTUATOR.get())) return false;
        }
        return true;
    }

    private void tick(ServerLevel level) {
        if (!complete(level)) {
            stop(Status.INCOMPLETE);
            return;
        }
        int outputs = layout().firstOutput();
        if (pending != null) {
            if (!fits(pending, outputs, layout().size())) {
                stop(Status.BLOCKED);
                return;
            }
            pending.forEach(stack -> store(stack, outputs, layout().size()));
            pending = null;
        }
        Upgrades upgrades = Upgrades.read(this, layout());
        if (!requestPower(level, gp(upgrades))) return;
        if (++progress < ticksPerBlock(upgrades)) {
            setChanged();
            return;
        }
        progress = 0;
        List<ItemStack> drops = mine(level, upgrades, mined++);
        if (fits(drops, outputs, layout().size())) drops.forEach(stack -> store(stack, outputs, layout().size()));
        else pending = drops;
        setChanged();
    }

    /** The drops of the {@code index}-th virtual block this quarry mines with {@code upgrades}. */
    public List<ItemStack> mine(ServerLevel level, Upgrades upgrades, long index) {
        RandomSource random = RandomSource.create(seed(level.getSeed(), worldPosition, index));
        BlockState state = table(level).draw(random);
        return drops(level, worldPosition, state, upgrades.tool(), random);
    }

    /** {@code state}'s loot as if mined at {@code pos} with {@code tool}. */
    public static List<ItemStack> drops(ServerLevel level, BlockPos pos, BlockState state, ItemStack tool, RandomSource random) {
        return state.getDrops(new LootContext.Builder(level).withRandom(random)
                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
                .withParameter(LootContextParams.TOOL, tool));
    }

    /** The table for the biome on the marker, plains without one. */
    public QuarryTable table(ServerLevel level) {
        ResourceLocation biome = BiomeMarkerItem.biome(items.get(layout().first(WorkerLayout.Role.MARKER)));
        if (biome == null) biome = Biomes.PLAINS.location();
        if (table == null || !biome.equals(tableBiome) || level.getGameTime() - tableBuilt >= TABLE_REFRESH_TICKS) {
            table = QuarryTable.forBiome(biomeHolder(level, biome));
            tableBiome = biome;
            tableBuilt = level.getGameTime();
        }
        return table;
    }

    private static Holder<Biome> biomeHolder(ServerLevel level, ResourceLocation id) {
        Registry<Biome> biomes = level.registryAccess().registryOrThrow(Registry.BIOME_REGISTRY);
        return biomes.getHolder(ResourceKey.create(Registry.BIOME_REGISTRY, id))
                .<Holder<Biome>>map(holder -> holder).orElseGet(() -> biomes.getHolderOrThrow(Biomes.PLAINS));
    }

    /** A well-mixed seed (SplitMix64 finaliser), so consecutive indices give unrelated draws. */
    static long seed(long worldSeed, BlockPos pos, long index) {
        long z = worldSeed ^ (pos.asLong() * 0x9E3779B97F4A7C15L) ^ (index * 0xC2B2AE3D27D4EB4FL);
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        super.setItem(slot, stack);
        if (slot == layout().first(WorkerLayout.Role.MARKER)) table = null;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putLong("Mined", mined);
        tag.putInt("Progress", progress);
        if (pending != null) tag.put("Pending", saveStacks(pending));
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        mined = tag.getLong("Mined");
        progress = tag.getInt("Progress");
        pending = tag.contains("Pending") ? loadStacks(tag.getList("Pending", 10)) : null;
        table = null;
    }
}
