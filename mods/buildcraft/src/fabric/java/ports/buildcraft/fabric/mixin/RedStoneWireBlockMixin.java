package ports.buildcraft.fabric.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import ports.buildcraft.transport.PipeBlock;

/**
 * Forge's {@code IForgeBlock.canConnectRedstone} for Fabric: vanilla wire connects to any signal source, so without
 * this it would join every face of a pipe carrying a gate. The wire's side check asks
 * {@link PipeBlock#canConnectRedstone} instead, so it joins only a face whose pluggable allows it, as on Forge and
 * upstream. Other blocks keep vanilla's answer, and other mods' wrappers of the same call still run.
 */
@Mixin(RedStoneWireBlock.class)
public abstract class RedStoneWireBlockMixin {
    @WrapOperation(
            method = "getConnectingSide(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;Z)Lnet/minecraft/world/level/block/state/properties/RedstoneSide;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/RedStoneWireBlock;shouldConnectTo(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/Direction;)Z"))
    private boolean buildcraft$connectToPipeFace(BlockState neighbour, @Nullable Direction direction, Operation<Boolean> original,
            @Local(argsOnly = true) BlockGetter level, @Local(argsOnly = true) BlockPos pos) {
        if (neighbour.getBlock() instanceof PipeBlock pipe && direction != null) {
            return pipe.canConnectRedstone(neighbour, level, pos.relative(direction), direction);
        }
        return original.call(neighbour, direction);
    }
}
