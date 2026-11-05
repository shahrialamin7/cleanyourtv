# Recent apps and background cleanup

A shelf of the apps Home has seen, newest first, with one action that asks Android to stop their
background processes. It is the one feature that reads state outside the launcher, so its limits are
written down here rather than hidden in a tooltip.

## Where it opens

Hold **OK** on **Settings** on Home, then choose **Recent apps**. The same event or a top-bar tab can
open it later; the first version stays in the existing hold menu so no theme, layout, or translated
top bar has to change.

## What it shows

Two sources are merged, newest first, at most 12:

1. **Home's own history.** Every intent Home opens is recorded in the `recent_apps` prefs file. This
   needs no permission and works the moment the launcher is installed.
2. **Android's usage stats.** When the user grants **Usage access** (Settings > Special access >
   Usage access), `UsageStatsManager` adds apps opened by a remote's own Netflix/YouTube button or by
   another app in the last seven days. Without the grant the shelf still works from the first source.

Only launchable apps are shown. System UI, input methods, providers, the shell, and Home itself are
filtered out. Settings, TV settings, GMS, TV input services, the IME, enabled accessibility helpers,
and every HOME app are protected from the clear action.

## Clear all: what it really does

`ActivityManager.killBackgroundProcesses(pkg)` is the strongest action available to a normal app on
Android 11. It kills the package's **background** processes; a foreground service, a system-important
app, or a package the OEM protects can come straight back. There is no root, device-owner, or shell
path on the target TV, so the UI says **"Asked N apps to stop"**, never "force-stopped".

After a clear:

- the local history is emptied and a `cleared_at` mark hides older usage entries;
- a single app removed with **Hold OK > Remove from recents** is hidden until it is used again;
- the list is rebuilt from the next launch, so an app opened after the clear returns immediately.

## Remote and touch

| Input | Action |
|---|---|
| Left / Right | Move between app cards |
| OK | Open the focused app through the same `launch_actions` path a tile uses |
| Hold OK | Remove that app from the list (does not kill it) |
| Down | Move to **Clear all** / **Close** |
| Back | Close the shelf |
| Tap | Open a card |
| Horizontal drag | Scroll the shelf |
| Tap **Clear all** | Ask the listed apps to stop |

Cards are 150 × 190 dp with a 64 dp icon, so a tap target is well above the 48 dp minimum even on a
720p set. The shelf is built only while open; icons load on one background thread and are cached for
that opening, then dropped on dismiss.

## Storage and compatibility

- New prefs file `recent_apps`; no existing key changes, no `Config` constructor change, no theme key.
- `wallpaper_choice`, `open_on_start_app`, and every other on-TV setting are untouched.
- Theme pushes do not clear the history. **Clear all** is the only bulk removal.
- The feature is additive: existing Home layout, dock, reorder, module cards, and App library behave
  exactly as before.

## Permissions

| Permission | Why | Grant |
|---|---|---|
| `KILL_BACKGROUND_PROCESSES` | Ask the system to stop a package's background processes | Normal, install-time |
| `PACKAGE_USAGE_STATS` | Read apps opened outside Home | Special app-op, user toggle |

No accessibility service is required. The key mapper's foreground tracking can replace usage stats
later, but it is not needed for this shelf.
