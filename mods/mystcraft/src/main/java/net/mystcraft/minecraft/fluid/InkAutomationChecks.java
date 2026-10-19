package net.mystcraft.minecraft.fluid;
/** Optional test-harness hook for checking the installed native API without linking loaders in shared code. */
public final class InkAutomationChecks {
    @FunctionalInterface public interface Check{void run(net.minecraft.server.level.ServerLevel level,net.minecraft.core.BlockPos desk);}
    private static Check check;
    public static void install(Check nativeCheck){check=nativeCheck;}
    public static void run(net.minecraft.server.level.ServerLevel level,net.minecraft.core.BlockPos desk){if(check==null)throw new AssertionError("Native ink automation adapter was not initialized");check.run(level,desk);}
    private InkAutomationChecks(){}
}
