package com.technovision.advancedgenetics.common.block.dnaextractor;

import com.technovision.advancedgenetics.Config;
import com.technovision.advancedgenetics.api.block.AbstractGeneticsBlock;

public class DnaExtractorBlock extends AbstractGeneticsBlock {

    public DnaExtractorBlock() {
        super(DnaExtractorBlockEntity::new, () -> Config.Common.dnaExtractorEnergyPerTick.get());
    }
}
