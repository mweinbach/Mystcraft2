package ports.jurassicraft.assembly;

import java.util.Map;
import ports.jurassicraft.content.Species;

/** Generated from the original dinosaur metadata by generate_assembly.py. */
final class AssemblyRecipeFixtures {
    static final Map<Species, String[][]> RECIPES = Map.ofEntries(
            Map.entry(Species.VELOCIRAPTOR, new String[][] {{"", "", "neck_vertebrae", "skull"}, {"tail_vertebrae", "ribcage", "shoulder_bone", "tooth"}, {"leg_bones", "leg_bones", "arm_bones", "claw"}, {"foot_bones", "foot_bones", "", ""}}),
            Map.entry(Species.COELACANTH, new String[][] {{"", "second_dorsal_fin", "first_dorsal_fin", ""}, {"caudal_fin", "spine", "pectoral_fin_bones", "skull"}, {"anal_fin", "", "pelvic_fin_bones", "teeth"}}),
            Map.entry(Species.MICRORAPTOR, new String[][] {{"", "", "", "neck_vertebrae", "skull"}, {"tail_vertebrae", "pelvis", "ribcage", "shoulder", "tooth"}, {"", "leg_bones", "leg_bones", "arm_bones", ""}, {"", "foot_bones", "foot_bones", "", ""}}),
            Map.entry(Species.BRACHIOSAURUS, new String[][] {{"", "", "", "", "skull"}, {"", "", "", "neck_vertebrae", "tooth"}, {"tail_vertebrae", "pelvis", "ribcage", "shoulder", ""}, {"", "hind_leg_bones", "hind_leg_bones", "front_leg_bones", "front_leg_bones"}}),
            Map.entry(Species.MUSSAURUS, new String[][] {{"", "pelvis", "", "", ""}, {"tail_vertebrae", "ribcage", "shoulder", "neck_vertebrae", "skull"}, {"leg_bones", "leg_bones", "arm_bones", "arm_bones", "teeth"}}),
            Map.entry(Species.DILOPHOSAURUS, new String[][] {{"", "", "", "neck", "skull"}, {"tail_vertebrae", "pelvis", "ribcage", "shoulder", "tooth"}, {"leg_bones", "leg_bones", "", "", "arm_bones"}}),
            Map.entry(Species.GALLIMIMUS, new String[][] {{"", "", "", "neck_vertebrae", "skull"}, {"tail_vertebrae", "pelvis", "ribcage", "shoulder", ""}, {"", "leg_bones", "leg_bones", "arm_bones", ""}, {"", "foot_bones", "foot_bones", "", ""}}),
            Map.entry(Species.PARASAUROLOPHUS, new String[][] {{"tail_vertebrae", "pelvis", "ribcage", "neck_vertebrae", "skull"}, {"hind_leg_bones", "hind_leg_bones", "", "shoulder_bone", "teeth"}, {"", "", "", "front_leg_bones", "front_leg_bones"}}),
            Map.entry(Species.TRICERATOPS, new String[][] {{"", "", "", "", "horn"}, {"tail_vertebrae", "pelvis", "ribcage", "neck_vertebrae", "skull"}, {"hind_leg_bones", "hind_leg_bones", "", "shoulder_bone", "tooth"}, {"", "", "", "front_leg_bones", "front_leg_bones"}}),
            Map.entry(Species.TYRANNOSAURUS, new String[][] {{"", "", "", "neck_vertebrae", "skull"}, {"tail_vertebrae", "pelvis", "ribcage", "shoulder_bone", "tooth"}, {"", "leg_bones", "leg_bones", "arm_bones", ""}, {"", "foot_bones", "foot_bones", "", ""}})
    );
    private AssemblyRecipeFixtures() {}
}
