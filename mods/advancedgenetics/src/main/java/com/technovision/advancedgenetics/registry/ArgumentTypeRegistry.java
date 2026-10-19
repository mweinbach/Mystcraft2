package com.technovision.advancedgenetics.registry;

import com.technovision.advancedgenetics.AdvancedGenetics;
import com.technovision.advancedgenetics.commands.GeneArgumentType;
import net.minecraft.commands.synchronization.ArgumentTypeInfo;
import net.minecraft.commands.synchronization.ArgumentTypeInfos;
import net.minecraft.commands.synchronization.SingletonArgumentInfo;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ArgumentTypeRegistry {

    public static final DeferredRegister<ArgumentTypeInfo<?, ?>> ARGUMENT_TYPES = DeferredRegister.create(ForgeRegistries.COMMAND_ARGUMENT_TYPES, AdvancedGenetics.MOD_ID);

    public static final RegistryObject<SingletonArgumentInfo<GeneArgumentType>> GENE = ARGUMENT_TYPES.register("gene",
            () -> ArgumentTypeInfos.registerByClass(GeneArgumentType.class, SingletonArgumentInfo.contextFree(GeneArgumentType::gene)));
}
