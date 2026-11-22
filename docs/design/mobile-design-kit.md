# Anton mobile design kit

Reference for the Android app's look: the tokens, spacing, components and
assets it is built from, and the rules that keep it matching the web app
(anton-v2's Aura UI) while staying under the 2 MB APK budget.

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

1. **Same language as the web.** Aura dark: cool zinc neutrals, hairline
   borders, five corner steps, Inter type, one indigo accent. If the web
   has a component, the app's version should read as the same thing at
   phone size.
2. **Relaxed, not loose.** Elements get room to breathe (see Spacing), but a
   phone screen should still show a useful amount of content. Prefer more
   space *between* groups over bigger controls.
3. **No UI libraries.** Compose foundation + Material3 only for the theme
   plumbing. Icons are vectors in code; no icon packs, image loaders or
   component kits. Every addition is weighed against the APK budget.
4. **Quiet by default.** Secondary information is tertiary-grey, mono, or
   both. Colour is reserved for state (accent, success, warning, danger) and
   for the data tones in the work card.
5. **Flat surfaces.** No gradients on cards or controls — Aura separates
   layers with the surface ramp and 1dp hairlines, not shading.

## Colour

Aura's dark ramp (`--n-*`, coolest first) steps from pure black to near-white.
Screens use the role names, not the neutrals:

| Role | Token | Value |
|---|---|---|
| Canvas / window | `Canvas` | `#0A0A0B` |
| Panel (screen background) | `Panel` | `#0D0D0F` |
| Surface (cards, wells) | `Surface` | `#111113` |
| Raised (inputs, cards on cards) | `Raised` | `#151518` |
| Overlay (menus, popovers, dialogs, sheets) | `Overlay` | `#19191C` |
| Inset (code) | `Inset` | `#000000` |
| Hover / pressed washes | `Hover` / `Pressed` | white 4% / 7% |
| Card | `CardBg` (= `Surface`) + `CardBorder` (white 7%) | flat + inset hairline |
| Wells | `WellBg` (= `Hover`) + `WellBorder` | quiet inset |
| Borders | `BorderSubtle` / `BorderDefault` / `BorderStrong` | white 7% / 11% / 16% |
| Text | `TextPrimary` / `Secondary` / `Tertiary` / `Disabled` | `#EDEDEF` / `#A1A1AA` / `#71717A` / `#52525B` |
| Accent | `Accent` / `AccentHover` / `AccentPressed` / `AccentFg` | `#6E72F7` / `#8A8EFF` / `#5A5DE0` / white |
| Accent soft / line / ring | `AccentBg` / `AccentBorder` / `AccentRing` | indigo 14% / 38% / 54% |
| Success / Warning / Danger | `Success`, `Warning`, `Danger` (+ `…Text`, `…Bg`, `…Border`) | `#3DD68C`, `#F5B544`, `#F2555A` |
| Merged / Conflict / Info | `Merged`, `Conflict`, `Info` | `#B58CFF`, `#FF8A4C`, `#5AB8FF` |
| Data tones (work card) | `Data1…Data6` | indigo leads the series |

shadcn aliases mirror `src/web/styles.css`: `Popover`, `PopoverRing`,
`Input`, `InputBg`, `Ring`.

Pressed and hovered states use `Hover` and `Pressed` through
`PressHighlight`, which replaces Material's ripple.

## Type

Inter and JetBrains Mono — Aura's typefaces, variable TTFs (wght axis),
Latin subset (OFL, `licenses/`). `face()` in `Theme.kt` pins each
`FontWeight` to the matching point on the axis, so the app gets real
Medium/SemiBold/Bold from one file per family.

| Style | Size / line height | Weight | Use |
|---|---|---|---|
| `Display` | 30 / 36 | SemiBold | Rare hero text |
| `Title` | 20 / 26 | SemiBold | Screen titles |
| `Heading` | 17 / 22 | SemiBold | Sheet titles, h1 in replies |
| `H3` | 15 / 20 | SemiBold | Sub-headings |
| `BodyLarge` | 16 / 24 | Regular | Prominent body text |
| `Body` / `BodyMedium` | 15 / 22 | Regular / Medium | Default text, row titles |
| `Small` / `SmallMedium` | 13 / 19 | Regular / Medium | Secondary lines, chips |
| `Meta` | 12 / 18 | Regular | Subtitles, captions |
| `Micro` | 11 / 16 | Regular | Bottom-bar labels |
| `MonoLabel` | 11 / 16, tracked | Medium, mono | Section labels |
| `Num` | 12 / 18 | Mono | Ages, counts, token sizes |
| `Code` | 13 / 20 | Mono | Code blocks |

Three weights do the work: 400 for reading, 500 for actions and labels,
600 for hierarchy. Inter for the interface, JetBrains Mono for what the
agent reads and writes (code, paths, numbers, token counts).

The scale is sized for phones: the web's 13px body reads small on a
handset, so the app runs every step about 1.5–2sp larger than the web
while keeping the same hierarchy.

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
| Composer | 14 from the screen edge, 8 above, 12 below; input 16 × 14, toolbar 10, circular send |
| Model picker | 12 between filter pills, 18 above a vendor group, model rows 12 × 11 with a 32 logo tile |
| Bottom sheet | 20 side padding, 16 below the header |
| Menu item | min height 38, 10 × 8 padding |
| Bottom bar | hairline top, icon-over-label stops, raised accent New button in the middle |
| Settings | gutter 18, 24 above each group, 18 between fields in a sheet |

Rule of thumb: when something feels cramped, add space **between groups**
first (section labels, turns, cards), then row padding, and only last make a
control bigger.

## Shape

Aura's five corner steps, in `Radius`:

| Radius | dp | Use |
|---|---|---|
| `Radius.Mark` | 3 | Tiny marks, checks, selection ticks |
| `Radius.Inner` | 4 | Badges, keys, nested marks inside controls |
| `Radius.Control` | 6 | Buttons, inputs, chips, pills, icon tiles, menu items |
| `Radius.Card` | 8 | Cards, rows, image cards, wells |
| `Radius.Dialog` | 12 | Dialogs, composer, user bubbles (with a 4dp tail corner) |
| Circle | — | Status dots, plan dot, switches, the bar's New button, send |

Borders are 1dp hairlines. Dividers use `hairlineTop()` / `hairlineBottom()`
or `Hairline()`.

## Components

Aura equivalents in brackets.

- **AntonCard** [Card] — flat `Surface` fill, inset hairline, 8 radius.
  No gradient, no top highlight.
- **AntonWell** — inset container for code, tool output, subagent detail.
- **AntonButton** [Button] — `Primary` (accent fill, white text), `Outline`,
  `Secondary`, `Ghost`, `Destructive`; sizes `Sm` 32, `Md` 38, `Lg` 44.
  `GhostIconButton` (36, 20 icon) for bars.
- **AntonInput** / **Field** [Input + Label] — 40 min height, 6 radius;
  focus shows an accent edge and a soft accent ring.
- **SelectChip** [Select trigger] — 32 high, 6 radius, `leading`/`trailing`
  slots (the composer's model chip puts the logo and effort/context/fast
  facts there).
- **Segmented** [Tabs] — 32 high on a `Raised` track, selected option is an
  `Overlay` key with a hairline.
- **Badge**, **SectionLabel**, **StateGlyph** (task state icons), **ListRow**.
- **AntonDropdown** / **MenuItem** / **MenuLabel** / **MenuSeparator**
  [DropdownMenu] — `Overlay` surface, 8 radius, hairline + soft shadow.
- **AntonSelect** [Combobox], **AntonSheet** [Drawer] — raised surface,
  top corners, hairline, handle, 60% scrim.
- **AntonComposer** — 12 radius field on `Surface`; focus gets the accent
  edge; circular accent send button.
- **FileChip** / **SentAttachments** / **ReplyOutputs** — files as small
  chips, tap to open the zoomable **ImageViewer**.
- **WorkCard** — one card per reply holding thoughts, tool calls, subagents
  and narration, each on a 30dp step row with a lucide icon and data tone.
- **ModelPicker** — source tabs with plan/provider logos, capability filters,
  sort, per-model options (effort, context, fast).
- **BottomBar** — hairline-topped bar: icon-over-label stops on the left
  and right, a raised accent circle in the middle that starts a task
  (the web's New / command palette, on a phone).

## Icons and logos

- **Icons**: lucide (ISC), the same set the web uses, as `ImageVector`s built
  from lucide's own path data in `Lucide.kt`. 1.5–2 stroke, sizes 12–22 (14–16 inline with text, 20–22 in bars).
  Add an icon by copying its path data from `lucide-static`; don't pull in an
  icon library.
- **Logos**: LobeHub icons (MIT), converted with Android's `Svg2Vector` into
  `res/drawable/logo_*.xml`. Mono logos are tinted; colour logos (Claude)
  draw as-is. `Logos.forModel` picks a vendor logo, `Logos.forSource` a plan
  or gateway logo. Subscription models show the plan's logo with an indigo
  `PlanDot`. Unknown vendors fall back to an initial tile.
- **Launcher**: the Anton cube, `res/drawable/ic_launcher_foreground.xml` on
  `#101517`.

## Images and media

Images load through OkHttp with the Access headers, decode into the in-memory
`ImageStore` (LRU) and are shared via `LocalImages`. No image library.

## Budget

The release APK must stay under **2 MB** (R8 + resource shrinking, arm64).
It is ~1.65 MB today. The two variable fonts take ~270 KB of it and all
other resources (logos, launcher icon) ~40 KB. Check the size of
`app/build/outputs/apk/release/` after any change that adds assets.

## Checking a change

There is no emulator in CI. Screens are rendered on the JVM with Paparazzi in
a scratch copy of the project (copy `app/src/main/java` and `res`, then
`gradle :app:recordPaparazziDebug`) and compared before/after.
