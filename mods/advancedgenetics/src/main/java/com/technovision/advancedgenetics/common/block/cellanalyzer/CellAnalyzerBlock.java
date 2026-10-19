package com.technovision.advancedgenetics.common.block.cellanalyzer;

import com.technovision.advancedgenetics.Config;
import com.technovision.advancedgenetics.api.block.AbstractGeneticsBlock;

public class CellAnalyzerBlock extends AbstractGeneticsBlock {

    public CellAnalyzerBlock() {
        super(CellAnalyzerBlockEntity::new, () -> Config.Common.cellAnalyzerEnergyPerTick.get());
    }
}
