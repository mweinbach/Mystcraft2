package ports.buildcraft.forge;

import java.util.function.Consumer;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.world.item.Item;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import ports.buildcraft.silicon.plug.FacadeItem;

/** Forge's item renderer hook. {@code initializeClient} runs from {@code Item}'s constructor on the client. */
final class ForgeFacadeItem extends FacadeItem {
    ForgeFacadeItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            private BlockEntityWithoutLevelRenderer renderer;

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (renderer == null) renderer = new FacadeItemRenderer();
                return renderer;
            }
        });
    }
}
