# Settings plan

Status: **P0 implemented** (store + precedence + non-destructive theme push). P1 is the on-TV UI.

## 1. The rule

```
effective value = on-TV choice  >  theme config.json  >  res/values/config.xml
```

- `config.xml` is still the only source of compiled defaults.
- A theme is a set of optional defaults, not an owner of the device.
- A key is an "on-TV choice" only when the owner changed it; presence in `SettingsStore` is the
  proof. There is no separate boolean per key, so a backup restores the same state.
- **Follow theme** deletes every on-TV choice and bumps the revision. A theme push only records a
  new theme signature; it never deletes a choice.

`Config.load()` merges the two JSON objects before applying them, so there is exactly one apply
path. `SettingsStore.merge()` is unit-tested for precedence, null themes, empty overrides and input
immutability.

## 2. Apply and persistence

| Concern | Decision |
|---|---|
| Storage | `SharedPreferences` named `launcher_settings` through `SettingsStore` |
| Revision | `settings_revision` long; Home rebuilds in `onResume` when it moved |
| Theme change | `theme_signature` records `fileStamp(config.json) + ":" + theme_revision`; diagnostic only |
| Backup | `SettingsStore.exportJson()` / `importJson()`; primitives only, bookkeeping excluded |
| Restore | unknown keys retained; wrong JSON types skipped; revision bumped once |
| Defaults | never copied into prefs; an unset key follows the theme and then `config.xml` |
| Reset | `clearOverrides()` for Follow theme; per-page reset removes only that page's keys |
| Migration | `settings_schema` integer; v1 is the current shape |

Export file shape:

```json
{
  "settings_schema": 1,
  "columns": 5,
  "dock_row": true,
  "app_keys": ["KEYCODE_TV=com.example.tv"],
  "keymap_v1": "[{\"key\":\"KEYCODE_TV\",\"taps\":2,\"action\":\"app:com.example\"}]"
}
```

No storage permission is needed: use `ACTION_CREATE_DOCUMENT` / `ACTION_OPEN_DOCUMENT` (SAF).

## 3. Pages

Ten pages, each at most seven primary rows. Rows that cannot work on this TV are hidden, not
disabled, except where the owner needs to understand a dependency (then the row stays with a
one-line reason).

### 3.1 Home & layout

`columns`, `rows_below_dock`, `dock_apps`, `fill_dock`, `dock_title`, `dock_row`, `single_row`,
`dock_band`, `dock_band_all`, `dock_even_padding`, `dock_glass`, `dock_opacity`,
`always_show_names`, `tile_menu`

Dependencies: `single_row` requires `dock_row`; `dock_title`, `dock_opacity`, `dock_band`,
`dock_band_all` and `dock_even_padding` require `dock_row`; `dock_glass` requires `dock_band`.

### 3.2 Look & motion

`animations`, `advanced_effects`, `reduced_motion`, `focus_overshoot`, `cinematic_home`,
`cinematic_dots`, `cinematic_white_focus`, `cinematic_art`, `cinematic_scrim`, `ambient_glow`,
`app_glow`, `glow_snap`, `fade_rows`, `glow_style`, `glass_style`, `background_color`,
`sky_intensity`

Dependencies: `advanced_effects` requires `animations`; `reduced_motion` disables the advanced rows
and explains why; `glow_snap` requires `ambient_glow` or `app_glow`.

### 3.3 Wallpaper

`wallpaper` (on/off), `wallpaper_choice` (pick one / none / theme default),
`wallpaper_rotation_minutes`, `wallpaper_order`, `wallpaper_dim`, `wallpaper_true_color`,
`wallpaper_captions`

Dependencies: rotation, order and dim require `wallpaper` on; `wallpaper_true_color` carries a
"2 GB+" warning because it doubles the bitmap to about 8 MB at 1080p.

### 3.4 Header & clock

`show_greeting`, `greeting_name`, `header_white`, `clock_format`, `date_below_clock`,
`shamsi_date`, `lunar_date`, `lunar_utc_offset`, `clock_wallpaper`, `clock_wallpaper_dim`,
`clock_glow`, `show_stats`, `game_day`, `locale`, `show_home_settings_shortcut`

