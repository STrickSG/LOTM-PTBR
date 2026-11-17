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
