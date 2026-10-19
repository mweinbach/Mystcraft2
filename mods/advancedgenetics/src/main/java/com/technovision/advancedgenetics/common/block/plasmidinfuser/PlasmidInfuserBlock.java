package com.technovision.advancedgenetics.common.block.plasmidinfuser;

import com.technovision.advancedgenetics.Config;
import com.technovision.advancedgenetics.api.block.AbstractGeneticsBlock;

public class PlasmidInfuserBlock extends AbstractGeneticsBlock {

    public PlasmidInfuserBlock() {
        super(PlasmidInfuserBlockEntity::new, () -> Config.Common.plasmidInfuserEnergyPerTick.get());
    }
}
