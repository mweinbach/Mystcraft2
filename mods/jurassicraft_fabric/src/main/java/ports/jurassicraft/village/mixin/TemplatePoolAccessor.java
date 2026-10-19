package ports.jurassicraft.village.mixin;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(StructureTemplatePool.class)
public interface TemplatePoolAccessor {
    @Accessor("templates") ObjectArrayList<StructurePoolElement> jurassicraft$templates();
    @Accessor("maxSize") void jurassicraft$setMaxSize(int value);
}
