package com.gathin.tvhome;

import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

/** The precedence rule behind Launcher settings: an explicit choice outranks the theme. */
public class SettingsStoreMergeTest {
    @Test
    public void overrideWinsOverTheme() throws Exception {
        JSONObject theme = new JSONObject().put("columns", 6).put("dock_row", true);
        JSONObject user = new JSONObject().put("columns", 4);

        JSONObject merged = SettingsStore.merge(theme, user);

        assertEquals(4, merged.getInt("columns"));
        assertEquals(true, merged.getBoolean("dock_row"));
    }

    @Test
    public void themeStaysWhenNothingIsOverridden() {
        JSONObject theme = new JSONObject().put("columns", 6);
        assertSame(theme, SettingsStore.merge(theme, new JSONObject()));
        assertSame(theme, SettingsStore.merge(theme, null));
    }

    @Test
    public void overridesWorkWithoutATheme() throws Exception {
        JSONObject merged = SettingsStore.merge(null, new JSONObject().put("columns", 5));
        assertEquals(5, merged.getInt("columns"));
    }

    @Test
    public void mergeDoesNotTouchEitherInput() throws Exception {
        JSONObject theme = new JSONObject().put("columns", 6);
        JSONObject user = new JSONObject().put("columns", 4);

        SettingsStore.merge(theme, user);

        assertEquals(6, theme.getInt("columns"));
        assertEquals(4, user.getInt("columns"));
    }

    @Test
    public void emptyInputsStayEmpty() {
        assertNull(SettingsStore.merge(null, null));
    }
}
