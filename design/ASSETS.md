# Mercantile — Asset Manifest

> Where every committed asset lives: its source under `art/` (a re-renderable
> `.glyph` for pixel art, or a `.png` master for logos) and the final file it
> ships as. **`MISSING`** in the glyph column flags a pixel asset that has no
> `.glyph` source yet — a candidate for the glyph pipeline (concord
> [`design/DESIGN-SYSTEM.md`](../../concord/design/DESIGN-SYSTEM.md) §8).
> [`DESIGN.md`](DESIGN.md) covers *why* each asset exists; this file covers *where* it lives.
>
> Final paths are under `src/main/resources/` unless noted. A separate report sweeps
> the resource tree for any final asset lacking a `.glyph` source.

## Branding masters (`.png` — not glyph-based)

| Asset | `art/` master | Final / derived copies |
|---|---|---|
| Full logo | `art/logo.png` | `site/assets/logo.png` |
| Mod icon (128) | `art/icon-128.png` | `assets/mercantile/icon.png` (in-jar), `site/assets/icon.png` |
| OG image | — | `site/assets/og-image.png` |

## In-game pixel art

| Asset | `.glyph` source | Final asset |
|---|---|---|
| Reputation HUD badge (emerald gem) | `art/glyphs/hud-emerald-gem.glyph` | `art/hud-icon-16.png` master → `assets/mercantile/textures/gui/sprites/reputation_badge.png` |
| Workstation link mote (particle) | `art/glyphs/link-mote.glyph` | `assets/mercantile/textures/particle/link_mote.png` |
| Merchant lock button | `art/glyphs/locked-button.glyph` | `assets/mercantile/textures/gui/sprites/locked_button.png` |
| Merchant unlock button | `art/glyphs/unlocked-button.glyph` | `assets/mercantile/textures/gui/sprites/unlocked_button.png` |
| Workstation claimed marker | `art/glyphs/workstation-claimed.glyph` | `assets/mercantile/textures/particle/workstation_claimed.png` |
| Workstation unclaimed marker | `art/glyphs/workstation-unclaimed.glyph` | `assets/mercantile/textures/particle/workstation_unclaimed.png` |
| Info button | **MISSING** | `assets/mercantile/textures/gui/sprites/info_button.png` |
| Close button | **MISSING** | `assets/mercantile/textures/gui/sprites/close_button.png` |
| Sentry pylon block (top/side/bottom) | **MISSING** | `assets/mercantile/textures/block/sentry_pylon_{top,side,bottom}.png` |
| Pickup sparkle (particle) | **MISSING** | `assets/mercantile/textures/particle/pickup_sparkle.png` |
| Trade cycle glint (particle) | **MISSING** | `assets/mercantile/textures/particle/cycle_glint.png` |
| Follow trail (particle) | **MISSING** | `assets/mercantile/textures/particle/follow_trail.png` |
| Golem shard (particle) | **MISSING** | `assets/mercantile/textures/particle/golem_shard.png` |
| Pylon mote (particle) | **MISSING** | `assets/mercantile/textures/particle/pylon_mote.png` |
| Pylon spark (particle) | **MISSING** | `assets/mercantile/textures/particle/pylon_spark.png` |

## Not yet created

| Asset | Source | Final asset |
|---|---|---|
| Store gallery screenshots | Screenshot | — (planned) |
