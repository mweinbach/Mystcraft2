package ports.buildcraft.transport.statements;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import ports.buildcraft.statement.Action;
import ports.buildcraft.statement.BCStatement;
import ports.buildcraft.statement.ColourText;
import ports.buildcraft.statement.Statement;
import ports.buildcraft.statement.StatementContainer;
import ports.buildcraft.statement.StatementParameter;
import ports.buildcraft.transport.behaviour.EmzuliBehaviour.SlotIndex;

/**
 * Upstream {@code ActionExtractionPreset}: while active, the emzuli pipe extracts with the filter slot of this
 * colour; the pipe's behaviour listens for it.
 */
public class ActionExtractionPreset extends BCStatement implements Action.Internal {
    public final SlotIndex index;
    private final ResourceLocation sprite;

    ActionExtractionPreset(SlotIndex index) {
        super("buildcraft:extraction.preset." + index.colour.getName(), "buildcraft.extraction.preset." + index.colour.getName());
        this.index = index;
        sprite = sprite("extraction_preset_" + index.colour.getName());
    }

    @Override
    public Component getDescription() {
        return Component.translatable("gate.action.extraction", ColourText.of(index.colour));
    }

    @Override
    public void actionActivate(StatementContainer source, StatementParameter[] parameters) {}

    @Override
    public ResourceLocation getSprite() {
        return sprite;
    }

    @Override
    public Statement[] getPossible() {
        return BCTransportStatements.ACTION_EXTRACTION_PRESET;
    }

    @Override
    public String toString() {
        return "ActionExtractionPreset[" + index + "]";
    }
}