Dependencies: `greeting_name` requires `show_greeting`; date keys require the date row;
`lunar_utc_offset` requires `lunar_date`; `clock_wallpaper_dim` and `clock_glow` require
`clock_wallpaper`; `locale` shows "Home restarts to apply".

### 3.5 Top bar & navigation

`show_network_pill`, `show_inputs_pill`, `icon_only_buttons`, `icon_only_inputs`, `icon_only_vpn`,
`wifi_label_width`, `wifi_status_color`, `wifi_status_warnings`, `vpn_app`, `vpn_pill`,
`top_bar_apps`, `top_menu`, `launcher_settings_button`, `library_button`, `show_library_pill`,
`reorder_button`

Dependencies: `wifi_label_width` requires `icon_only_buttons`;
`wifi_status_warnings` requires `wifi_status_color`; `icon_only_inputs` requires
`show_inputs_pill`; `vpn_pill` and `icon_only_vpn` require `vpn_app` or a system VPN;
`show_library_pill` requires `app_library`.

### 3.6 Apps & library

`app_library`, `app_library_selection`, `library_ok_opens`, `library_apps`,
`library_excluded_apps`, `hidden_apps`, `launch_actions`, `app_labels`, `app_banners`,
`icon_tiles`, `uniform_tiles`

Dependencies: the three library behaviour rows require `app_library`; `app_labels` and
`app_banners` offer an installed-app picker; `hidden_apps` explains that the library can still
show a hidden app.

### 3.7 Inputs & Live TV

`live_tv_package`, `live_tv_action`, `live_tv_input_id`, `source_panel_service`,
`source_panel_action`, `source_panel_extra`, `coocaa_sources`, `input_action`, `input_sources`,
`input_uri_scheme`, `inputs_connected_only`, `inputs_cec_state_only`, `handle_view_inputs`

Capability rules: Live TV rows appear only when a Live TV app or input resolves;
`source_panel_*` moves under Advanced when `TvInputManager` has inputs; `coocaa_sources` appears
only on Skyworth/Coocaa; `input_sources` requires `input_action`.

### 3.8 Remote & buttons

`key_filter`, `gamepad_passthrough`, `consume_volume_keys`, `settings_key_keycodes`,
`settings_key_panel`, `input_key_keycodes`, `app_keys`, `stock_home_redirect`, `power_on_home`,
`power_on_home_always`, `home_long_press_home`, `restore_key_services`, `back_opens_clock`,
and the **Button mapper** editor (section 5).

Dependencies: keycode rows require the accessibility service to be enabled;
`power_on_home_always` requires `power_on_home`; `restore_key_services` is Advanced.

### 3.9 Startup & idle

`boot_to_live_tv`, `open_on_start_choice`, `open_on_start_app` (preference, not a theme key),
`open_on_start_network_wait`, `open_on_start_on_wake`, `home_idle_clock_minutes`

Dependencies: the three `open_on_start_*` rows require `open_on_start_choice`.

### 3.10 Theme, modules & system

`module_cards`, theme push/export, `festival_until`, `greeting`, `accent_color`,
`seasonal_skin`, `race_calendar`, default-home picker, accessibility-service status, ADB/debloat
entry, logs, about, safe mode.

`module_cards` and the theme rows stay in Advanced because they are authoring tools, not daily
settings.

## 4. UI rules: remote and touch are equal inputs

Every action must be reachable both ways; neither input is a fallback.

| Action | Remote | Touch |
|---|---|---|
| Move focus / move selection | D-pad | Tap the row directly |
| Open a picker | OK | Tap the row |
| Change value in place | Left / Right | Tap a chip, or swipe the chip row |
| Reset one row to the theme | Long OK | Long-press the row |
| Context menu (move, hide, app info) | Long OK | Long-press the tile or row |
| Scroll a page | D-pad at the edge | Drag / fling |
| Back | Back key | System back gesture / button |
| Reorder | Long OK → Move, then D-pad | Long-press and drag a handle |
| Text entry | On-screen keyboard | System keyboard |

Implementation rules:

- Rows are `RecyclerView` items with `clickable`, `focusable`, `contextClickable`, a 48 dp minimum
  height and a visible focus *and* pressed state. Never use `onFocusChange` to perform an action.
- `Touch` must not move D-pad focus to the touched row unless the row is activated; scrolling must
  not open a row.
