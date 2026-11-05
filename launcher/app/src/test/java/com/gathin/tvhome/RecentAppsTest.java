package com.gathin.tvhome;

import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RecentAppsTest {
    @Test
    public void mergeKeepsTheNewestPerPackageAndCapsTheShelf() {
        Map<String, Long> usage = new HashMap<>();
        usage.put("com.example.old", 100L);
        usage.put("com.example.new", 300L);
        usage.put("com.example.mid", 200L);
        List<RecentApps.Entry> local = new ArrayList<>();
        local.add(new RecentApps.Entry("com.example.old", 400L)); // Home opened it later than usage says
        local.add(new RecentApps.Entry("com.example.extra", 50L));

        List<RecentApps.Entry> merged = RecentApps.merge(local, usage, 0L, 3);

        assertEquals(3, merged.size());
        assertEquals("com.example.old", merged.get(0).pkg);
        assertEquals(400L, merged.get(0).at);
        assertEquals("com.example.new", merged.get(1).pkg);
        assertEquals("com.example.mid", merged.get(2).pkg);
    }

    @Test
    public void mergeDropsEntriesAtOrBeforeTheClearMark() {
        Map<String, Long> usage = new HashMap<>();
        usage.put("com.example.before", 99L);
        usage.put("com.example.after", 101L);

        List<RecentApps.Entry> merged = RecentApps.merge(new ArrayList<>(), usage, 100L, 12);

        assertEquals(1, merged.size());
        assertEquals("com.example.after", merged.get(0).pkg);
    }

    @Test
    public void mergeIsStableWhenTimesMatch() {
        Map<String, Long> usage = new HashMap<>();
        usage.put("com.example.b", 100L);
        usage.put("com.example.a", 100L);

        List<RecentApps.Entry> merged = RecentApps.merge(new ArrayList<>(), usage, 0L, 12);

        assertEquals("com.example.a", merged.get(0).pkg);
        assertEquals("com.example.b", merged.get(1).pkg);
    }

    @Test
    public void systemPackagesAreNeverKilled() {
        assertTrue(RecentApps.protectedName("com.android.systemui"));
        assertTrue(RecentApps.protectedName("com.android.settings"));
        assertTrue(RecentApps.protectedName("com.google.android.tvlauncher"));
        assertTrue(RecentApps.protectedName("com.android.inputmethod.latin"));
        assertTrue(RecentApps.protectedName("android"));
        assertFalse(RecentApps.protectedName("com.netflix.ninja"));
        assertFalse(RecentApps.protectedName("com.example.player"));
    }
}
