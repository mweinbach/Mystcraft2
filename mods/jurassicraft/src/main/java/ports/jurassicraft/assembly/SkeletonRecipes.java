package ports.jurassicraft.assembly;

import java.util.EnumMap;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import ports.jurassicraft.content.JurassicContent;
import ports.jurassicraft.content.Species;

/** The upstream five-by-five, unmirrored fossil and fresh skeleton layouts. */
public final class SkeletonRecipes {
    public record Match(Species species, boolean fresh) {}
    private static final Map<Species, String[][]> RECIPES = new EnumMap<>(Species.class);
    static {
        RECIPES.put(Species.VELOCIRAPTOR, new String[][] {
                {"", "", "neck_vertebrae", "skull"},
                {"tail_vertebrae", "ribcage", "shoulder_bone", "tooth"},
                {"leg_bones", "leg_bones", "arm_bones", "claw"},
                {"foot_bones", "foot_bones", "", ""}});
        RECIPES.put(Species.COELACANTH, new String[][] {
                {"", "second_dorsal_fin", "first_dorsal_fin", ""},
                {"caudal_fin", "spine", "pectoral_fin_bones", "skull"},
                {"anal_fin", "", "pelvic_fin_bones", "teeth"}});
        RECIPES.put(Species.MICRORAPTOR, new String[][] {
                {"", "", "", "neck_vertebrae", "skull"},
                {"tail_vertebrae", "pelvis", "ribcage", "shoulder", "tooth"},
                {"", "leg_bones", "leg_bones", "arm_bones", ""},
                {"", "foot_bones", "foot_bones", "", ""}});
        RECIPES.put(Species.BRACHIOSAURUS, new String[][] {
                {"", "", "", "", "skull"},
                {"", "", "", "neck_vertebrae", "tooth"},
                {"tail_vertebrae", "pelvis", "ribcage", "shoulder", ""},
                {"", "hind_leg_bones", "hind_leg_bones", "front_leg_bones", "front_leg_bones"}});
        RECIPES.put(Species.MUSSAURUS, new String[][] {
                {"", "pelvis", "", "", ""},
                {"tail_vertebrae", "ribcage", "shoulder", "neck_vertebrae", "skull"},
                {"leg_bones", "leg_bones", "arm_bones", "arm_bones", "teeth"}});
        RECIPES.put(Species.DILOPHOSAURUS, new String[][] {
                {"", "", "", "neck", "skull"},
                {"tail_vertebrae", "pelvis", "ribcage", "shoulder", "tooth"},
                {"leg_bones", "leg_bones", "", "", "arm_bones"}});
        RECIPES.put(Species.GALLIMIMUS, new String[][] {
                {"", "", "", "neck_vertebrae", "skull"},
                {"tail_vertebrae", "pelvis", "ribcage", "shoulder", ""},
                {"", "leg_bones", "leg_bones", "arm_bones", ""},
                {"", "foot_bones", "foot_bones", "", ""}});
        RECIPES.put(Species.PARASAUROLOPHUS, new String[][] {
                {"tail_vertebrae", "pelvis", "ribcage", "neck_vertebrae", "skull"},
                {"hind_leg_bones", "hind_leg_bones", "", "shoulder_bone", "teeth"},
                {"", "", "", "front_leg_bones", "front_leg_bones"}});
        RECIPES.put(Species.TRICERATOPS, new String[][] {
                {"", "", "", "", "horn"},
                {"tail_vertebrae", "pelvis", "ribcage", "neck_vertebrae", "skull"},
                {"hind_leg_bones", "hind_leg_bones", "", "shoulder_bone", "tooth"},
                {"", "", "", "front_leg_bones", "front_leg_bones"}});
        RECIPES.put(Species.TYRANNOSAURUS, new String[][] {
                {"", "", "", "neck_vertebrae", "skull"},
                {"tail_vertebrae", "pelvis", "ribcage", "shoulder_bone", "tooth"},
                {"", "leg_bones", "leg_bones", "arm_bones", ""},
                {"", "foot_bones", "foot_bones", "", ""}});
    }
    private SkeletonRecipes() {}

    /** Returns an independent copy of the actual recipe grid for recipe viewers. */
    public static String[][] layout(Species species) {
        String[][] recipe = RECIPES.get(species);
        if (recipe == null) return new String[0][];
        String[][] copy = new String[recipe.length][];
        for (int i = 0; i < recipe.length; i++) copy[i] = recipe[i].clone();
        return copy;
    }

    public static boolean isBone(ItemStack stack) {
        if (stack.isEmpty()) return false;
        return JurassicContent.BONES.values().stream().flatMap(map -> map.values().stream()).anyMatch(bone -> stack.is(bone.get()))
                || AssemblyRegistry.FRESH_BONES.values().stream().flatMap(map -> map.values().stream()).anyMatch(bone -> stack.is(bone.get()));
    }

    @Nullable public static Species match(Container inventory) {
        Match result = find(inventory);
        return result == null ? null : result.species();
    }

    @Nullable public static Match find(Container inventory) {
        for (var entry : RECIPES.entrySet()) {
            String[][] recipe = entry.getValue();
            int height = recipe.length, width = recipe[0].length;
            for (int top = 0; top <= 5 - height; top++) {
                for (int left = 0; left <= 5 - width; left++) {
                    if (matches(inventory, entry.getKey(), recipe, left, top, false)) return new Match(entry.getKey(), false);
                    if (matches(inventory, entry.getKey(), recipe, left, top, true)) return new Match(entry.getKey(), true);
                }
            }
        }
        return null;
    }

    private static boolean matches(Container inventory, Species species, String[][] recipe, int left, int top, boolean fresh) {
        for (int y = 0; y < 5; y++) for (int x = 0; x < 5; x++) {
            int rx = x - left, ry = y - top;
            String bone = ry >= 0 && ry < recipe.length && rx >= 0 && rx < recipe[0].length ? recipe[ry][rx] : "";
            ItemStack stack = inventory.getItem(x + y * 5);
            if (bone.isEmpty()) {
                if (!stack.isEmpty()) return false;
            } else {
                var expected = (fresh ? AssemblyRegistry.FRESH_BONES : JurassicContent.BONES).get(species).get(bone);
                if (expected == null || !stack.is(expected.get())) return false;
            }
        }
        return true;
    }
}
