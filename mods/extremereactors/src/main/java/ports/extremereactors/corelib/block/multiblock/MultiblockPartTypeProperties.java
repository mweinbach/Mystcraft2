/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.block.multiblock;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import ports.extremereactors.multiblock.IMultiblockController;
import ports.extremereactors.multiblock.IMultiblockPart;
import ports.extremereactors.multiblock.variant.IMultiblockVariant;

import javax.annotation.Nullable;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * How to build the blocks of a part type, and which block entity they host.
 */
public class MultiblockPartTypeProperties<Controller extends IMultiblockController<Controller>,
                                          PartType extends Enum<PartType> & IMultiblockPartType> {

    /**
     * @param tileTypeSupplier a supplier of the (lazily registered) block entity type supplier
     * @param blockFactory builds the block from its part properties
     * @param blockPropertiesFixer adjusts the block properties of the variant (or the default ones)
     */
    public MultiblockPartTypeProperties(final Supplier<Supplier<BlockEntityType<?>>> tileTypeSupplier,
                                        final Function<MultiblockPartBlock.MultiblockPartProperties<PartType>,
                                                MultiblockPartBlock<Controller, PartType>> blockFactory,
                                        final String translationKey,
                                        final Function<Block.Properties, Block.Properties> blockPropertiesFixer) {
        this(tileTypeSupplier, blockFactory, translationKey, blockPropertiesFixer, pp -> pp);
    }

    /**
     * @param partPropertiesFixer adjusts the part properties before the block is built
     */
    public MultiblockPartTypeProperties(final Supplier<Supplier<BlockEntityType<?>>> tileTypeSupplier,
                                        final Function<MultiblockPartBlock.MultiblockPartProperties<PartType>,
                                                MultiblockPartBlock<Controller, PartType>> blockFactory,
                                        final String translationKey,
                                        final Function<Block.Properties, Block.Properties> blockPropertiesFixer,
                                        final Function<MultiblockPartBlock.MultiblockPartProperties<PartType>,
                                                MultiblockPartBlock.MultiblockPartProperties<PartType>> partPropertiesFixer) {

        this._tileTypeSupplier = tileTypeSupplier;
        this._blockFactory = blockFactory;
        this._translationKey = translationKey;
        this._blockPropertiesFixer = blockPropertiesFixer;
        this._partPropertiesFixer = partPropertiesFixer;
    }

    public String getTranslationKey() {
        return this._translationKey;
    }

    public BlockEntityType<?> getTileEntityType() {
        return this._tileTypeSupplier.get().get();
    }

    @Nullable
    public BlockEntity createTileEntity(final BlockPos position, final BlockState state) {
        return this.getTileEntityType().create(position, state);
    }

    public MultiblockPartBlock<Controller, PartType> createBlock(final PartType partType,
                                                                 @Nullable final IMultiblockVariant variant) {

        final Block.Properties blockProperties = this._blockPropertiesFixer.apply(null != variant ?
                variant.getBlockProperties() : IMultiblockPart.getDefaultBlockProperties());
        final MultiblockPartBlock.MultiblockPartProperties<PartType> partProperties =
                new MultiblockPartBlock.MultiblockPartProperties<>(partType, variant, blockProperties);

        return this._blockFactory.apply(this._partPropertiesFixer.apply(partProperties));
    }

    //region internals

    private final Supplier<Supplier<BlockEntityType<?>>> _tileTypeSupplier;
    private final Function<MultiblockPartBlock.MultiblockPartProperties<PartType>, MultiblockPartBlock<Controller, PartType>> _blockFactory;
    private final String _translationKey;
    private final Function<Block.Properties, Block.Properties> _blockPropertiesFixer;
    private final Function<MultiblockPartBlock.MultiblockPartProperties<PartType>, MultiblockPartBlock.MultiblockPartProperties<PartType>> _partPropertiesFixer;

    //endregion
}