- Long-press works with both a remote OK hold (`OnLongClickListener`) and a touch long-press.
- Chips are at least 48 dp tall and scroll horizontally with touch; the focused chip stays visible
  when the D-pad moves.
- Dialogs are dismissible by Back, by tapping outside, and by a visible Cancel row.
- No hover-only affordance, no swipe-only action, no gesture that hides a primary control.
- Test matrix: TV remote, touch TV/tablet, and a phone-sized window.

Other rules:

- No sliders. Integers use discrete chips (4/5/6/7 columns, 0/1/2/3 rows, 100/70/50/30 opacity).
- Text entry only for `greeting_name` and free-form package/action fields.
- App pickers show icon + label, sorted by label, with a search filter that accepts touch typing.
- Key capture shows keycode, scancode and device id, then asks for taps and hold time.
- Risky rows (advanced effects, key filter, consume volume, restore services) carry a short
  warning and live in Advanced.
- Presets: **Performance** (no wallpaper, reduced motion, no glow, RGB_565), **Balanced**,
  **Cinematic** (wallpaper, glow, cinematic shelf). A preset writes explicit choices; Follow theme
  still clears them.
- Simple/Advanced: Advanced unlocks after long OK / long-press on About, or five taps on the
  version row.
- Every page has "Reset this page" and "Follow theme".
- Settings search is an optional P4 item; the rail is faster with a remote, the search field is
  faster with touch.

## 5. Button mapper

Stored as one JSON string (`keymap_v1`) so export/import is a single key.

```json
[
  {
    "key": "KEYCODE_TV",
    "taps": 2,
    "holdMs": 0,
    "when": "any",
    "action": "app",
    "value": "com.example.tv"
  }
]
```

- **Trigger:** keycode name, tap count 1–3, optional hold threshold, condition `any`, `launcher`
  or `foreground:<package>`.
- **Action:** `app`, `activity`, `input`, `action`, `key` (send a keycode), `launcher`
  (library, reorder, settings, screensaver, sleep, wifi, recents), `none`.
- **Capture:** the next remote key event, with keycode/scancode/device id shown before saving.
- **Conflicts:** warn when two rules match the same key + tap count + condition, and when a rule
  would consume Back, Home or volume without an explicit confirmation.
- **Safety:** ignore injected events; do not consume volume repeats unless the owner opted in;
  disable remaps while a gamepad is connected if `gamepad_passthrough` is on; one "Disable all
  remaps" switch; keep the existing service-repair path after an OEM force-stop.
- **Service:** one accessibility service, `canRequestFilterKeyEvents`, `onKeyEvent` returns
  `true` only when a rule fires. Home-key handling stays with the existing `HomeKeyService`
  unless the mapper explicitly owns that key.

## 6. Performance budget (Maxton 1.5 GB, Android 11)

| Metric | Budget |
|---|---|
| Release APK | ≤ 1.5 MB |
| Idle PSS | ≤ 60 MB |
| Cold start to first frame | ≤ 1.5 s |
| Key-to-action p95 | ≤ 80 ms |
| Background CPU | ~0% |
| Runtime dependencies | 0 (AndroidX RecyclerView + dynamicanimation already present) |

Rules: no Compose, no Kotlin runtime, no network permission, no `QUERY_ALL_PACKAGES`, no
`MANAGE_EXTERNAL_STORAGE`; build settings pages on demand and release bitmaps when a page closes;
keep wallpapers on external files, never copy them into the APK or app-private storage.

## 7. Acceptance criteria

- [x] A preference set on the TV wins over a theme push and survives a restart.
- [x] Follow theme returns every key to the theme value.
- [x] Merge logic unit-tested.
- [ ] Every key in `themes/README.md` is reachable from a page (or explicitly marked Advanced).
- [ ] Every row works with a remote **and** with touch: tap, long-press, drag-scroll, back.
- [ ] Changing any key applies without a reboot; only `locale` may restart Home.
- [ ] Export → factory reset → import restores layout, hidden apps and keymap.
- [ ] A corrupt or partial import cannot break Home; invalid values fall back to defaults.
- [ ] Key mapper: 1x/2x/3x, hold, send-key, app/input/action; volume repeat unchanged by default.
- [ ] Release APK is signed by a key that is not the debug key, and CI verifies the signature.
