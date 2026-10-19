package net.mystcraft.minecraft.fluid;
/** Loader-neutral millibucket operations. Simulation must not dirty state or mutate inventory. */
public interface InkTank {
    int inkAmount();
    int inkCapacity();
    int fillInk(int maximum,boolean execute);
    int drainInk(int maximum,boolean execute);
    void setInkAmount(int amount,boolean dirty);
}
