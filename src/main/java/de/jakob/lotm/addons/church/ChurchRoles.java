package de.jakob.lotm.addons.church;

import java.util.List;

/**
 * Os cargos dados no altar. Na Igreja seguem a hierarquia das igrejas da novel; na Seita, cargos de seita.
 * "believer" é o fiel comum. O limite é por Igreja inteira (papa, líder, arcebispos) ou por colônia (bispo).
 */
public final class ChurchRoles {
    private ChurchRoles() {}

    public static final List<String> CHURCH = List.of("believer", "priest", "deacon", "bishop", "archbishop", "pope");
    public static final List<String> SECT = List.of("believer", "acolyte", "sect_priest", "high_priest", "leader");

    public static List<String> of(String kind) {
        return Churches.SECT.equals(kind) ? SECT : CHURCH;
    }

    /** Quantos podem ter o cargo: em toda a Igreja (positivo) ou por colônia (negativo) */
    public static int limit(String role) {
        return switch (role) {
            case "pope", "leader" -> 1;
            case "archbishop" -> 13;
            case "high_priest" -> 13;
            case "bishop" -> -1;
            default -> Integer.MAX_VALUE;
        };
    }

    /** Clero: ajuda a converter os colonos da colônia onde mora */
    public static boolean isClergy(String role) {
        return !"believer".equals(role);
    }
}
