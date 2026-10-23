package ports.buildcraft.builders.snapshot;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.material.Fluids;
import ports.buildcraft.builders.snapshot.rules.JsonRule;
import ports.buildcraft.builders.snapshot.rules.NbtJson;
import ports.buildcraft.builders.snapshot.rules.RequiredExtractor;
import ports.buildcraft.builders.snapshot.rules.RulesLoader;
import ports.buildcraft.lib.FluidAmount;

/**
 * Upstream {@code SchematicBlockDefault}: a block state and its block entity data, adjusted by the rules that
 * match them. Rules decide what the block costs, which blocks must be in place first, which properties may
 * differ, which block is actually placed and which blocks count as already built.
 */
public class SchematicBlockDefault implements SchematicBlock {
    private static final Comparator<BlockPos> POS_ORDER = Comparator.<BlockPos>comparingInt(BlockPos::getX)
            .thenComparingInt(BlockPos::getY).thenComparingInt(BlockPos::getZ);

    protected final Set<BlockPos> requiredBlockOffsets = new HashSet<>();
    protected BlockState blockState;
    protected final List<Property<?>> ignoredProperties = new ArrayList<>();
    @Nullable protected CompoundTag tileNbt;
    /** Kept for parity with upstream's data; 1.19 block entities have no rotation hook, so it changes nothing. */
    protected Rotation tileRotation = Rotation.NONE;
    protected Block placeBlock;
    protected final Set<BlockPos> updateBlockOffsets = new HashSet<>();
    protected final Set<Block> canBeReplacedWithBlocks = new HashSet<>();

    /**
     * The block entity data a scan records, read from the world or, for a detached context, given with it;
     * either way {@linkplain SchematicSafety#sanitizeTileNbt sanitized}, so a scan and a reload agree.
     */
    @Nullable
    static CompoundTag tileNbt(SchematicBlockContext context) {
        if (!context.blockState().hasBlockEntity()) return null;
        CompoundTag raw;
        if (context.level() == null) {
            raw = context.storedTileNbt();
        } else {
            BlockEntity blockEntity = context.level().getBlockEntity(context.pos());
            raw = blockEntity == null ? null : blockEntity.saveWithId();
        }
        return SchematicSafety.sanitizeTileNbt(context.blockState(), raw);
    }

    /** Upstream's test, plus {@link SchematicSafety}: forbidden blocks and blocks that would cost nothing are recorded as air. */
    public static boolean predicate(SchematicBlockContext context) {
        if (context.blockState().isAir() || SchematicSafety.isForbidden(context.blockState())) return false;
        ResourceLocation registryName = Registry.BLOCK.getKey(context.block());
        if (!RulesLoader.readDomains().contains(registryName.getNamespace())) return false;
        Set<JsonRule> rules = RulesLoader.getRules(context.blockState(), tileNbt(context));
        return rules.stream().noneMatch(rule -> rule.ignore) && SchematicSafety.hasCost(context.blockState(), rules);
    }

    protected void setRequiredBlockOffsets(SchematicBlockContext context, Set<JsonRule> rules) {
        requiredBlockOffsets.clear();
        rules.stream().map(rule -> rule.requiredBlockOffsets).filter(Objects::nonNull).flatMap(List::stream).forEach(requiredBlockOffsets::add);
        if (context.block() instanceof FallingBlock) requiredBlockOffsets.add(new BlockPos(0, -1, 0));
    }

    protected void setBlockState(SchematicBlockContext context, Set<JsonRule> rules) {
        blockState = context.blockState();
    }

    protected void setIgnoredProperties(SchematicBlockContext context, Set<JsonRule> rules) {
        ignoredProperties.clear();
        rules.stream().map(rule -> rule.ignoredProperties).filter(Objects::nonNull).flatMap(List::stream)
                .collect(TreeSet<String>::new, Set::add, Set::addAll)
                .forEach(name -> context.blockState().getProperties().stream()
                        .filter(property -> property.getName().equals(name))
                        .forEach(ignoredProperties::add));
    }

    protected void setTileNbt(SchematicBlockContext context, Set<JsonRule> rules) {
        tileNbt = tileNbt(context);
    }

    protected void setPlaceBlock(SchematicBlockContext context, Set<JsonRule> rules) {
        placeBlock = rules.stream().map(rule -> rule.placeBlock).filter(Objects::nonNull).findFirst()
                .map(ResourceLocation::tryParse).flatMap(Registry.BLOCK::getOptional)
                .orElse(context.block());
    }

