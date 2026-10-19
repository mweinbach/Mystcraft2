package com.technovision.advancedgenetics.common.block.plasmidinjector;

import com.technovision.advancedgenetics.Config;
import com.technovision.advancedgenetics.api.block.AbstractGeneticsBlock;

public class PlasmidInjectorBlock extends AbstractGeneticsBlock {

    public PlasmidInjectorBlock() {
        super(PlasmidInjectorBlockEntity::new, () -> Config.Common.plasmidInjectorEnergyPerTick.get());
    }
}
