/*
 * ProjectE 1.19.2 port, adapted from the Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.lib.common.extensions;

import com.google.common.collect.Multimap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.piglin.PiglinAi;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.ShearsItem;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ports.projecte.lib.client.extensions.common.IClientItemExtensions;
import ports.projecte.lib.common.ToolAction;
import ports.projecte.lib.common.ToolActions;
import ports.projecte.lib.common.capabilities.ICapabilityProvider;

/**
 * Methods Forge adds to {@link Item}, injected into it on Fabric. The port's mixins call them where Forge's patches
 * do; Fabric API's item hooks are used where they cover the same behavior.
 */
public interface IForgeItem {

    private Item self() {
        return (Item) this;
    }

    default Multimap<Attribute, AttributeModifier> getAttributeModifiers(EquipmentSlot slot, ItemStack stack) {
        return self().getDefaultAttributeModifiers(slot);
    }

    default InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        return InteractionResult.PASS;
    }

    default boolean isPiglinCurrency(ItemStack stack) {
        return stack.is(PiglinAi.BARTERING_ITEM);
    }

    default boolean makesPiglinsNeutral(ItemStack stack, LivingEntity wearer) {
        return stack.getItem() instanceof ArmorItem armor && armor.getMaterial() == ArmorMaterials.GOLD;
    }

    default boolean projecte$onBlockStartBreak(ItemStack stack, BlockPos pos, Player player) {
        return false;
    }

    /** Called before a player attacks an entity with this item; {@code true} cancels the attack. */
    default boolean projecte$onLeftClickEntity(ItemStack stack, Player player, Entity entity) {
        return false;
    }

    /** The tag sent to clients for this stack. */
    @Nullable
    default CompoundTag projecte$getShareTag(ItemStack stack) {
        return stack.getTag();
    }

    /** Applies a tag received from the server. */
    default void projecte$readShareTag(ItemStack stack, @Nullable CompoundTag nbt) {
        stack.setTag(nbt);
    }

    /** The stack-aware form of {@link Item#isCorrectToolForDrops}; {@code ItemStack.isCorrectToolForDrops} asks this. */
    default boolean projecte$isCorrectToolForDrops(ItemStack stack, BlockState state) {
        return self().isCorrectToolForDrops(state);
    }

    /** The box a sweep attack hits around its target. */
    default AABB projecte$getSweepHitBox(ItemStack stack, Player player, Entity target) {
        return target.getBoundingBox().inflate(1.0, 0.25, 1.0);
    }

    /** The damage stored on the stack. */
    default int projecte$getDamage(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null ? 0 : tag.getInt("Damage");
    }

    default ItemStack getCraftingRemainingItem(ItemStack stack) {
        Item remainder = self().getCraftingRemainingItem();
        return remainder == null ? ItemStack.EMPTY : new ItemStack(remainder);
    }

    default boolean hasCraftingRemainingItem(ItemStack stack) {
        return self().hasCraftingRemainingItem();
    }

    default boolean doesSneakBypassUse(ItemStack stack, LevelReader level, BlockPos pos, Player player) {
        return false;
    }

    default void onArmorTick(ItemStack stack, Level level, Player player) {
    }

    default boolean canEquip(ItemStack stack, EquipmentSlot armorType, Entity entity) {
        return Mob.getEquipmentSlotForItem(stack) == armorType;
    }

    @Nullable
    default EquipmentSlot getEquipmentSlot(ItemStack stack) {
        return null;
    }

    default boolean isBookEnchantable(ItemStack stack, ItemStack book) {
        return true;
    }

    @Nullable
    default String getArmorTexture(ItemStack stack, Entity entity, EquipmentSlot slot, String type) {
        return null;
    }

    /**
     * Forge's per-item tool actions. Paxels and other Mekanism tools override this; vanilla tool classes get the
     * action set Forge gives that class, so a hoe can till and an axe can strip unless a subclass says otherwise.
     */
    default boolean canPerformAction(ItemStack stack, ToolAction toolAction) {
        Item item = self();
        if (item instanceof AxeItem) {
            return ToolActions.DEFAULT_AXE_ACTIONS.contains(toolAction);
        } else if (item instanceof HoeItem) {
            return ToolActions.DEFAULT_HOE_ACTIONS.contains(toolAction);
        } else if (item instanceof ShovelItem) {
            return ToolActions.DEFAULT_SHOVEL_ACTIONS.contains(toolAction);
        } else if (item instanceof PickaxeItem) {
            return ToolActions.DEFAULT_PICKAXE_ACTIONS.contains(toolAction);
        } else if (item instanceof SwordItem) {
            return ToolActions.DEFAULT_SWORD_ACTIONS.contains(toolAction);
        } else if (item instanceof ShieldItem) {
            return ToolActions.DEFAULT_SHIELD_ACTIONS.contains(toolAction);
        } else if (item instanceof ShearsItem) {
            return ToolActions.DEFAULT_SHEARS_ACTIONS.contains(toolAction);
        }
        return false;
    }

    /**
     * Forge's stack-aware durability. Paxels and Mekanism armor override it; the stack mixin calls it from
     * {@link ItemStack#getMaxDamage()}.
     */
    default int getMaxDamage(ItemStack stack) {
        return self().getMaxDamage();
    }

    /**
     * Forge's damageable check. It ignores the unbreakable flag, which the paxel recipe relies on when it averages
     * the durability of its inputs.
     */
    default boolean isDamageable(ItemStack stack) {
        return getMaxDamage(stack) > 0;
    }

    /**
     * Forge adds this to digger items. Mekanism axes, hoes, pickaxes, shovels and paxels override it.
     */
    default float getAttackDamage() {
        return 0;
    }

    default int getMaxStackSize(ItemStack stack) {
        return self().getMaxStackSize();
    }

    default boolean canApplyAtEnchantingTable(ItemStack stack, Enchantment enchantment) {
        return enchantment.category.canEnchant(stack.getItem());
    }

    default int getEnchantmentLevel(ItemStack stack, Enchantment enchantment) {
        ResourceLocation id = Registry.ENCHANTMENT.getKey(enchantment);
        for (Tag tag : stack.getEnchantmentTags()) {
            CompoundTag entry = (CompoundTag) tag;
            if (id != null && id.equals(EnchantmentHelper.getEnchantmentId(entry))) {
                return EnchantmentHelper.getEnchantmentLevel(entry);
            }
        }
        return 0;
    }

    default Map<Enchantment, Integer> getAllEnchantments(ItemStack stack) {
        return EnchantmentHelper.deserializeEnchantments(stack.getEnchantmentTags());
    }

    default boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return !ItemStack.matches(oldStack, newStack);
    }

    default boolean shouldCauseBlockBreakReset(ItemStack oldStack, ItemStack newStack) {
        if (!newStack.is(oldStack.getItem())) {
            return true;
        } else if (!newStack.isDamageableItem() || !oldStack.isDamageableItem()) {
            return !ItemStack.tagMatches(newStack, oldStack);
        }
        //Only a change in damage keeps the block breaking, so mending does not reset it
        CompoundTag newTag = newStack.getTag();
        CompoundTag oldTag = oldStack.getTag();
        if (newTag == null || oldTag == null) {
            return newTag != oldTag;
        }
        Set<String> keys = newTag.getAllKeys();
        if (!keysWithoutDamage(keys).equals(keysWithoutDamage(oldTag.getAllKeys()))) {
            return true;
        }
        for (String key : keys) {
            if (!key.equals(ItemStack.TAG_DAMAGE) && !Objects.equals(newTag.get(key), oldTag.get(key))) {
                return true;
            }
        }
        return false;
    }

    private static Set<String> keysWithoutDamage(Set<String> keys) {
        return keys.contains(ItemStack.TAG_DAMAGE) ? keys.stream().filter(key -> !key.equals(ItemStack.TAG_DAMAGE)).collect(Collectors.toSet())
                                                   : keys;
    }

    @Nullable
    default String projecte$getCreatorModId(ItemStack stack) {
        return Registry.ITEM.getKey(stack.getItem()).getNamespace();
    }

    @Nullable
    default ICapabilityProvider initCapabilities(ItemStack stack, @Nullable CompoundTag nbt) {
        return null;
    }

    default int getBurnTime(ItemStack stack, @Nullable RecipeType<?> recipeType) {
        return -1;
    }

    default <T extends LivingEntity> int damageItem(ItemStack stack, int amount, T entity, Consumer<T> onBroken) {
        return amount;
    }

    default void onDestroyed(ItemEntity itemEntity, DamageSource damageSource) {
        self().onDestroyed(itemEntity);
    }

    default boolean isEnderMask(ItemStack stack, Player player, EnderMan enderMan) {
        return stack.is(Blocks.CARVED_PUMPKIN.asItem());
    }

    default boolean canElytraFly(ItemStack stack, LivingEntity entity) {
        return false;
    }

    default boolean elytraFlightTick(ItemStack stack, LivingEntity entity, int flightTicks) {
        return false;
    }

    default boolean canWalkOnPowderedSnow(ItemStack stack, LivingEntity wearer) {
        return stack.is(Items.LEATHER_BOOTS);
    }

    default int projecte$getDefaultTooltipHideFlags(@NotNull ItemStack stack) {
        return 0;
    }

    @Nullable
    default FoodProperties getFoodProperties(ItemStack stack, @Nullable LivingEntity entity) {
        return self().getFoodProperties();
    }

    default boolean isNotReplaceableByPickAction(ItemStack stack, Player player, int inventorySlot) {
        return stack.isEnchanted();
    }

    default boolean isRepairable(ItemStack stack) {
        return stack.isDamageableItem();
    }

    /**
     * Supplies the item's client behavior; read once through {@link IClientItemExtensions#of}.
     */
    default void initializeClient(Consumer<IClientItemExtensions> consumer) {
    }
}