    protected void setUpdateBlockOffsets(SchematicBlockContext context, Set<JsonRule> rules) {
        updateBlockOffsets.clear();
        if (rules.stream().map(rule -> rule.updateBlockOffsets).anyMatch(Objects::nonNull)) {
            rules.stream().map(rule -> rule.updateBlockOffsets).filter(Objects::nonNull).flatMap(List::stream).forEach(updateBlockOffsets::add);
        } else {
            Stream.of(Direction.values()).map(Direction::getNormal).map(BlockPos::new).forEach(updateBlockOffsets::add);
            updateBlockOffsets.add(BlockPos.ZERO);
        }
    }

    protected void setCanBeReplacedWithBlocks(SchematicBlockContext context, Set<JsonRule> rules) {
        canBeReplacedWithBlocks.clear();
        rules.stream().map(rule -> rule.canBeReplacedWithBlocks).filter(Objects::nonNull).flatMap(List::stream)
                .map(ResourceLocation::tryParse).filter(Objects::nonNull)
                .flatMap(id -> Registry.BLOCK.getOptional(id).stream())
                .forEach(canBeReplacedWithBlocks::add);
        canBeReplacedWithBlocks.add(context.block());
        canBeReplacedWithBlocks.add(placeBlock);
    }

    @Override
    public void init(SchematicBlockContext context) {
        Set<JsonRule> rules = RulesLoader.getRules(context.blockState(), tileNbt(context));
        setRequiredBlockOffsets(context, rules);
        setBlockState(context, rules);
        setIgnoredProperties(context, rules);
        setTileNbt(context, rules);
        setPlaceBlock(context, rules);
        setUpdateBlockOffsets(context, rules);
        setCanBeReplacedWithBlocks(context, rules);
    }

    @Override
    public Set<BlockPos> getRequiredBlockOffsets() {
        return requiredBlockOffsets;
    }

    @Override
    public List<ItemStack> computeRequiredItems() {
        List<List<RequiredExtractor>> extractors = RulesLoader.getRules(blockState, tileNbt).stream()
                .map(rule -> rule.requiredExtractors).filter(Objects::nonNull).toList();
        Stream<RequiredExtractor> used = extractors.isEmpty() ? Stream.of(new RequiredExtractor.ItemFromBlock()) : extractors.stream().flatMap(List::stream);
        return used.flatMap(extractor -> extractor.extractItemsFromBlock(blockState, tileNbt).stream())
                .filter(stack -> !stack.isEmpty())
                .toList();
    }

    /** Waterlogging did not exist in 1.12; a waterlogged block also needs the bucket of water it holds. */
    @Override
    public List<FluidAmount> computeRequiredFluids() {
        List<FluidAmount> fluids = new ArrayList<>(RulesLoader.getRules(blockState, tileNbt).stream()
                .map(rule -> rule.requiredExtractors).filter(Objects::nonNull).flatMap(List::stream)
                .flatMap(extractor -> extractor.extractFluidsFromBlock(blockState, tileNbt).stream())
                .filter(fluid -> !fluid.isEmpty())
                .toList());
        if (blockState.hasProperty(BlockStateProperties.WATERLOGGED) && blockState.getValue(BlockStateProperties.WATERLOGGED)) {
            fluids.add(new FluidAmount(Fluids.WATER, 1000));
        }
        return List.copyOf(fluids);
    }

    @Override
    public SchematicBlockDefault getRotated(Rotation rotation) {
        SchematicBlockDefault schematicBlock = SchematicBlockManager.createCleanCopy(this);
        requiredBlockOffsets.stream().map(pos -> pos.rotate(rotation)).forEach(schematicBlock.requiredBlockOffsets::add);
        schematicBlock.blockState = blockState.rotate(rotation);
        schematicBlock.ignoredProperties.addAll(ignoredProperties);
        schematicBlock.tileNbt = tileNbt;
        schematicBlock.tileRotation = tileRotation.getRotated(rotation);
        schematicBlock.placeBlock = placeBlock;
        updateBlockOffsets.stream().map(pos -> pos.rotate(rotation)).forEach(schematicBlock.updateBlockOffsets::add);
        schematicBlock.canBeReplacedWithBlocks.addAll(canBeReplacedWithBlocks);
        return schematicBlock;
    }

    @Override
    public boolean canBuild(Level level, BlockPos pos) {
        return level.isEmptyBlock(pos);
    }

