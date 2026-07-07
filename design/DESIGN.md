# Mercantile — Design Specification

> Villager & Trade Overhaul for Minecraft 1.21.1 Fabric

---

## 1. Brand Identity

### Narrative

Mercantile transforms Minecraft's villagers from lever-operated vending machines
into people you have a history with — named, portable, persistent characters
whose standing with you matters. The name evokes the merchant class and market
economy. The visual language draws from **craft-commerce**: emeralds, the
village bell, the market stall, brass scales — the working iconography of trade.
Within the Concord collection, Mercantile owns the **Trade** verb: it converts
surplus into what you're missing (`VISION.md` §1–2).

### Tagline

*"Every villager remembers."*

The tagline follows the suite pattern (`VISION.md` §2): a short declarative
sentence about the player's relationship to the system. It states the reputation
thesis in three words and deliberately echoes Prosperity's "Every chest, yours to
discover." The README masthead, the site hero lede, and both store listings lead
with it; the descriptive line ("villager and trade overhaul…") is the supporting
copy and the SEO metadata, per the VISION §2 pattern.

### Logo Description

**Full Logo (`art/logo.png`):** A pixel-art market stall — green-and-cream
striped awning, brass scales hanging from the frame, a coin pile and chest on
the counter — set inside a circular stone medallion rimmed with an emerald
glow, on a field of glowing green brickwork with floating emerald particles.
Below, "MERCANTILE" in blocky pixel type with the subtitle "MINECRAFT VILLAGER
OVERHAUL". Vines frame the upper corners.

**Icon (`art/glyphs/icon.glyph`):** A standing balance scale — the same
brand motif as the reputation HUD glyph — in neutral emerald with a gold pivot
and finial, set inside a circular dark green-stone medallion with an emerald
rim-glow over a green-brickwork field. No text. This single glyph is the source
for every icon surface: `art/icon-128.png` (128 native) and `art/icon-512.png`
(512 store master) render from it, and the 256px in-jar `assets/mercantile/icon.png`
(shown in Mod Menu) and website favicon/nav (`site/assets/icon.png`) ship from it.

**Formula note:** `VISION.md` §3.2 specs the suite logo formula as *dark stone
brickwork frame, one central glowing motif object, name in blocky pixel type*.
The logo matches the formula's bones (pixel art, stone frame, glowing central
motif, pixel-type wordmark), rendered as a circular medallion on green brick with
a market-stall motif. The market-stall / scales medallion is Mercantile's
established identity — it is what siblings cite for Mercantile (Meridian
`design/DESIGN.md` §7).

### Color Palette

Shared neutrals are Concord design-system tokens (`concord/design/DESIGN-SYSTEM.md`,
hot-linked as `tokens.css`); the signature pair is Mercantile's per `VISION.md` §3.1.

| Role | Color | Hex | Usage |
|------|-------|-----|-------|
| Accent 1 | Emerald | `#50C878` | Headings, links, interactive elements, selection |
| Accent 2 | Emerald Bright | `#6DDB94` | Hover states, emphasis |
| Gradient End | Trade Green | `#00C853` | Heading gradient terminus (`site.css`) |
| Text Primary | Bone | `#e8e0d4` | Body text |
| Text Secondary | Ash | `#a89f93` | Muted text, descriptions |
| Text Tertiary | Smoke | `#6b6359` | Disabled, placeholder |
| Surface Base | Obsidian / Ink | `#0a0a0a` | Page backgrounds |
| Surface Card | Dark Stone | `#1a1a1a` | Cards, panels |
| Surface Elevated | Stone | `#222222` | Elevated surfaces, hover cards |

> **Tinted surface pair — open.** Mercantile sits on pure neutral surfaces,
> while siblings layer a tinted dark pair (Meridian violet `#1a0a3e`/`#2a1a6e`,
> Tribulation crimson, Prosperity bronze). `VISION.md` §3.1 recommends
> Mercantile adopt deep emerald-blacks (suggested `#0a140d` / `#10241a`) to
> match its siblings' depth. This pair is proposed but not chosen — see §9.

Accent-pairing rule (`VISION.md` §3.1): accents never leave Mercantile
surfaces; no sibling may share *both* accents. Emerald-with-emerald is
Mercantile's pair; Husbandry's proposed leaf green was explicitly checked
against it.

### Typography

- **Headings:** Pixel/blocky display treatment in gradient (`#50C878` → `#00C853`)
- **Body:** Monospace stack: SF Mono, Cascadia Code, Fira Code, Consolas
- **Website gradient animation:** `emerald-pulse` keyframes (4s ease-in-out,
  brightness 1→1.15) — the suite-standard pulse in Mercantile's accent
- **In-game:** vanilla Minecraft font only, ever

---

## 2. Assets

