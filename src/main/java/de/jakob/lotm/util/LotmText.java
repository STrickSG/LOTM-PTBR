package de.jakob.lotm.util;

import net.minecraft.locale.Language;

/**
 * Texts that are built as plain Strings (drawn on screens, measured with Font#width, stored in labels...).
 * Looks the key up in the active language and falls back to the English text, so it is safe on both sides:
 * on a dedicated server, where the mod's language files are not loaded, the English fallback is used.
 * Texts that end up in a Component use Component.translatableWithFallback instead, which is translated on the
 * client of whoever reads it.
 */
public final class LotmText {
    private LotmText() {}

    // English text -> key, for display data built once at class load (before any language is loaded)
    private static final java.util.Map<String, String> REGISTERED = new java.util.concurrent.ConcurrentHashMap<>();

    static {
        // Compass directions used as data (danger arrows, divination); translated where shown
        reg("lotmcraft.text.direction.north", "North");
        reg("lotmcraft.text.direction.north_east", "North-East");
        reg("lotmcraft.text.direction.east", "East");
        reg("lotmcraft.text.direction.south_east", "South-East");
        reg("lotmcraft.text.direction.south", "South");
        reg("lotmcraft.text.direction.south_west", "South-West");
        reg("lotmcraft.text.direction.west", "West");
        reg("lotmcraft.text.direction.north_west", "North-West");
    }

    /**
     * For static data (records, enum constants, lists built at class load): keeps the English text as the
     * stored value and remembers its key. Translate it where it is shown with {@link #tr(String)}; translating
     * here would freeze the text in whatever language was active when the class loaded (usually none yet).
     */
    public static String reg(String key, String english) {
        REGISTERED.put(english, key);
        return english;
    }

    /** Like {@link #tr} but as a Component, so it is translated on the client of whoever reads it. */
    public static net.minecraft.network.chat.MutableComponent trc(String english) {
        if (english == null) return net.minecraft.network.chat.Component.empty();
        String key = REGISTERED.get(english);
        return key == null ? net.minecraft.network.chat.Component.literal(english)
                : net.minecraft.network.chat.Component.translatableWithFallback(key, english);
    }

    /** Translation of a text stored with {@link #reg}; any other text is returned unchanged. */
    public static String tr(String english) {
        if (english == null) return null;
        String key = REGISTERED.get(english);
        return key == null ? english : t(key, english);
    }

    /** Translated text for {@code key}, or {@code fallback} when the key is unknown. */
    public static String t(String key, String fallback) {
        return Language.getInstance().getOrDefault(key, fallback);
    }

    /** Translated format for {@code key} (or {@code fallback}) filled with {@code args} like String.format. */
    public static String f(String key, String fallback, Object... args) {
        String format = t(key, fallback);
        try {
            return String.format(format, args);
        } catch (java.util.IllegalFormatException e) {
            return String.format(fallback, args);
        }
    }
}