    @Override
    public boolean build(Level level, BlockPos pos) {
        if (placeBlock == Blocks.AIR) return true;
        BlockState newBlockState = blockState;
        if (placeBlock != blockState.getBlock()) {
            newBlockState = placeBlock.defaultBlockState();
            for (Property<?> property : blockState.getProperties()) newBlockState = copyProperty(property, newBlockState, blockState);
        }
        for (Property<?> property : ignoredProperties) newBlockState = copyProperty(property, newBlockState, placeBlock.defaultBlockState());
        if (!level.setBlock(pos, newBlockState, 11)) return false;
        updateBlockOffsets.stream().map(pos::offset).forEach(updatePos -> level.updateNeighborsAt(updatePos, placeBlock));
        if (tileNbt != null && blockState.hasBlockEntity()) {
            CompoundTag replaceNbt = RulesLoader.getRules(blockState, tileNbt).stream()
                    .map(rule -> rule.replaceNbt).filter(Objects::nonNull)
                    .map(Tag.class::cast).reduce(NbtJson::merge)
                    .map(CompoundTag.class::cast).orElse(null);
            CompoundTag newTileNbt = withPos(tileNbt, pos);
            placeBlockEntity(level, pos, newBlockState, replaceNbt != null ? (CompoundTag) NbtJson.merge(newTileNbt, replaceNbt) : newTileNbt);
        }
        return true;
    }

    @Override
    public boolean buildWithoutChecks(Level level, BlockPos pos) {
        if (!level.setBlock(pos, blockState, 0)) return false;
        if (tileNbt != null && blockState.hasBlockEntity()) placeBlockEntity(level, pos, blockState, withPos(tileNbt, pos));
        return true;
    }

    private static CompoundTag withPos(CompoundTag tileNbt, BlockPos pos) {
        CompoundTag tag = tileNbt.copy();
        tag.putInt("x", pos.getX());
        tag.putInt("y", pos.getY());
        tag.putInt("z", pos.getZ());
        return tag;
    }

    private static void placeBlockEntity(Level level, BlockPos pos, BlockState state, CompoundTag nbt) {
        BlockEntity blockEntity = BlockEntity.loadStatic(pos, state, nbt);
        if (blockEntity != null) level.setBlockEntity(blockEntity);
    }

    private static <T extends Comparable<T>> BlockState copyProperty(Property<T> property, BlockState destination, BlockState source) {
        return destination.hasProperty(property) && source.hasProperty(property) ? destination.setValue(property, source.getValue(property)) : destination;
    }

    /** Upstream {@code BlockUtil.blockStatesWithoutBlockEqual}: shared properties match, apart from ignored ones. */
    static boolean blockStatesWithoutBlockEqual(BlockState a, BlockState b, List<Property<?>> ignoredProperties) {
        return a.getProperties().stream()
                .filter(b::hasProperty)
                .filter(property -> !ignoredProperties.contains(property))
                .allMatch(property -> Objects.equals(a.getValue(property), b.getValue(property)));
    }

    @Override
    public boolean isBuilt(Level level, BlockPos pos) {
        BlockState worldState = level.getBlockState(pos);
        return blockState != null && canBeReplacedWithBlocks.contains(worldState.getBlock())
                && blockStatesWithoutBlockEqual(blockState, worldState, ignoredProperties);
    }

    @Override
    public BlockState getPreviewState() {
        return blockState;
    }

    @Nullable
    @Override
    public CompoundTag getPreviewTileNbt() {
        return blockState.hasBlockEntity() ? tileNbt : null;
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.put("requiredBlockOffsets", posList(requiredBlockOffsets));
        tag.put("blockState", NbtUtils.writeBlockState(blockState));
        ListTag ignored = new ListTag();
        ignoredProperties.forEach(property -> ignored.add(StringTag.valueOf(property.getName())));
        tag.put("ignoredProperties", ignored);
        if (tileNbt != null) tag.put("tileNbt", tileNbt);
        tag.putString("tileRotation", tileRotation.name());
        tag.putString("placeBlock", Registry.BLOCK.getKey(placeBlock).toString());
        tag.put("updateBlockOffsets", posList(updateBlockOffsets));
        ListTag replaceable = new ListTag();
        canBeReplacedWithBlocks.stream().map(block -> Registry.BLOCK.getKey(block).toString()).sorted()
                .forEach(name -> replaceable.add(StringTag.valueOf(name)));
        tag.put("canBeReplacedWithBlocks", replaceable);
        return tag;
    }

