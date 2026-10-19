package com.technovision.advancedgenetics.registry;

import com.technovision.advancedgenetics.commands.GeneArgumentType;
import com.technovision.advancedgenetics.platform.Platform;
import net.minecraft.commands.synchronization.SingletonArgumentInfo;

public class ArgumentTypeRegistry {

    public static void init() {
        Platform.registerArgumentType("gene", GeneArgumentType.class, SingletonArgumentInfo.contextFree(GeneArgumentType::gene));
    }
}
