# Mercantile — Asset Manifest

> Where every committed asset lives: its source under `art/` (a re-renderable
> `.glyph` for pixel art, a `.sfx` for audio, or a `.png` master for generated
> hi-res art) and the final file it ships as. **`MISSING`** in the source
> column flags a pixel asset that has no `.glyph` source yet — a candidate for
> the glyph pipeline (concord
> [`design/DESIGN-SYSTEM.md`](../../concord/design/DESIGN-SYSTEM.md) §8).
> [`DESIGN.md`](DESIGN.md) covers *why* each asset exists; this file covers *where* it lives.
>
> Final paths are under `src/main/resources/` unless noted. A separate report sweeps
> the resource tree for any final asset lacking a `.glyph` source.

## Branding masters

| Asset | Source | Final / derived copies |
|---|---|---|
| Full logo | `art/logo.png` — `.png` master (not glyph-based) | `site/assets/logo.png` |
| Mod icon | `art/glyphs/icon.gen.py` → `art/glyphs/icon.glyph` → `art/icon-128.png` (128 native), `art/icon-512.png` (512 store master) | `assets/mercantile/icon.png` (256, in-jar Mod Menu), `site/assets/icon.png` (256, favicon/nav) |
| OG image | — | `site/assets/og-image.png` |

## In-game pixel art

| Asset | `.glyph` source | Final asset |
|---|---|---|
| Reputation HUD badge (balance scale) | `art/glyphs/hud-scales-16.glyph` | `art/hud-icon-16.png` master → `assets/mercantile/textures/gui/sprites/reputation_badge.png` |
| Reputation detail icon (balance scale, 32px) | `art/glyphs/hud-scales-32.glyph` | `assets/mercantile/textures/gui/reputation_detail_icon.png` |
| Reputation detail panel (9-slice frame) | `art/glyphs/panel-frame-64.glyph` | `assets/mercantile/textures/gui/reputation_detail_panel.png` |
| Workstation link mote (particle) | `art/glyphs/link-mote.glyph` | `assets/mercantile/textures/particle/link_mote.png` |
| Merchant lock button | `art/glyphs/locked-button.glyph` | `assets/mercantile/textures/gui/sprites/locked_button.png` |
| Merchant unlock button | `art/glyphs/unlocked-button.glyph` | `assets/mercantile/textures/gui/sprites/unlocked_button.png` |
| Workstation claimed marker | `art/glyphs/workstation-claimed.glyph` | `assets/mercantile/textures/particle/workstation_claimed.png` |
| Workstation unclaimed marker | `art/glyphs/workstation-unclaimed.glyph` | `assets/mercantile/textures/particle/workstation_unclaimed.png` |
| Info button | `art/glyphs/info-button.glyph` | `assets/mercantile/textures/gui/sprites/info_button.png` |
| Close button | `art/glyphs/close-button.glyph` | `assets/mercantile/textures/gui/sprites/close_button.png` |
| Trade pin button (pinned) | `art/glyphs/pin-button.glyph` | `assets/mercantile/textures/gui/sprites/pin_button.png` |
| Trade pin button (unpinned) | `art/glyphs/pin-button-off.glyph` | `assets/mercantile/textures/gui/sprites/pin_button_off.png` |
| Sentry pylon block (top/side/bottom) | `art/glyphs/sentry-pylon-{top,side,bottom}.glyph` | `assets/mercantile/textures/block/sentry_pylon_{top,side,bottom}.png` |
| Pickup sparkle (particle — 8×8, emerald green, 4-pointed star) | `art/glyphs/pickup-sparkle.glyph` | `assets/mercantile/textures/particle/pickup_sparkle.png` |
| Trade cycle glint (particle — 8×8, gold, diamond facet) | `art/glyphs/cycle-glint.glyph` | `assets/mercantile/textures/particle/cycle_glint.png` |
| Follow trail (particle — 8×8, teal/cyan, wide flat ellipse) | `art/glyphs/follow-trail.glyph` | `assets/mercantile/textures/particle/follow_trail.png` |
| Golem shard (particle — 8×8, iron/stone, angular chunk) | `art/glyphs/golem-shard.glyph` | `assets/mercantile/textures/particle/golem_shard.png` |
| Pylon mote (particle — 4×4, light gray, round dot) | `art/glyphs/pylon-mote.glyph` | `assets/mercantile/textures/particle/pylon_mote.png` |
| Pylon spark (particle — 8×8, orange-gold, spiky) | `art/glyphs/pylon-spark.glyph` | `assets/mercantile/textures/particle/pylon_spark.png` |
| Contract available (particle) | `art/glyphs/contract-available.glyph` | `assets/mercantile/textures/particle/contract_available.png` |
| Grief tear (particle) | `art/glyphs/grief-tear.glyph` | `assets/mercantile/textures/particle/grief_tear.png` |
| Delivery contract (item) | `art/glyphs/delivery-contract.glyph` | `assets/mercantile/textures/item/delivery_contract.png` |
| Memorial keepsake (item) | `art/glyphs/memorial.glyph` | `assets/mercantile/textures/item/memorial.png` |

## Audio (`.sfx` — procedural synthesis)

Custom sound cues authored as `.sfx` specs and rendered to Ogg Vorbis through the
`mc-audio` pipeline (concord [`design/DESIGN-SYSTEM.md`](../../concord/design/DESIGN-SYSTEM.md) §9).
The `.sfx` is the source of truth; the rendered `.ogg`/`.report.png` byproducts are git-ignored
under `art/audio/`, and the shipping copy lives under `assets/`.

_None currently._ Every cue maps to a vanilla `SoundEvent` (see SPEC §Sound Design); the
sentry pylon's threat alert rides the vanilla bell ring at extended range. The
`art/audio/sentry-pylon-alarm.ogg` render under `art/audio/` is git-ignored (no committed
`.sfx` source, not shipped in-jar), so it is not a manifest entry.

## Not yet created

| Asset | Source | Final asset |
|---|---|---|
| Store gallery screenshots | Screenshot | — (planned) |
