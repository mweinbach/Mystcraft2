/*
 * ProjectE 1.19.2 port - Fabric implementation of the Forge API shapes ProjectE uses.
 * Copyright (c) 2026 ProjectE 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.lib.patched;

import com.mojang.brigadier.arguments.ArgumentType;
import net.minecraft.commands.synchronization.ArgumentTypeInfo;
import net.minecraft.commands.synchronization.ArgumentTypeInfos;

/**
 * Forge's {@code ArgumentTypeInfos.registerByClass}: records which info serializes an argument class, which the
 * command tree sync looks up. The info itself is registered in the argument type registry by the caller.
 */
public final class ForgeArgumentTypeInfos {

    private ForgeArgumentTypeInfos() {
    }

    public static synchronized <A extends ArgumentType<?>, T extends ArgumentTypeInfo.Template<A>, I extends ArgumentTypeInfo<A, T>> I registerByClass(
          Class<A> infoClass, I argumentTypeInfo) {
        ArgumentTypeInfos.BY_CLASS.put(infoClass, argumentTypeInfo);
        return argumentTypeInfo;
    }
}
