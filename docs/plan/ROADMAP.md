# Roadmap

Base: `Gathin23/cleanyourtv` `main`, fork `shahrialamin7/cleanyourtv`.
Target: Maxton FHD+ Android 11, 1.5 GB RAM, 16 GB storage, no voice remote.
Inputs: D-pad remote **and touch**, both first-class.

## P0 — foundation (this branch)

- [x] Branch from current upstream, not the six-day-old fork.
- [x] `SettingsStore`: typed prefs, revision bump, export/import, clear-overrides.
- [x] Precedence: on-TV choice > theme `config.json` > `config.xml`.
- [x] Theme push no longer deletes on-TV choices; Follow theme does.
- [x] Merge unit tests.
- [x] ADR, roadmap and settings plan.
- [x] Signed release workflow in GitHub Actions (keystore secrets configured; back up
      `~/.config/home-launcher/signing/CREDENTIALS.txt` offline).
- [ ] CI green on the branch.

Exit: a stored preference survives a theme push; tests and lint pass; an APK is downloadable from
the run.

## P1 — real settings UI (remote + touch)

1. `SettingsActivity` shell: left rail (10 pages) + right `RecyclerView`, saved-instance state,
   Back returns to Home without losing changes.
2. Row types: switch, chips, app picker, list editor, key capture, text, info.
3. Pages in this order: Home & layout, Header & clock, Top bar, Look & motion, Wallpaper,
   Apps & library, Inputs & Live TV, Remote & buttons, Startup & idle, Advanced.
4. Dependency rules and capability hiding from `docs/plan/SETTINGS.md`.
5. Per-page reset + Follow theme; export/import through SAF.
6. Presets: Performance / Balanced / Cinematic.
7. Touch parity pass: tap, long-press, drag-scroll, 48 dp targets, no focus-only action.
8. Replace the current six-row `LauncherSettingsActivity` with an entry to the new UI; keep its
   intents (`EXTRA_REORDER`) working.

Exit: 90%+ of overlay keys reachable on the TV, every row usable by remote and touch, no reboot
except `locale`.

## P1.5 — recent apps (this branch)

- [x] Recent apps shelf from Home history + optional `UsageStatsManager`, newest first, max 12.
- [x] Hold OK on Settings > Recent apps; D-pad and touch parity; hold OK removes one app.
- [x] Best-effort **Clear all** with `killBackgroundProcesses`, protected system/IME/Home packages,
      and honest copy ("Asked N apps to stop").
- [x] New `recent_apps` prefs only; no `Config`/theme key, no existing feature changed.
- [ ] Top-bar tab/pill entry and a settings row in the P1 shell.

Exit: the shelf opens on the TV, shows apps opened outside Home after Usage access, and never kills
Home, the IME, an accessibility helper, or a system TV service.

## P2 — remote button mapper

1. One accessibility service for remaps; keep the existing Home/input services unless a rule owns
   the key.
2. Rule model and JSON storage (`keymap_v1`), export/import.
3. Capture screen: keycode, scancode, device id, taps 1–3, hold threshold, condition.
4. Actions: app, activity, input, action, send key, launcher action, none.
5. Conflict detection, warnings for Back/Home/volume, injected-event guard, gamepad passthrough,
   volume-repeat preservation.
6. Panic switch: "Disable all remaps"; service repair after OEM force-stop.

Exit: 10 common remotes and 3 gamepads tested; volume long-press behaves exactly as before when
remapping is off; a bad rule cannot make Home unreachable.

## P3 — borrow the best of tvOS-Launcher

- Quick settings / control panel overlay (inputs, Wi-Fi, volume, sleep, settings).
- Recent-apps switcher (first version shipped in P1.5; a top-bar tab remains).
- Lock screen / idle screensaver entry.
- Icon changer + app rename.
- Other Apps folder and hidden-apps vault.
- Wallpaper folder shuffle and caption polish.

Each item is its own PR with a memory measurement; drop anything that pushes idle PSS over 60 MB.

## P4 — optional

- Watch Next / continue-watching integration (research only upstream).
- On-TV search across apps (TvProvider + installed apps).
- In-app updater (opt-in, no default INTERNET permission unless the feature is on).
- On-TV debloat UI reusing the existing record/restore engine, not a second implementation.
- Settings search.

## Build and release

- `ci.yml` remains the gate: unit tests, lint, release build, 576 KB size gate.
- New `release.yml`: manual and `v*` tags; JDK 17, SDK 34; optional keystore from secrets;
  `apksigner verify`; sha256; artifact; GitHub Release on tag.
- Default package for the owner's release: `tv.homelauncher.app` via `-PlauncherApplicationId`
  (neutral; no personal name). CI keeps the upstream `com.gathin.tvhome` fallback because its
  fixtures and docs build without the property.
- Changing the applicationId is a new install identity: the first neutral build cannot upgrade an
  existing `com.gathin.tvhome*` app, so uninstall the old package once (or make the new one Home
  first, then uninstall the old). Settings live per package and do not migrate.
- A release without a keystore is allowed for testing and is labelled **debug-signed**; a tagged
  release fails if the keystore secret is missing.

## Verification

| What | How |
|---|---|
| Unit tests | `./gradlew testReleaseUnitTest` in CI |
| Lint | `./gradlew lintRelease` in CI |
| Size | APK < 589,824 bytes (CI gate) |
| Signature | `apksigner verify --print-certs` |
| Cold start | `adb shell am start -W -n tv.homelauncher.app/.MainActivity` |
| Memory | `adb shell dumpsys meminfo tv.homelauncher.app` after 2 min idle |
| Key latency | `adb shell input keyevent` + logcat timestamps, p95 <= 80 ms |
| Touch | `adb shell input tap/swipe`; emulator with touch; phone-sized window |
| Theme push | push a theme with a chosen key, verify the choice survives |
