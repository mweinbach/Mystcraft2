package ports.tekxitprobe.mixin;

import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(CreateWorldScreen.class)
public interface CreateWorldScreenAccessor {
    @Accessor("nameEdit")
    EditBox tekxitprobe$nameEdit();

    @Accessor("commands")
    void tekxitprobe$setCommands(boolean commands);

    @Accessor("commandsChanged")
    void tekxitprobe$setCommandsChanged(boolean changed);

    @Invoker("onCreate")
    void tekxitprobe$onCreate();
}
