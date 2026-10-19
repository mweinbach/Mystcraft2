/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.block.property;

import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import ports.extremereactors.corelib.block.BlockFacings;

/**
 * Every combination of the six block faces, as a block state property value. The serialized name is a shape
 * category followed by the initials of the set faces in alphabetical order (d, e, n, s, u, w).
 * Constants are declared in {@link BlockFacings} bit-mask order (down, up, north, south, west, east).
 */
public enum BlockFacingsProperty
        implements StringRepresentable {

    None("none", 0),
    FaceD("face_d", 1),
    FaceU("face_u", 2),
    OppositeDU("opposite_du", 3),
    FaceN("face_n", 4),
    AngleDN("angle_dn", 5),
    AngleNU("angle_nu", 6),
    CshapeDNU("cshape_dnu", 7),
    FaceS("face_s", 8),
    AngleDS("angle_ds", 9),
    AngleSU("angle_su", 10),
    CshapeDSU("cshape_dsu", 11),
    OppositeNS("opposite_ns", 12),
    CshapeDNS("cshape_dns", 13),
    CshapeNSU("cshape_nsu", 14),
    PipeDNSU("pipe_dnsu", 15),
    FaceW("face_w", 16),
    AngleDW("angle_dw", 17),
    AngleUW("angle_uw", 18),
    CshapeDUW("cshape_duw", 19),
    AngleNW("angle_nw", 20),
    CornerDNW("corner_dnw", 21),
    CornerNUW("corner_nuw", 22),
    MiscDNUW("misc_dnuw", 23),
    AngleSW("angle_sw", 24),
    CornerDSW("corner_dsw", 25),
    CornerSUW("corner_suw", 26),
    MiscDSUW("misc_dsuw", 27),
    CshapeNSW("cshape_nsw", 28),
    MiscDNSW("misc_dnsw", 29),
    MiscNSUW("misc_nsuw", 30),
    PipeendDNSUW("pipeend_dnsuw", 31),
    FaceE("face_e", 32),
    AngleDE("angle_de", 33),
    AngleEU("angle_eu", 34),
    CshapeDEU("cshape_deu", 35),
    AngleEN("angle_en", 36),
    CornerDEN("corner_den", 37),
    CornerENU("corner_enu", 38),
    MiscDENU("misc_denu", 39),
    AngleES("angle_es", 40),
    CornerDES("corner_des", 41),
    CornerESU("corner_esu", 42),
    MiscDESU("misc_desu", 43),
    CshapeENS("cshape_ens", 44),
    MiscDENS("misc_dens", 45),
    MiscENSU("misc_ensu", 46),
    PipeendDENSU("pipeend_densu", 47),
    OppositeEW("opposite_ew", 48),
    CshapeDEW("cshape_dew", 49),
    CshapeEUW("cshape_euw", 50),
    PipeDEUW("pipe_deuw", 51),
    CshapeENW("cshape_enw", 52),
    MiscDENW("misc_denw", 53),
    MiscENUW("misc_enuw", 54),
    PipeendDENUW("pipeend_denuw", 55),
    CshapeESW("cshape_esw", 56),
    MiscDESW("misc_desw", 57),
    MiscESUW("misc_esuw", 58),
    PipeendDESUW("pipeend_desuw", 59),
    PipeENSW("pipe_ensw", 60),
    PipeendDENSW("pipeend_densw", 61),
    PipeendENSUW("pipeend_ensuw", 62),
    All("all", 63);

    public static final EnumProperty<BlockFacingsProperty> FACINGS = EnumProperty.create("facings", BlockFacingsProperty.class);

    public static BlockFacingsProperty fromMask(final int mask) {
        return VALUES[mask & 0x3f];
    }

    public int getMask() {
        return this._mask;
    }

    public BlockFacings toFacings() {
        return BlockFacings.fromMask(this._mask);
    }

    @Override
    public String getSerializedName() {
        return this._name;
    }

    BlockFacingsProperty(final String name, final int mask) {

        this._name = name;
        this._mask = mask;
    }

    private static final BlockFacingsProperty[] VALUES = values();

    private final String _name;
    private final int _mask;
}
