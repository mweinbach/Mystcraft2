package ports.jurassicraft.journal;

import java.util.Locale;

/** The nine journal authors in their original metadata order. */
public enum JournalType {
    CHEF_ALEJANDRO, DENNIS_NEDRY, DR_GERRY_HARDING, DR_HENRY_WU, DR_LAURA_SORKIN,
    ED_REGIS, JOHN_HAMMOND, RAY_ARNOLD, ROBERT_MULDOON;
    public String id() { return name().toLowerCase(Locale.ROOT); }
    public static JournalType byId(String id) {
        for (JournalType type : values()) if (type.id().equals(id)) return type;
        return CHEF_ALEJANDRO;
    }
}
