package net.mystcraft.minecraft.fluid;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.BlockHitResult;
import net.mystcraft.integration.registry.InkRegistry;
/** Original bucketFix/bottleFix: black ink must never be picked up as vanilla water. */
public final class InkInteractions {
    public static boolean denyWaterBottle(Level level,Player player,ItemStack stack){
        if(!stack.is(Items.GLASS_BOTTLE))return false;
        var eye=player.getEyePosition();var hit=level.clip(new ClipContext(eye,eye.add(player.getViewVector(1F).scale(5)),ClipContext.Block.OUTLINE,ClipContext.Fluid.SOURCE_ONLY,player));
        return hit.getType()==HitResult.Type.BLOCK&&level.getBlockState(((BlockHitResult)hit).getBlockPos()).is(InkRegistry.BLACK_INK_BLOCK.get());
    }
    private InkInteractions(){}
}