The full asset manifest — every `.glyph` source under `art/`, the final
resource/site path it ships as, and what is still `MISSING` a glyph source —
lives in [`ASSETS.md`](ASSETS.md).

---

## 3. Generation Prompts

### HUD Glyph — shipped (balance scale)

The HUD glyph is a standing merchant's balance scale — the same brand motif as
the logo icon (§1), the most legible option at 16px and a direct tie to the
trade economy. Authored as an ASCII glyph spec through Concord's glyph pipeline
(`/glyph`, `mc-textures` skill), natively at 16px; master at
`art/hud-icon-16.png`, source at `art/glyphs/hud-scales-16.glyph` (with a 32px
variant at `art/glyphs/hud-scales-32.glyph`). It ships as the
`mercantile:reputation_badge` sprite; the scale is a full-color sprite, so
reputation tier reads in the bar color beneath it rather than in the glyph.
To regenerate or revise:

```
Subject: standing merchant's balance scale, one centered motif, no text
Size: 16x16
Colors: Emerald (#50C878) body, Emerald Bright (#6DDB94) highlight, deeper
        emerald (#2C8A57) facet shadow, deepest emerald (#1F6B41)
        underside/base, ink (#0A0A0A) outline, gold (#FFD700) pivot; must read
        on a 50–60% black HUD box
Notes: Stays legible next to Tribulation's skull and Prosperity's chest in
       the HUD stack. Edit art/glyphs/hud-scales-16.glyph, then re-render
       with `/glyph` (or `python3 .ai/skills/mc-textures/scripts/glyph.py
       art/glyphs/hud-scales-16.glyph`).
```

Logo regeneration prompts belong in `art/exploration/` alongside their outputs,
per `REPO-LAYOUT.md`.

---

## 4. Image References

| Image | Reference Source | Notes |
|-------|----------------|-------|
| Market stall motif | `art/logo.png` | Awning, scales, coin pile, chest |
| Emerald glow style | `art/logo.png` | Soft radial green glow on brick, floating particles |
| Brickwork field | `art/logo.png` background | Green-tinted brick — Mercantile's established divergence from suite dark-stone (§1) |
| Pixel type treatment | `art/logo.png` wordmark | Blocky pixel font, emerald fill |
| Pixel art item style | `assets/mercantile/textures/gui/sprites/reputation_badge.png` | Reputation badge sprite — sets the in-game pixel density |
| Sentry pylon | `assets/mercantile/textures/block/` | The mod's one custom block — top/side/bottom |

---

## 5. HUD

