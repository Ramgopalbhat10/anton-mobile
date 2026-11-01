# Anton mobile design kit

Reference for the Android app's look: the tokens, spacing, components and
assets it is built from, and the rules that keep it matching the web app
(anton-v2's shadcn UI) while staying under the 2 MB APK budget.

Everything here lives in code. This page says where, and what the numbers
are, so a new screen can be built without reverse-engineering an old one.

| Area | Source |
|---|---|
| Colours, type, spacing, press highlight | `app/src/main/java/in/mrgb/anton/ui/theme/Theme.kt` |
| Icons | `ui/theme/Lucide.kt` |
| Vendor and plan logos | `ui/theme/Logos.kt`, `res/drawable/logo_*.xml` |
| Primitives (card, button, input, select chip, list row, composer…) | `ui/components/Components.kt` |
| Dropdown, menu, select (combobox), bottom sheet | `ui/components/Overlays.kt` |
| File chips, image viewer, remote images | `ui/components/Media.kt` |
| Work (reasoning) card | `ui/components/Work.kt` |
| Model picker | `ui/screens/ModelPicker.kt` |

## Principles

1. **Same language as the web.** Dark canvas, hairline borders, small radii,
   Geist type, one cyan accent. If the web has a component, the app's version
   should read as the same thing at phone size.
2. **Relaxed, not loose.** Elements get room to breathe (see Spacing), but a
   phone screen should still show a useful amount of content. Prefer more
   space *between* groups over bigger controls.
3. **No UI libraries.** Compose foundation + Material3 only for the theme
   plumbing. Icons are vectors in code; no icon packs, image loaders or
   component kits. Every addition is weighed against the APK budget.
4. **Quiet by default.** Secondary information is tertiary-grey, mono, or
   both. Colour is reserved for state (accent, success, warning, danger) and
   for the data tones in the work card.

## Colour

Neutrals (`Anton.N1000…N100`) step from pure black to near-white. Screens use
the role names, not the neutrals:

| Role | Token | Value |
|---|---|---|
| Canvas (screen background) | `Canvas` | `#0A0A0B` |
| Surface / raised / overlay | `Surface`, `Raised`, `Overlay` | `#101012`, `#16161A`, `#1C1C21` |
| Inset (code, wells) | `Inset`, `WellBg` | `#000000`, `#0C0C0C` |
| Card | `CardGradient` (`#161616` → `#121212`) + `CardBorder` (white 7%) + 1dp top highlight |
| Borders | `BorderSubtle` / `BorderDefault` / `BorderStrong` | white 6% / 9% / 14% |
| Text | `TextPrimary` / `Secondary` / `Tertiary` / `Disabled` | `#EDEDEF` / `#A0A0A8` / `#6E6E78` / `#4A4A52` |
| Accent | `Accent` / `AccentHover` / `AccentFg` | `#3FB6D8` / `#6FD1EC` / `#06181F` |
| Accent fill / ring | `AccentBg` / `AccentBorder` / `Ring` | cyan 12% / 40% / 30% |
| Success / Warning / Danger | `Success`, `Warning`, `Danger` (+ `…Text`, `…Bg`, `…Border`) | `#35C88A`, `#E3A63F`, `#E5544F` |
| Data tones (work card) | `Data1…Data5` | cyan, violet, amber, green, pink |

shadcn aliases mirror `src/web/styles.css`: `Popover`, `PopoverRing`,
`Input`, `InputBg`, `Ring`.

Pressed and hovered states use `Hover` (white 4%) and `Pressed` (white 7%)
through `PressHighlight`, which replaces Material's ripple.

## Type

Geist and Geist Mono, variable TTFs, Latin subset (OFL, `licenses/`).

| Style | Size / line height | Weight | Use |
|---|---|---|---|
| `Title` | 18 / 24 | SemiBold | App bar titles |
| `Heading` | 17 / 22 | SemiBold | Sheet titles, h1 in replies |
| `BodyLarge` | 15.5 / 24 | Regular | Prominent body text |
| `Body` / `BodyMedium` | 14.5 / 21 | Regular / Medium | Default text, row titles |
| `Small` / `SmallMedium` | 13.5 / 19 | Regular / Medium | Secondary lines, chips |
| `Meta` | 12.5 / 19 | Regular | Subtitles, captions |
| `MonoLabel` | 11.5 / 18, uppercase, tracked | Medium, mono | Section labels |
| `Num` | 12 / 18 | Mono | Ages, counts, token sizes |
| `Code` | 13 / 20 | Mono | Code blocks |

Markdown replies use `Body` at 14.5/22 with 6dp between blocks (`mdPadding()`
in `ThreadScreen.kt`). Text between work-card steps uses 13.5/21.

The scale is sized for phones: the web's 13px body reads small on a handset, so the app runs every step about 1–1.5sp larger than the web while keeping the same hierarchy.

## Spacing

All spacing sits on a 2/4dp grid. `Space` in `Theme.kt` holds the scale:

| Token | dp | Typical use |
|---|---|---|
| `Space.Xxs` | 2 | Hairline nudges, icon optical alignment |
| `Space.Xs` | 4 | Between a title and its meta line, list-item gaps |
| `Space.Sm` | 8 | Gaps inside a row, chip gaps, between bubbles and chips |
| `Space.Md` | 12 | Row padding, card inner padding, icon → text in rows |
| `Space.Lg` | 16 | Card padding, sheet sections |
| `Space.Xl` | 20 | Between transcript turns |
| `Space.Xxl` | 24 | Between settings groups, above section labels |
| `Space.Gutter` | 18 | Screen edge → content |
| `Space.Turn` | 20 | Between messages in a thread or coordinator feed |

### Rhythm by surface

These are the values the screens use today. Keep new screens on the same
numbers.

| Surface | Spacing |
|---|---|
| App bar | min height 56; padding 8 vertical, gutter 18 (8 with a back button); 8 between actions |
| Task / thread row | 8 outer inset, 12 × 12 inner padding, 6 between title and facts, 10 glyph → title |
| Section label | 18 above, 6 below, 18 from the edge |
| Project row | 12 × 13 padding, 4 between rows |
| Transcript | gutter 18, 18 top/bottom, 20 between turns, 14 between parts of one reply |
| User bubble | 15 × 11 padding, 8 above its attachment chips |
| Work card | 44 header, 14 side padding; step rows 30 high with 3 between |
| Ask / plan card | 16 padding, 12 icon → text, option rows ≥ 44 high, 6 between options |
| Composer | 14 from the screen edge, 8 above, 12 below; input 16 × 14, toolbar 10, 34 send button |
| Model picker | 12 between filter pills, 18 above a vendor group, model rows 12 × 11 with a 32 logo tile |
| Bottom sheet | 20 side padding, 16 below the header |
| Menu item | min height 38, 10 × 8 padding |
| Bottom bar | 52 tabs, 10 side padding |
| Settings | gutter 18, 24 above each group, 18 between fields in a sheet |

Rule of thumb: when something feels cramped, add space **between groups**
first (section labels, turns, cards), then row padding, and only last make a
control bigger.

## Shape

| Radius | Use |
|---|---|
| 14 | Cards (`AntonCard`), user bubbles (with a 4dp tail corner), composer |
| 10 | Rows, wells, bottom-bar tabs, ask options |
| 8 | Buttons, chips, icon buttons, model rows, logo tiles |
| 6–7 | Small chips, badges, step tiles |
| Circle | Status dots, plan dot, switches |

Borders are 1dp hairlines. Dividers use `hairlineTop()` / `hairlineBottom()`
or `Hairline()`.

## Components

shadcn equivalents in brackets.

- **AntonCard** [Card] — gradient fill, hairline border, 14 radius.
- **AntonWell** — inset container for code, tool output, subagent detail.
- **AntonButton** [Button] — `Primary`, `Outline`, `Secondary`, `Ghost`,
  `Destructive`; sizes `Sm` 32, `Md` 38, `Lg` 44. `GhostIconButton` (36, 20 icon) for bars.
- **AntonInput** / **Field** [Input + Label] — 40 min height, focus ring in `Ring`, optional
  leading icon and trailing slot.
- **SelectChip** [Select trigger] — 32 high, `leading`/`trailing` slots
  (the composer's model chip puts the logo and effort/context/fast facts there).
- **Segmented** [Tabs] — 32 high, in a well, with counts.
- **Badge**, **SectionLabel**, **StateGlyph** (task state icons), **ListRow**.
- **AntonDropdown** / **MenuItem** / **MenuLabel** / **MenuSeparator**
  [DropdownMenu], **AntonSelect** [Combobox], **AntonSheet** [Drawer].
- **AntonComposer** — text field + attach + model chip + send/stop.
- **FileChip** / **SentAttachments** / **ReplyOutputs** — files as small
  chips, tap to open the zoomable **ImageViewer**.
- **WorkCard** — one card per reply holding thoughts, tool calls, subagents
  and narration, each on a 30dp step row with a lucide icon and data tone.
- **ModelPicker** — source tabs with plan/provider logos, capability filters,
  sort, per-model options (effort, context, fast).

## Icons and logos

- **Icons**: lucide (ISC), the same set the web uses, as `ImageVector`s built
  from lucide's own path data in `Lucide.kt`. 1.5–2 stroke, sizes 12–22 (14–16 inline with text, 20–22 in bars).
  Add an icon by copying its path data from `lucide-static`; don't pull in an
  icon library.
- **Logos**: LobeHub icons (MIT), converted with Android's `Svg2Vector` into
  `res/drawable/logo_*.xml`. Mono logos are tinted; colour logos (Claude)
  draw as-is. `Logos.forModel` picks a vendor logo, `Logos.forSource` a plan
  or gateway logo. Subscription models show the plan's logo with a cyan
  `PlanDot`. Unknown vendors fall back to an initial tile.
- **Launcher**: the Anton cube, `res/drawable/ic_launcher_foreground.xml` on
  `#101517`.

## Images and media

Images load through OkHttp with the Access headers, decode into the in-memory
`ImageStore` (LRU) and are shared via `LocalImages`. No image library.

## Budget

The release APK must stay under **2 MB** (R8 + resource shrinking, arm64).
It is ~1.65 MB today. The two fonts take ~67 KB of it and all other resources (logos, launcher icon) ~40 KB. Check the size of
`app/build/outputs/apk/release/` after any change that adds assets.

## Checking a change

There is no emulator in CI. Screens are rendered on the JVM with Paparazzi in
a scratch copy of the project (copy `app/src/main/java` and `res`, then
`gradle :app:recordPaparazziDebug`) and compared before/after.
