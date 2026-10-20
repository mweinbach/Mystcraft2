/*
 * Extreme Reactors 1.19.2 port - EMI integration
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.compat.emi;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.emi.emi.api.stack.EmiStack;
import it.zerono.mods.extremereactors.ExtremeReactors;
import it.zerono.mods.extremereactors.api.coolant.Coolant;
import it.zerono.mods.extremereactors.api.coolant.Vapor;
import it.zerono.mods.extremereactors.api.internal.AbstractNamedValue;
import it.zerono.mods.extremereactors.api.reactor.Reactant;
import it.zerono.mods.extremereactors.gamecontent.multiblock.common.client.screen.CachedSprites;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import ports.extremereactors.corelib.client.gui.sprite.ISprite;
import ports.extremereactors.corelib.client.render.ModRenderHelper;
import ports.extremereactors.corelib.data.gfx.Colour;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Supplier;

/**
 * A reactant, coolant or vapor as an EMI stack, drawn the way the JEI plugin draws them: a tinted fluid square.
 */
final class NamedValueEmiStack extends EmiStack {

    static NamedValueEmiStack of(final Reactant reactant) {
        return new NamedValueEmiStack("reactant", reactant, reactant.getColour(), CachedSprites.REACTOR_FUEL_COLUMN_FLOWING);
    }

    static NamedValueEmiStack of(final Coolant coolant) {
        return new NamedValueEmiStack("coolant", coolant, coolant.getColour(), CachedSprites.WATER_SOURCE);
    }

    static NamedValueEmiStack of(final Vapor vapor) {
        return new NamedValueEmiStack("vapor", vapor, vapor.getColour(), CachedSprites.WATER_SOURCE);
    }

    //region EmiStack

    @Override
    public EmiStack copy() {

        final NamedValueEmiStack copy = new NamedValueEmiStack(this._kind, this._value, this._colour, this._sprite);

        copy.setAmount(this.amount);
        copy.setChance(this.chance);
        return copy;
    }

    @Override
    public boolean isEmpty() {
        return false;
    }

    @Override
    public void render(final PoseStack matrix, final int x, final int y, final float delta, final int flags) {

        if (0 != (flags & RENDER_ICON)) {
            ModRenderHelper.paintVerticalProgressSprite(matrix, this._sprite.get(), this._colour, x, y, 0, 16, 16, 0, 1.0d);
        }
    }

    @Nullable
    @Override
    public CompoundTag getNbt() {
        return null;
    }

    @Override
    public Object getKey() {
        return this._value;
    }

    @Override
    public ResourceLocation getId() {
        return ExtremeReactors.newID(this._kind + "_" + this._value.getName());
    }

    @Override
    public List<Component> getTooltipText() {
        return List.of(this.getName());
    }

    @Override
    public Component getName() {
        return this._value.getTranslatedName();
    }

    //endregion
    //region internals

    private NamedValueEmiStack(final String kind, final AbstractNamedValue value, final Colour colour,
                               final Supplier<ISprite> sprite) {

        this._kind = kind;
        this._value = value;
        this._colour = colour;
        this._sprite = sprite;
    }

    private final String _kind;
    private final AbstractNamedValue _value;
    private final Colour _colour;
    private final Supplier<ISprite> _sprite;

    //endregion
}
