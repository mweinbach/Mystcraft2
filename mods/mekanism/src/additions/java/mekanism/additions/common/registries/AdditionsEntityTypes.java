package mekanism.additions.common.registries;

import mekanism.additions.common.MekanismAdditions;
import mekanism.additions.common.entity.EntityObsidianTNT;
import mekanism.common.registration.impl.EntityTypeDeferredRegister;
import mekanism.common.registration.impl.EntityTypeRegistryObject;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public class AdditionsEntityTypes {

    private AdditionsEntityTypes() {
    }

    public static final EntityTypeDeferredRegister ENTITY_TYPES = new EntityTypeDeferredRegister(MekanismAdditions.MODID);

    public static final EntityTypeRegistryObject<EntityObsidianTNT> OBSIDIAN_TNT = ENTITY_TYPES.register("obsidian_tnt",
          EntityType.Builder.of(EntityObsidianTNT::new, MobCategory.MISC).fireImmune().sized(0.98F, 0.98F));
}
