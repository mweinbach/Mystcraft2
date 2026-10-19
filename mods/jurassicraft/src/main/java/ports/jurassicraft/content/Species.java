package ports.jurassicraft.content;

import java.util.List;

/** Species and fossil bone sets registered by the upstream 1.12.2 release. */
public enum Species {
    VELOCIRAPTOR(0, "cretaceous", "claw", "tooth", "skull", "foot_bones", "leg_bones", "neck_vertebrae", "ribcage", "shoulder_bone", "tail_vertebrae", "arm_bones"),
    COELACANTH(1, "devonian", "anal_fin", "caudal_fin", "first_dorsal_fin", "pectoral_fin_bones", "pelvic_fin_bones", "second_dorsal_fin", "skull", "spine", "teeth"),
    MICRORAPTOR(2, "cretaceous", "tooth", "arm_bones", "foot_bones", "leg_bones", "neck_vertebrae", "pelvis", "ribcage", "shoulder", "skull", "tail_vertebrae"),
    BRACHIOSAURUS(3, "jurassic", "skull", "tooth", "tail_vertebrae", "shoulder", "ribcage", "pelvis", "neck_vertebrae", "hind_leg_bones", "front_leg_bones"),
    MUSSAURUS(4, "triassic", "arm_bones", "leg_bones", "neck_vertebrae", "pelvis", "ribcage", "shoulder", "skull", "tail_vertebrae", "teeth"),
    DILOPHOSAURUS(7, "jurassic", "skull", "tooth", "arm_bones", "leg_bones", "neck", "pelvis", "ribcage", "shoulder", "tail_vertebrae"),
    GALLIMIMUS(9, "cretaceous", "skull", "tail_vertebrae", "shoulder", "ribcage", "pelvis", "neck_vertebrae", "leg_bones", "foot_bones", "arm_bones"),
    PARASAUROLOPHUS(13, "cretaceous", "ribcage", "front_leg_bones", "hind_leg_bones", "neck_vertebrae", "pelvis", "shoulder_bone", "skull", "tail_vertebrae", "teeth"),
    TRICERATOPS(19, "cretaceous", "front_leg_bones", "hind_leg_bones", "horn", "neck_vertebrae", "pelvis", "ribcage", "shoulder_bone", "skull", "tail_vertebrae", "tooth"),
    TYRANNOSAURUS(20, "cretaceous", "arm_bones", "foot_bones", "leg_bones", "neck_vertebrae", "pelvis", "ribcage", "shoulder_bone", "skull", "tail_vertebrae", "tooth");

    private final int legacyId;
    private final String period;
    private final List<String> bones;

    Species(int legacyId, String period, String... bones) {
        this.legacyId = legacyId;
        this.period = period;
        this.bones = List.of(bones);
    }

    public String id() { return name().toLowerCase(java.util.Locale.ROOT); }
    public int legacyId() { return legacyId; }
    public String period() { return period; }
    public List<String> bones() { return bones; }
}
