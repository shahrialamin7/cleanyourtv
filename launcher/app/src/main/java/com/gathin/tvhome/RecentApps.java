package com.gathin.tvhome;

import android.app.ActivityManager;
import android.app.AppOpsManager;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.os.Process;
import android.provider.Settings;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The "Recent apps" list and its best-effort background cleanup.
 *
 * <p>Two sources are merged, newest first: the apps Home itself opened (kept in this class, no
 * permission) and Android's usage stats (needs the "Usage access" special app-op, which the user
 * grants on the TV). The local half is what makes the list useful immediately after install; the
 * usage half catches apps started by a remote's own Netflix/YouTube button or by another app.
 *
 * <p>{@link #clearAll} asks the system to kill each app's background processes. That is the strongest
 * thing a normal app can do on Android 11 without root or a device owner: foreground services and
 * apps the system considers important can come straight back, so the UI says "asked" and never
 * claims a force-stop.
 */
final class RecentApps {
    static final int MAX = 12;

    private static final String PREFS = "recent_apps";
    private static final String KEY_LOCAL = "local_v1";
    private static final String KEY_HIDDEN = "hidden_v1";
    private static final String KEY_CLEARED = "cleared_at";
    private static final long WEEK_MS = 7L * 24 * 60 * 60 * 1000;

    /** System packages whose background processes are never killed. Prefix match, see {@link #protectedName}. */
    private static final String[] PROTECTED_PREFIXES = {
            "com.android.systemui",
            "com.android.settings",
            "com.android.shell",
            "com.android.providers",
            "com.android.bluetooth",
            "com.android.phone",
            "com.android.nfc",
            "com.android.keychain",
            "com.android.certinstaller",
            "com.android.packageinstaller",
            "com.android.tv",
            "com.google.android.tv",
            "com.google.android.tvlauncher",
            "com.google.android.gms",
            "com.google.android.gsf",
            "com.google.android.packageinstaller",
            "com.google.android.inputmethod",
            "com.android.inputmethod",
    };

    private RecentApps() {
    }

    static final class Entry {
        final String pkg;
        final long at;

        Entry(String pkg, long at) {
            this.pkg = pkg;
            this.at = at;
        }
    }

    /** Notes that Home (or the recents list) launched this app. Cheap: one small JSON write. */
    static void record(Context ctx, String pkg) {
        if (ctx == null || pkg == null || pkg.isEmpty() || pkg.equals(ctx.getPackageName())) return;
        List<Entry> entries = local(ctx);
        List<Entry> out = new ArrayList<>();
        out.add(new Entry(pkg, System.currentTimeMillis()));
        for (Entry e : entries) {
            if (!e.pkg.equals(pkg)) out.add(e);
        }
        if (out.size() > MAX * 2) out = new ArrayList<>(out.subList(0, MAX * 2));
        writeLocal(ctx, out);
        unhide(ctx, pkg);
    }

    /** Records the package an intent will open, when it resolves to one. */
    static void record(Context ctx, Intent intent) {
        if (ctx == null || intent == null) return;
        String pkg = intent.getPackage();
        if (pkg == null && intent.getComponent() != null) pkg = intent.getComponent().getPackageName();
        if (pkg == null) {
            try {
                ResolveInfo ri = ctx.getPackageManager().resolveActivity(intent, 0);
                if (ri != null) pkg = ri.activityInfo.packageName;
            } catch (Exception ignored) {
            }
        }
        record(ctx, pkg);
    }

    /** Newest first, at most {@link #MAX}, launchable apps only, with cleared/hidden entries removed. */
    static List<Entry> list(Context ctx) {
        if (ctx == null) return Collections.emptyList();

        Map<String, Long> byPkg = new HashMap<>();
        for (Entry e : local(ctx)) {
            Long old = byPkg.get(e.pkg);
            if (old == null || e.at > old) byPkg.put(e.pkg, e.at);
        }
        try {
            UsageStatsManager usm = (UsageStatsManager) ctx.getSystemService(Context.USAGE_STATS_SERVICE);
            if (usm != null) {
                long now = System.currentTimeMillis();
                List<UsageStats> stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY,
                        now - WEEK_MS, now);
                if (stats != null) {
                    for (UsageStats s : stats) {
                        String pkg = s.getPackageName();
                        long at = s.getLastTimeUsed();
                        if (pkg == null || pkg.isEmpty() || at <= 0) continue;
                        Long old = byPkg.get(pkg);
                        if (old == null || at > old) byPkg.put(pkg, at);
                    }
                }
            }
        } catch (Exception ignored) {
            // No usage access, or an OEM with a broken stats service: the local half still works.
        }

        long cleared = prefs(ctx).getLong(KEY_CLEARED, 0);
        Map<String, Long> hidden = hidden(ctx);
        PackageManager pm = ctx.getPackageManager();
        Map<String, Long> visible = new HashMap<>();
        for (Map.Entry<String, Long> e : byPkg.entrySet()) {
            String pkg = e.getKey();
            long at = e.getValue();
            if (at <= cleared) continue;
            Long hiddenAt = hidden.get(pkg);
            if (hiddenAt != null && at <= hiddenAt) continue;
            if (!displayable(pm, ctx, pkg)) continue;
            visible.put(pkg, at);
        }
        return merge(Collections.<Entry>emptyList(), visible, 0, MAX);
    }

    /**
     * Asks the system to kill every listed app's background processes, then hides the current list.
     * Returns how many packages were asked to stop. Never throws.
     */
    static int clearAll(Context ctx) {
        if (ctx == null) return 0;
        List<Entry> entries = list(ctx);
        Set<String> protectedPkgs = protectedPackages(ctx);
        ActivityManager am = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
        int asked = 0;
        for (Entry e : entries) {
            if (protectedPkgs.contains(e.pkg) || protectedName(e.pkg)) continue;
            try {
                if (am != null) am.killBackgroundProcesses(e.pkg);
                asked++;
            } catch (Exception ignored) {
                // A package that vanished, or a vendor that refuses: the rest of the list still runs.
            }
        }
        prefs(ctx).edit()
                .putLong(KEY_CLEARED, System.currentTimeMillis())
                .remove(KEY_LOCAL)
                .remove(KEY_HIDDEN)
                .apply();
        return asked;
    }

    /** Takes one app off the list without killing it. It comes back when the app is used again. */
    static void forget(Context ctx, String pkg) {
        if (ctx == null || pkg == null || pkg.isEmpty()) return;
        Map<String, Long> hidden = hidden(ctx);
        hidden.put(pkg, System.currentTimeMillis());
        writeHidden(ctx, hidden);
        List<Entry> out = new ArrayList<>();
        for (Entry e : local(ctx)) {
            if (!e.pkg.equals(pkg)) out.add(e);
        }
        writeLocal(ctx, out);
    }

    /** True when the user has granted Settings > Special access > Usage access. */
    static boolean usageAccess(Context ctx) {
        if (ctx == null) return false;
        try {
            AppOpsManager ops = (AppOpsManager) ctx.getSystemService(Context.APP_OPS_SERVICE);
            if (ops == null) return false;
            int mode;
            if (Build.VERSION.SDK_INT >= 29) {
                mode = ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                        Process.myUid(), ctx.getPackageName());
            } else {
                mode = ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                        Process.myUid(), ctx.getPackageName());
            }
            return mode == AppOpsManager.MODE_ALLOWED;
        } catch (Exception ignored) {
            return false;
        }
    }

    static Intent usageAccessIntent() {
        return new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }

    /** System packages that must survive a clear-all: Home, the IME, accessibility helpers, TV services. */
    private static Set<String> protectedPackages(Context ctx) {
        Set<String> out = new HashSet<>();
        out.add(ctx.getPackageName());
        PackageManager pm = ctx.getPackageManager();
        try {
            Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
            for (ResolveInfo ri : pm.queryIntentActivities(home, 0)) {
                if (ri.activityInfo != null) out.add(ri.activityInfo.packageName);
            }
        } catch (Exception ignored) {
        }
        try {
            String enabled = Settings.Secure.getString(ctx.getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (enabled != null) {
                for (String service : enabled.split(":")) {
                    int slash = service.indexOf('/');
                    if (slash > 0) out.add(service.substring(0, slash));
                }
            }
        } catch (Exception ignored) {
        }
        try {
            InputMethodManager imm = (InputMethodManager) ctx.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                for (InputMethodInfo imi : imm.getEnabledInputMethodList()) out.add(imi.getPackageName());
            }
        } catch (Exception ignored) {
        }
        try {
            String ime = Settings.Secure.getString(ctx.getContentResolver(),
                    Settings.Secure.DEFAULT_INPUT_METHOD);
            if (ime != null) {
                int slash = ime.indexOf('/');
                out.add(slash > 0 ? ime.substring(0, slash) : ime);
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    /** Pure prefix rule, kept free of Android calls so the unit test can run it on the JVM. */
    static boolean protectedName(String pkg) {
        if (pkg == null || pkg.isEmpty() || pkg.equals("android")) return true;
        for (String prefix : PROTECTED_PREFIXES) {
            if (pkg.equals(prefix) || pkg.startsWith(prefix + ".")) return true;
        }
        return false;
    }

    /** Pure merge: newest wins per package, entries at or before {@code clearedAt} are dropped, then cap. */
    static List<Entry> merge(List<Entry> local, Map<String, Long> usage, long clearedAt, int max) {
        Map<String, Long> best = new HashMap<>();
        if (usage != null) {
            for (Map.Entry<String, Long> e : usage.entrySet()) {
                if (e.getKey() == null || e.getValue() == null || e.getValue() <= clearedAt) continue;
                best.put(e.getKey(), e.getValue());
            }
        }
        if (local != null) {
            for (Entry e : local) {
                if (e == null || e.pkg == null || e.at <= clearedAt) continue;
                Long old = best.get(e.pkg);
                if (old == null || e.at > old) best.put(e.pkg, e.at);
            }
        }
        List<Entry> out = new ArrayList<>(best.size());
        for (Map.Entry<String, Long> e : best.entrySet()) out.add(new Entry(e.getKey(), e.getValue()));
        Collections.sort(out, (a, b) -> {
            int byTime = Long.compare(b.at, a.at);
            return byTime != 0 ? byTime : a.pkg.compareTo(b.pkg);
        });
        if (out.size() > max) return new ArrayList<>(out.subList(0, max));
        return out;
    }

    private static boolean displayable(PackageManager pm, Context ctx, String pkg) {
        if (pkg == null || pkg.isEmpty() || pkg.equals(ctx.getPackageName())) return false;
        if (pkg.equals("android") || pkg.equals("com.android.systemui")) return false;
        if (pkg.startsWith("com.android.providers") || pkg.equals("com.android.shell")) return false;
        if (pkg.contains("inputmethod")) return false;
        try {
            if (pm.getLeanbackLaunchIntentForPackage(pkg) != null) return true;
            return pm.getLaunchIntentForPackage(pkg) != null;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static List<Entry> local(Context ctx) {
        List<Entry> out = new ArrayList<>();
        String raw = prefs(ctx).getString(KEY_LOCAL, "");
        if (raw == null || raw.isEmpty()) return out;
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                String pkg = o.optString("pkg", "");
                long at = o.optLong("at", 0L);
                if (!pkg.isEmpty() && at > 0) out.add(new Entry(pkg, at));
            }
        } catch (Exception ignored) {
            // A truncated write (battery pull) drops the local half; usage stats can still fill the list.
        }
        return out;
    }

    private static void writeLocal(Context ctx, List<Entry> entries) {
        try {
            JSONArray arr = new JSONArray();
            for (Entry e : entries) {
                JSONObject o = new JSONObject();
                o.put("pkg", e.pkg);
                o.put("at", e.at);
                arr.put(o);
            }
            prefs(ctx).edit().putString(KEY_LOCAL, arr.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    private static Map<String, Long> hidden(Context ctx) {
        Map<String, Long> out = new HashMap<>();
        String raw = prefs(ctx).getString(KEY_HIDDEN, "");
        if (raw == null || raw.isEmpty()) return out;
        try {
            JSONObject o = new JSONObject(raw);
            Iterator<String> keys = o.keys();
            while (keys.hasNext()) {
                String pkg = keys.next();
                long at = o.optLong(pkg, 0L);
                if (!pkg.isEmpty() && at > 0) out.put(pkg, at);
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    private static void writeHidden(Context ctx, Map<String, Long> hidden) {
        try {
            JSONObject o = new JSONObject();
            for (Map.Entry<String, Long> e : hidden.entrySet()) o.put(e.getKey(), e.getValue());
            prefs(ctx).edit().putString(KEY_HIDDEN, o.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    private static void unhide(Context ctx, String pkg) {
        Map<String, Long> hidden = hidden(ctx);
        if (hidden.remove(pkg) != null) writeHidden(ctx, hidden);
    }
}
