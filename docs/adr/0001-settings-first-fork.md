# ADR 0001 — Settings-first fork of CleanYourTV

- **Status:** Accepted
- **Date:** 2026-11-05
- **Deciders:** project owner

## Context

CleanYourTV's launcher already has the hard parts of a TV home screen: a record-before-mutate
recovery layer, a HOME guard, a vendor-aware debloat catalogue, 31 locales, a theme format and a
module-card contract. Its weak point is *configuration surface*: roughly 112 overlay keys, of which
only six have an on-TV control. The rest require writing a theme folder and pushing it over ADB.

The target device is a Maxton FHD+ Android 11 TV with 1.5 GB RAM and 16 GB storage. It has no voice
remote. Builds must be produced by GitHub Actions; the Termux host has no Android SDK build-tools
for aarch64.

The owner wants:

1. every existing CleanYourTV feature, unchanged;
2. a real, grouped settings UI on the TV, with live apply;
3. accessibility-based remote-button remapping;
4. selected ideas from `techgenius1/tvOS-Launcher`;
5. a professional build/release path.

## Decision

**Fork CleanYourTV instead of rewriting, and add a settings layer rather than replacing the base.**

Specific choices:

| Area | Decision | Why |
|---|---|---|
| Base | `Gathin23/cleanyourtv` `main`, forked to `shahrialamin7/cleanyourtv` | keeps the safety layer, vendor fixes, themes, addons and 31 locales |
| Identity | app label **Home Launcher**; `launcherApplicationId` stays a Gradle property, default `tv.homelauncher.app` for the owner's release (neutral, no personal name) and `com.gathin.tvhome` for upstream-compatible CI builds | no package rename across 119 Java files; `tv.<domain>.<product>` is a valid reverse-DNS form (Twitch ships `tv.twitch.android.app`); a new applicationId is a new install identity, so the first neutral build needs one uninstall of the old package |
| Compatibility | 100% theme JSON and module-card compatibility | 21 themes and 32 addons keep working |
| Language/UI | Java + Android Views, no Compose, no third-party runtime dependency; **D-pad and touch are both first-class** | 1.5 GB / weak GPU target; the same APK must work on a TV remote, a touch TV and a tablet |
| Settings storage | `SharedPreferences` (`SettingsStore`), typed, versioned | no new dependency, survives process death, already backed up by Android |
| Precedence | **on-TV choice > theme `config.json` > `config.xml`** | the owner's explicit choice must never be silently erased |
| Theme push | no longer clears on-TV choices; **Follow theme** clears them explicitly | fixes the reported core problem |
| Apply | bump `settings_revision`; Home rebuilds on resume | existing upstream mechanism, no restart |
| Schema | one declarative registry drives rows, validation, export/import and docs | a new key must be one entry, not three code paths |
| Key mapping | rules: key + tap count (1/2/3) + hold + condition → action | matches the best part of tvOS-Launcher |
| Build | Gradle in GitHub Actions; optional release keystore from repository secrets | reproducible and independent of Termux |
| Distribution | signed APK artifact + GitHub Release on `v*` tags | upgradeable installs, auditable builds |

## Consequences

- Existing users keep their choices across theme pushes. This is an intentional behaviour change and
  is called out in the release notes; **Follow theme** is the escape hatch.
- The neutral package is a new install identity. Tooling accepts
  `TVHOME_PACKAGE=tv.homelauncher.app` (for example `tools/theme.sh` and
  `launcher/build-and-install.sh`); upstream device notes still show `com.gathin.tvhome` because
  that is the default build they document.
- The settings UI can grow one page at a time without touching `Config` again: all overlay keys go
  through the same merge.
- `Config` stays the single source of defaults; the schema does not duplicate `config.xml` values.
- The app remains Java/Views, so no Kotlin stdlib or Compose runtime is added to the APK.
- A signed release key must be backed up outside GitHub; losing it means uninstall/reinstall on the TV.
  The key lives in `~/.config/home-launcher/signing/` on the build host for now. The first
  release-signed install must replace the current debug-signed app (different signature), so one
  uninstall is expected; every later upgrade keeps its data.

## Alternatives considered

1. **Rewrite from tvOS-Launcher.** Rejected: 34.6k LOC of god-class Activities, heavier permissions,
   and it would discard CleanYourTV's recovery/debloat safety net.
2. **Keep theme-file-only configuration and ship a desktop config generator.** Rejected: still needs
   ADB, and the owner explicitly wants on-TV control.
3. **Compose UI.** Rejected for v1: binary size, memory and startup cost on a 1.5 GB device.
4. **Room/DataStore.** Rejected: no query needs; `SharedPreferences` is smaller and adequate.
5. **Remote-only UI.** Rejected: the owner also needs touch. Every row is a real clickable view
   with a 48 dp minimum target, scrolling works by drag, and no action is reachable only through
   focus or hover.
