package ports.jurassicraft.scanner;

import java.util.List;
import java.util.Locale;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import ports.jurassicraft.entity.DinosaurEntity;

/** Server-side diagnostic report, with the upstream debug scanner's sneak-use behavior. */
public final class DinoScannerItem extends Item {
    private static final String PREFIX = "message.jurassicraft.dino_scanner.";
    private static final int SNEAK_DRAIN = 5000;
    public DinoScannerItem(Properties properties) { super(properties); }

    @Override public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (!(target instanceof DinosaurEntity dinosaur)) return InteractionResult.PASS;
        if (!player.level.isClientSide) {
            boolean drained = player.isShiftKeyDown();
            if (drained) dinosaur.reduceEnergy(SNEAK_DRAIN);
            Component sex = Component.translatable(PREFIX + (dinosaur.isMale() ? "male" : "female"));
            Component report = Component.translatable(PREFIX + "header", dinosaur.getDisplayName()).withStyle(ChatFormatting.GOLD)
                    .append("\n").append(Component.translatable(PREFIX + "health", Math.round(displayHealth(dinosaur)), Math.round(dinosaur.getMaxHealth())))
                    .append("\n").append(Component.translatable(PREFIX + "growth", Math.round(dinosaur.growth() * 100), sex))
                    .append("\n").append(Component.translatable(PREFIX + "energy", dinosaur.energy(), dinosaur.maximumEnergy()))
                    .append("\n").append(Component.translatable(PREFIX + "water", dinosaur.water(), dinosaur.maximumWater()))
                    .append("\n").append(Component.translatable(PREFIX + "dna", dinosaur.getDNAQuality(), dinosaur.getGenetics()));
            if (drained) report = report.copy().append("\n").append(Component.translatable(PREFIX + "drained", SNEAK_DRAIN).withStyle(ChatFormatting.YELLOW));
            String dump = dump(dinosaur);
            Component copy = Component.translatable(PREFIX + "copy").withStyle(style -> style.withColor(ChatFormatting.AQUA)
                    .withUnderlined(true).withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, dump))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable(PREFIX + "copy_hover"))));
            player.sendSystemMessage(report.copy().append("\n").append(copy));
        }
        return InteractionResult.sidedSuccess(player.level.isClientSide);
    }

    private static String dump(DinosaurEntity dinosaur) {
        return String.format(Locale.ROOT,
                "Species: %s\nUUID: %s\nOwner: %s\nHealth: %.1f / %.1f\nGrowth: %d%%\nSex: %s\nEnergy: %d / %d\nWater: %d / %d\nDNA quality: %d%%\nGenetics: %s\nPosition: %.1f, %.1f, %.1f",
                dinosaur.species().id(), dinosaur.getUUID(), dinosaur.getOwnerUUID(), displayHealth(dinosaur), dinosaur.getMaxHealth(),
                Math.round(dinosaur.growth() * 100), dinosaur.isMale() ? "male" : "female", dinosaur.energy(), dinosaur.maximumEnergy(),
                dinosaur.water(), dinosaur.maximumWater(),
                dinosaur.getDNAQuality(), dinosaur.getGenetics(), dinosaur.getX(), dinosaur.getY(), dinosaur.getZ());
    }

    private static float displayHealth(DinosaurEntity dinosaur) {
        return dinosaur.isCarcass() ? 0 : dinosaur.getHealth();
    }

    @Override public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.jurassicraft.dino_scanner.scan").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.jurassicraft.dino_scanner.sneak").withStyle(ChatFormatting.YELLOW));
    }
}
