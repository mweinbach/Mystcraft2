/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.block.multiblock;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import ports.extremereactors.corelib.block.AbstractModBlockEntity;
import ports.extremereactors.corelib.block.ModBlock;
import ports.extremereactors.multiblock.IMultiblockController;
import ports.extremereactors.multiblock.variant.IMultiblockVariant;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * The block of a multiblock part. Its part type decides which block entity it hosts.
 */
public class MultiblockPartBlock<Controller extends IMultiblockController<Controller>,
                                 PartType extends Enum<PartType> & IMultiblockPartType>
        extends ModBlock
        implements EntityBlock {

    public MultiblockPartBlock(final MultiblockPartProperties<PartType> properties) {

        super(properties._blockProperties);
        this._partType = properties._partType;
        this._variant = properties._variant;
        this._tooltipBuilder = properties._tooltipBuilder;
    }

    public PartType getPartType() {
        return this._partType;
    }

    public Optional<IMultiblockVariant> getMultiblockVariant() {
        return Optional.ofNullable(this._variant);
    }

    /**
     * @return true if the item of this block keeps the data of its block entity when the block is broken
     */
    public boolean isStackStorable() {
        return null != this._tooltipBuilder;
    }

    //region EntityBlock

    @Nullable
    @Override
    public BlockEntity newBlockEntity(final BlockPos position, final BlockState state) {
        return this._partType instanceof IMultiblockPartType2<?, ?> partType ?
                partType.getPartTypeProperties().createTileEntity(position, state) : null;
    }

    //endregion
    //region Block

    @Override
    public void appendHoverText(final ItemStack stack, @Nullable final BlockGetter world,
                                final List<Component> tooltip, final TooltipFlag flag) {

        super.appendHoverText(stack, world, tooltip, flag);

        if (null != this._tooltipBuilder) {

            final CompoundTag blockEntityData = BlockItem.getBlockEntityData(stack);

            if (null != blockEntityData && blockEntityData.contains(AbstractModBlockEntity.SYNC_PAYLOAD_KEY, Tag.TAG_COMPOUND)) {
                this._tooltipBuilder.build(stack, blockEntityData.getCompound(AbstractModBlockEntity.SYNC_PAYLOAD_KEY),
                        world, tooltip::add, flag.isAdvanced());
            }
        }
    }

    //endregion

    @FunctionalInterface
    public interface ITooltipBuilder {

        /**
         * @param data the stored block entity data
         */
        void build(ItemStack stack, CompoundTag data, @Nullable BlockGetter world, Consumer<Component> appender,
                   boolean isAdvancedTooltip);
    }

    public static class MultiblockPartProperties<PartType extends Enum<PartType> & IMultiblockPartType> {

        public MultiblockPartProperties(final PartType partType, @Nullable final IMultiblockVariant variant,
                                        final Block.Properties blockProperties) {

            this._partType = partType;
            this._variant = variant;
            this._blockProperties = blockProperties;
            this._tooltipBuilder = null;
        }

        public PartType getPartType() {
            return this._partType;
        }

        public Optional<IMultiblockVariant> getVariant() {
            return Optional.ofNullable(this._variant);
        }

        public Block.Properties getBlockProperties() {
            return this._blockProperties;
        }

        /**
         * Keep the block entity data in the dropped item (the loot table copies it) and describe it in the tooltip.
         */
        public MultiblockPartProperties<PartType> setAsStackStorable(final ITooltipBuilder tooltipBuilder) {

            this._tooltipBuilder = tooltipBuilder;
            return this;
        }

        private final PartType _partType;
        @Nullable
        private final IMultiblockVariant _variant;
        private final Block.Properties _blockProperties;
        @Nullable
        private ITooltipBuilder _tooltipBuilder;
    }

    //region internals

    private final PartType _partType;
    @Nullable
    private final IMultiblockVariant _variant;
    @Nullable
    private final ITooltipBuilder _tooltipBuilder;

    //endregion
}
