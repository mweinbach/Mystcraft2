package mekanism.additions.common.advancements;

import mekanism.additions.common.MekanismAdditions;
import mekanism.common.advancements.MekanismAdvancement;
import mekanism.common.advancements.MekanismAdvancements;
import org.jetbrains.annotations.Nullable;

public class AdditionsAdvancements {

    private AdditionsAdvancements() {
    }

    private static MekanismAdvancement advancement(@Nullable MekanismAdvancement parent, String name) {
        return new MekanismAdvancement(parent, MekanismAdditions.rl(name));
    }

    public static final MekanismAdvancement GLOW_IN_THE_DARK = advancement(MekanismAdvancements.ROOT, "glow_in_the_dark");
}
