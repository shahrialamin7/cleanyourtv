package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * The values the owner changed on the TV, kept apart from the theme.
 *
 * <p>Every value here is an explicit user choice. {@link Config} merges these over the theme's
 * {@code config.json}, so a theme push changes the defaults but never silently discards a choice
 * made in Launcher settings. {@code Follow theme} clears them on purpose.
 *
 * <p>The store is a plain {@link SharedPreferences} file: no dependency, no background work, and
 * the same process-wide revision counter Home already watches in {@code onResume}.
 */
final class SettingsStore {
    /** Keys that describe the store itself, never a launcher setting. */
    private static final Set<String> BOOKKEEPING = new HashSet<>(Arrays.asList(
            Config.SETTINGS_REVISION_KEY, "theme_signature"));

    private final SharedPreferences prefs;

    private SettingsStore(SharedPreferences prefs) {
        this.prefs = prefs;
    }

    static SettingsStore of(Context ctx) {
        return new SettingsStore(ctx.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE));
    }

    boolean has(String key) {
        return prefs.contains(key);
    }

    void putBoolean(String key, boolean value) {
        prefs.edit().putBoolean(key, value).apply();
    }

    void putInt(String key, int value) {
        prefs.edit().putInt(key, value).apply();
    }

    void putString(String key, String value) {
        prefs.edit().putString(key, value).apply();
    }

    void remove(String key) {
        prefs.edit().remove(key).apply();
    }

    long revision() {
        return prefs.getLong(Config.SETTINGS_REVISION_KEY, 0);
    }

    /** Home rebuilds on resume when this moves, however the settings were left. */
    void bumpRevision() {
        prefs.edit().putLong(Config.SETTINGS_REVISION_KEY, revision() + 1).apply();
    }

    /** Settings that override the theme, as the same keys and JSON types a theme uses. */
    JSONObject overridesJson() {
        JSONObject out = new JSONObject();
        for (Map.Entry<String, ?> entry : prefs.getAll().entrySet()) {
            String key = entry.getKey();
            if (BOOKKEEPING.contains(key)) continue;
            Object value = entry.getValue();
            if (value instanceof Boolean || value instanceof Integer || value instanceof String) {
                put(out, key, value);
            }
        }
        return out;
    }

    /** Everything the owner chose, for a backup file. Bookkeeping keys stay out. */
    JSONObject exportJson() {
        JSONObject out = overridesJson();
        put(out, "settings_schema", 1);
        return out;
    }

    /**
     * Restores a backup. Unknown keys are kept, so a backup from a newer build still restores what
     * this build understands; a value with the wrong JSON type is skipped rather than coerced.
     *
     * @return how many keys were written
     */
    int importJson(JSONObject in) {
        if (in == null) return 0;
        SharedPreferences.Editor editor = prefs.edit();
        int written = 0;
        for (Iterator<String> it = in.keys(); it.hasNext(); ) {
            String key = it.next();
            if (BOOKKEEPING.contains(key) || "settings_schema".equals(key) || key.length() > 64) continue;
            Object value = in.opt(key);
            if (value instanceof Boolean) {
                editor.putBoolean(key, (Boolean) value);
                written++;
            } else if (value instanceof Integer) {
                editor.putInt(key, (Integer) value);
                written++;
            } else if (value instanceof String && ((String) value).length() <= 4096) {
                editor.putString(key, (String) value);
                written++;
            }
        }
        editor.putLong(Config.SETTINGS_REVISION_KEY, revision() + 1).apply();
        return written;
    }

    /** Follow theme: forget every on-TV choice, keeping the revision counter. */
    void clearOverrides() {
        SharedPreferences.Editor editor = prefs.edit();
        for (String key : prefs.getAll().keySet()) {
            if (!BOOKKEEPING.contains(key)) editor.remove(key);
        }
        editor.putLong(Config.SETTINGS_REVISION_KEY, revision() + 1).apply();
    }

    /**
     * Theme defaults with the owner's explicit choices on top. Neither input is modified.
     * Returns {@code theme} unchanged when there is nothing to override.
     */
    static JSONObject merge(JSONObject theme, JSONObject overrides) {
        if (overrides == null || overrides.length() == 0) return theme;
        JSONObject out = new JSONObject();
        if (theme != null) {
            for (Iterator<String> it = theme.keys(); it.hasNext(); ) {
                String key = it.next();
                put(out, key, theme.opt(key));
            }
        }
        for (Iterator<String> it = overrides.keys(); it.hasNext(); ) {
            String key = it.next();
            put(out, key, overrides.opt(key));
        }
        return out;
    }

    private static void put(JSONObject out, String key, Object value) {
        try {
            out.put(key, value == null ? JSONObject.NULL : value);
        } catch (Exception ignored) {
            // A key JSON cannot hold says nothing about the others; leave it out.
        }
    }
}
