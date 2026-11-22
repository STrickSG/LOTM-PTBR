package de.jakob.lotm.addons.church;

import java.util.Map;

/**
 * Os modelos de templo do pacote de estilo LOTM: cada pasta de plantas é de um Caminho. Um templo só pode ser
 * consagrado por um Deus do Caminho do modelo (a Igreja do Sol é do Sol). Os membros é que podem vir de qualquer
 * Caminho numa Igreja; numa Seita, não.
 */
public final class ChurchModels {
    private ChurchModels() {}

    /** Pasta da planta -> Caminho */
    /** O pacote de estilos com as igrejas e a planta padrão (Igreja do Sol, nível 1) */
    public static final String PACK = "LOTM";
    public static final String DEFAULT_PATH = "sol/church1.blueprint";

    private static final Map<String, String> FOLDERS = Map.of(
            "sol", "sun"
    );

    /** O esconderijo da Seita ou da Ordem ("sol/hideout1.blueprint"): por fora, uma casa comum */
    public static boolean isHideout(String blueprintPath) {
        return blueprintPath != null && blueprintPath.replace('\\', '/').contains("/hideout");
    }

    /** O Caminho do modelo deste caminho de planta ("sol/church1.blueprint" -> "sun"), ou null se não for um modelo do pacote */
    public static String pathwayOf(String blueprintPath) {
        if (blueprintPath == null || blueprintPath.isEmpty()) return null;
        String path = blueprintPath.replace('\\', '/');
        if (path.startsWith("/")) path = path.substring(1);
        int slash = path.indexOf('/');
        if (slash < 0) return null;
        return FOLDERS.get(path.substring(0, slash));
    }
}