Mercantile **holds slot 2** in the Concord HUD stack — the full visual and
coordination spec is [`concord/HUD-STANDARD.md`](../../concord/HUD-STANDARD.md)
(normative; this section records only Mercantile's decisions and status).

**Slot 2 content:** reputation tier label (e.g. `Trusted`) beside the 16×16
glyph, in the standard semi-transparent box. Mercantile qualifies for a slot
because reputation is *persistent ambient state* the player carries while
walking around — exactly the standard's admission test.

**Mercantile-specific behavior:** the element renders only when a villager is
within proximity radius (periodic client-side scan), and respects both the
client toggle (`enableReputationHud`) and the server's synced
`enableReputation` config. Hidden during F1, open screens, and the death
screen per the standard's visibility rules.

**Conformance status** (each item conforms to `HUD-STANDARD.md`):

1. **Sibling stacking offset.** `ReputationHudOverlay` reserves space for a
   higher-priority sibling through the reflection-backed `isHudVisible()` /
   `getHudHeight()` accessors (`HUD-STANDARD.md` §6), applied only at the
   default TOP_LEFT anchor — not a hardcoded height.
2. **`api` package.** The coordination accessors are consumed through
   `com.rfizzle.mercantile.api` (`MercantileAPI`), which exposes the
   `isHudVisible()` / `getHudHeight()` facade per `API-STANDARD.md`.
3. **Glyph.** Slot 2 blits the custom `mercantile:reputation_badge` balance-scale
   sprite (master `art/hud-icon-16.png`; see [`ASSETS.md`](ASSETS.md)).
4. **Anchor config.** Configurable via `hudAnchor` (a four-corner enum:
   TOP_LEFT / TOP_RIGHT / BOTTOM_LEFT / BOTTOM_RIGHT) plus `hudOffsetX` /
   `hudOffsetY` pixel offsets, per the standard.

---

## 6. In-Game Asset Philosophy

Vanilla-first, per `AGENTS.md` and the `design/SPEC.md` preamble (SPEC is
authoritative where they differ):

- **Sounds:** existing vanilla sound events only — no custom audio.
- **Villager pickup items:** player heads with pre-existing skin textures
  (minecraft-heads.com sources, hosted on Mojang's CDN) — no custom item
  textures.
- **Particles:** custom particle *textures* for mod-identity effects (pickup,
  trade cycling, follow mode, sentry pylon); vanilla `dust` for functional
  overlays (workstation links, bell radius), which are
  readouts, not theming.
- **Blocks:** the sentry pylon is the only custom block texture in the mod.
- **Text:** action-bar messages and tooltips are vanilla-toned — short, dry,
  no exclamation points ("Villager will remember that" energy, `VISION.md` §2).

Behavioral detail for every feature lives in [`SPEC.md`](SPEC.md) — this
document never restates it.

---

## 7. Website Specification

- **Domain:** `mercantile.rfizzle.com` (CNAME in `docs/`)
- **Hosting:** GitHub Pages from `docs/` — **legacy**. Per `REPO-LAYOUT.md`,
  the canonical source is structured `site/` content rendered by the shared
  Concord template; Mercantile's migration to it is pending and `docs/` serves
  until the `site/` build is verified live.
- **Pages:** Home (`index.html`), Features, Config, Commands, Changelog, FAQ,
  Guide — matching the suite-standard page set (`VISION.md` §4), with no
  dedicated API page. The `api` package exists (§5) but has no companion site
  page.
- **Theme:** Tailwind with the §1 tokens (`--color-emerald: #50C878`,
  `--color-emerald-bright: #6DDB94` over the shared neutrals);
  `emerald-pulse` heading animation; `.pixelated` rendering for pixel art.
- **SEO:** title `Mercantile — Villager Overhaul for Minecraft`; absolute-URL
  `og-image.png`; `twitter:card` `summary_large_image`. The descriptive title
  is the SEO form; the tagline (§1) leads the human-facing hero copy.
- **Cross-mod footer:** the "Part of **Concord**" strip (four glyphs + names +
  taglines, current mod highlighted) per `VISION.md` §4 — lands with the
  template migration.

---

## 8. Distribution Listings

Store copy lives in-repo at `site/listing-modrinth.md` and
`site/listing-curseforge.md` (Mercantile originated this pattern,
`REPO-LAYOUT.md` §1). Format follows the suite listing standard (`VISION.md`
§4): 128×128 icon, full logo + screenshots in the gallery, tagline → feature
bullets with real numbers → "Enhanced by" section naming siblings as strictly
optional. Required deps: Fabric API only.

The bare `mercantile` slug belongs to an unrelated mod on both stores, so
Mercantile lists under the suite slug convention `<mod>-<domain>-overhaul`
(`VISION.md` §4) — `mercantile-villager-overhaul`. The mod is published on
Modrinth (project id `Bnp3Drhe`) and on CurseForge, both under the
`mercantile-villager-overhaul` slug. Required deps: Fabric API only.

README badges: MC 1.21.1 · Fabric · MIT · release · CI · Modrinth and
CurseForge store download badges.

---

## 9. Open Decisions

Recorded so they read as *undecided*, not as omissions:

1. **Tinted surface pair.** Adopt deep emerald-black surfaces
   (suggested `#0a140d` / `#10241a`, `VISION.md` §3.1) or stay on pure
   neutrals? Recommendation in VISION is to adopt; no pair has been chosen.

---

## 10. Concord Context

Mercantile is a member of **Concord** — *a modular collection of system
overhauls. Install any, combine all.* (That sentence is the entire cross-promotion allowance per
`VISION.md` §2.) Mercantile is the suite's **economic connective tissue**:
sibling integration flows through trade content — reputation-gated exclusive
trades selling sibling items — never through stat leakage (reputation → loot
luck and reputation → enchant discounts are explicitly rejected,
`VISION.md` §8).

| Mod | Verb | Color Signature | Motif | HUD |
|-----|------|----------------|-------|-----|
| Tribulation | Survive | Crimson / Ember | Hourglass, skulls | Slot 1 |
| Meridian | Enchant | Arcane Purple / Gold | Compass rose | No slot |
| **Mercantile** | **Trade** | **Emerald / Emerald Bright** | **Market stall, scales, bell, emeralds** | **Slot 2** |
| Prosperity | Discover | Gold / Diamond Cyan | Chalice, keys | Slot 3 |

Suite-level standards live in the concord repo and are never copied here:
[`VISION.md`](../../concord/VISION.md),
[`API-STANDARD.md`](../../concord/API-STANDARD.md),
[`HUD-STANDARD.md`](../../concord/HUD-STANDARD.md),
[`REPO-LAYOUT.md`](../../concord/REPO-LAYOUT.md),
`design/DESIGN-SYSTEM.md` + `docs/tokens.css`.

All four mods share: Minecraft 1.21.1 · Java 21 · Fabric · MIT · the dark
neutral web theme · monospace stack · pixel-art logo style · optional
Jade/WTHIT, EMI/REI/JEI, Mod Menu, Cloth Config integrations.
