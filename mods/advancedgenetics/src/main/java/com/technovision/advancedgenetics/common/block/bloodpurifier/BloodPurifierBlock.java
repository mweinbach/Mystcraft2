package com.technovision.advancedgenetics.common.block.bloodpurifier;

import com.technovision.advancedgenetics.Config;
import com.technovision.advancedgenetics.api.block.AbstractGeneticsBlock;

public class BloodPurifierBlock extends AbstractGeneticsBlock {

    public BloodPurifierBlock() {
        super(BloodPurifierBlockEntity::new, () -> Config.Common.bloodPurifierEnergyPerTick.get());
    }
}
