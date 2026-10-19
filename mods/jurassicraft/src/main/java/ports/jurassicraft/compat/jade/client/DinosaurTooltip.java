package ports.jurassicraft.compat.jade.client;

import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import ports.jurassicraft.compat.jade.InspectionData;
import ports.jurassicraft.compat.jade.ServerProviders;
import ports.jurassicraft.entity.DinosaurEntity;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.IEntityComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

@OnlyIn(Dist.CLIENT)
public enum DinosaurTooltip implements IEntityComponentProvider {
    INSTANCE;
    @Override public ResourceLocation getUid() { return ServerProviders.DINOSAUR_ID; }
    @Override public void appendTooltip(ITooltip tooltip, EntityAccessor accessor, IPluginConfig config) {
        if (!(accessor.getEntity() instanceof DinosaurEntity dinosaur)) return;
        CompoundTag data = accessor.getServerData().contains(ServerProviders.DINOSAUR_DATA, Tag.TAG_COMPOUND)
                ? accessor.getServerData().getCompound(ServerProviders.DINOSAUR_DATA) : InspectionData.dinosaur(dinosaur, null);
        Component gender = Component.translatable("mode.jurassicraft." + (data.getBoolean("Male") ? "male" : "female"));
        Component owner = Component.translatable("tooltip.jurassicraft.jade.not_imprinted");
        if (data.hasUUID("Owner")) {
            String name = data.getString("OwnerName");
            if (name.isEmpty()) {
                Player online = accessor.getLevel().getPlayerByUUID(data.getUUID("Owner"));
                name = online == null ? data.getUUID("Owner").toString() : online.getName().getString();
            }
            owner = Component.literal(name);
        }
        String order = data.getString("Order").toLowerCase(Locale.ROOT);
        if (!order.equals("wander") && !order.equals("follow") && !order.equals("sit")) order = "wander";
        tooltip.add(line("tooltip.jurassicraft.jade.gender", gender));
        tooltip.add(line("tooltip.jurassicraft.jade.owner", owner));
        tooltip.add(line("tooltip.jurassicraft.jade.order", Component.translatable("order.jurassicraft." + order)));
    }
    private static Component line(String key, Component value) {
        return Component.translatable(key).withStyle(ChatFormatting.GOLD).append(Component.literal(": ").withStyle(ChatFormatting.GOLD))
                .append(value.copy().withStyle(ChatFormatting.WHITE));
    }
}
