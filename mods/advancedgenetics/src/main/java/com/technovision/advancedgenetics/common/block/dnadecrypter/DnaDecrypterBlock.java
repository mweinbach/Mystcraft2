package com.technovision.advancedgenetics.common.block.dnadecrypter;

import com.technovision.advancedgenetics.Config;
import com.technovision.advancedgenetics.api.block.AbstractGeneticsBlock;

public class DnaDecrypterBlock extends AbstractGeneticsBlock {

    public DnaDecrypterBlock() {
        super(DnaDecrypterBlockEntity::new, () -> Config.Common.dnaDecrypterEnergyPerTick.get());
    }
}