    /** Sorted, unlike upstream's hash set order, so equal schematics always hash alike. */
    private static ListTag posList(Set<BlockPos> positions) {
        ListTag list = new ListTag();
        positions.stream().sorted(POS_ORDER).forEach(pos -> list.add(NbtUtils.writeBlockPos(pos)));
        return list;
    }

    @Override
    public void deserializeNBT(CompoundTag tag) throws InvalidSnapshotException {
        requiredBlockOffsets.clear();
        for (Tag pos : tag.getList("requiredBlockOffsets", Tag.TAG_COMPOUND)) requiredBlockOffsets.add(NbtUtils.readBlockPos((CompoundTag) pos));
        blockState = NbtUtils.readBlockState(tag.getCompound("blockState"));
        ignoredProperties.clear();
        for (Tag name : tag.getList("ignoredProperties", Tag.TAG_STRING)) {
            Property<?> property = blockState.getBlock().getStateDefinition().getProperty(name.getAsString());
            if (property != null) ignoredProperties.add(property);
        }
        tileNbt = tag.contains("tileNbt", Tag.TAG_COMPOUND) ? tag.getCompound("tileNbt") : null;
        try {
            tileRotation = Rotation.valueOf(tag.getString("tileRotation"));
        } catch (IllegalArgumentException e) {
            throw new InvalidSnapshotException("Unknown rotation " + tag.get("tileRotation"));
        }
        placeBlock = Registry.BLOCK.get(ResourceLocation.tryParse(tag.getString("placeBlock")));
        updateBlockOffsets.clear();
        for (Tag pos : tag.getList("updateBlockOffsets", Tag.TAG_COMPOUND)) updateBlockOffsets.add(NbtUtils.readBlockPos((CompoundTag) pos));
        canBeReplacedWithBlocks.clear();
        for (Tag name : tag.getList("canBeReplacedWithBlocks", Tag.TAG_STRING)) {
            canBeReplacedWithBlocks.add(Registry.BLOCK.get(ResourceLocation.tryParse(name.getAsString())));
        }
    }

    /**
     * Only the recorded state and block entity data are taken from saved data; everything else, including the
     * block that is placed and what it costs, is derived from them by the rules, exactly as a scan does. A
     * saved placed block that differs from the derived one is refused rather than corrected, as is a block
     * the builder may not place or a scan would not record. Blocks that no longer exist load as air.
     */
    @Override
    public SchematicBlock canonicalize() throws InvalidSnapshotException {
        if (blockState.isAir()) return SchematicBlockManager.canonical(blockState, null);
        if (SchematicSafety.isForbidden(blockState) || placeBlock != Blocks.AIR && SchematicSafety.isForbidden(placeBlock.defaultBlockState())) {
            throw new InvalidSnapshotException("A builder may not place " + blockState + " (placing " + Registry.BLOCK.getKey(placeBlock) + ")");
        }
        SchematicBlock canonical = SchematicBlockManager.canonical(blockState, tileNbt);
        if (canonical.getClass() != getClass()) {
            throw new InvalidSnapshotException("A scan of " + blockState + " does not record " + getClass().getSimpleName());
        }
        SchematicBlockDefault schematicBlock = (SchematicBlockDefault) canonical;
        if (schematicBlock.placeBlock != placeBlock) {
            throw new InvalidSnapshotException("The rules place " + Registry.BLOCK.getKey(schematicBlock.placeBlock) + " for " + blockState
                    + ", not " + Registry.BLOCK.getKey(placeBlock));
        }
        return schematicBlock;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SchematicBlockDefault that = (SchematicBlockDefault) o;
        return requiredBlockOffsets.equals(that.requiredBlockOffsets)
                && blockState.equals(that.blockState)
                && ignoredProperties.equals(that.ignoredProperties)
                && Objects.equals(tileNbt, that.tileNbt)
                && tileRotation == that.tileRotation
                && placeBlock.equals(that.placeBlock)
                && updateBlockOffsets.equals(that.updateBlockOffsets)
                && canBeReplacedWithBlocks.equals(that.canBeReplacedWithBlocks);
    }

    @Override
    public int hashCode() {
        return Objects.hash(requiredBlockOffsets, blockState, ignoredProperties, tileNbt, tileRotation, placeBlock, updateBlockOffsets,
                canBeReplacedWithBlocks);
    }
}
