package net.mystcraft.minecraft.exploration;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.LootItemConditionalFunction;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctionType;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.mystcraft.integration.registry.ExplorationRegistry;

public final class SymbolPageLootFunction extends LootItemConditionalFunction {
    public SymbolPageLootFunction(LootItemCondition[] conditions) { super(conditions); }
    @Override public LootItemFunctionType getType() { return ExplorationRegistry.SYMBOL_PAGE_FUNCTION.get(); }
    @Override protected ItemStack run(ItemStack stack, LootContext context) { return ExplorationPages.sample(context.getLevel().registryAccess(), context.getRandom(), false); }
    public static final class Serializer extends LootItemConditionalFunction.Serializer<SymbolPageLootFunction> {
        @Override public SymbolPageLootFunction deserialize(JsonObject json, JsonDeserializationContext context, LootItemCondition[] conditions) {
            return new SymbolPageLootFunction(conditions);
        }
    }
}
