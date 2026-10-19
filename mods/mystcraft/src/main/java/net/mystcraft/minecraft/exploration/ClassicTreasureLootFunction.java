package net.mystcraft.minecraft.exploration;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.LootItemConditionalFunction;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctionType;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.mystcraft.integration.registry.ExplorationRegistry;
/** A single weighted draw, leaving pool rolls and luck scaling to the native loot engine. */
public final class ClassicTreasureLootFunction extends LootItemConditionalFunction {
    public ClassicTreasureLootFunction(LootItemCondition[] conditions){super(conditions);}
    @Override public LootItemFunctionType getType(){return ExplorationRegistry.TREASURE_FUNCTION.get();}
    @Override protected ItemStack run(ItemStack stack,LootContext context){return ClassicTreasure.roll(context.getLevel().registryAccess(),context.getRandom());}
    public static final class Serializer extends LootItemConditionalFunction.Serializer<ClassicTreasureLootFunction>{
        @Override public ClassicTreasureLootFunction deserialize(JsonObject json,JsonDeserializationContext context,LootItemCondition[] conditions){return new ClassicTreasureLootFunction(conditions);}
    }
}
