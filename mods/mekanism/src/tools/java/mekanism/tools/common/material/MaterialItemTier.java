package mekanism.tools.common.material;

import net.minecraft.tags.TagKey;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

/**
 * The {@link net.minecraft.world.item.Tier} view of a Mekanism material. It is a separate object from the armor
 * material: both interfaces declare {@code getEnchantmentValue} and {@code getRepairIngredient}, and Fabric's
 * remapper cannot map one method onto both. The numbers are the material's.
 */
final class MaterialItemTier extends IItemTierHelper {

    private final BaseMekanismMaterial material;

    MaterialItemTier(BaseMekanismMaterial material) {
        this.material = material;
    }

    @Override
    public int getUses() {
        return material.getUses();
    }

    @Override
    public float getSpeed() {
        return material.getSpeed();
    }

    @Override
    public float getAttackDamageBonus() {
        return material.getAttackDamageBonus();
    }

    @Override
    public int getLevel() {
        return material.getLevel();
    }

    @Override
    public int getItemEnchantability() {
        return material.getCommonEnchantability();
    }

    @Override
    public Ingredient getItemRepairMaterial() {
        return material.getCommonRepairMaterial();
    }

    @Nullable
    @Override
    public TagKey<Block> getTag() {
        return material.getTag();
    }
}
