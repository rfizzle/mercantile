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
`design/DESIGN.md` §5, Concord Context).

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
> match its siblings' depth. This pair is proposed but not chosen — see Open Decisions.

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

## 2. HUD

Mercantile **holds slot 2** in the Concord HUD stack — the full visual and
coordination spec is
[`HUD-STANDARD.md`](https://github.com/rfizzle/concord/blob/master/HUD-STANDARD.md)
(normative; this section records only Mercantile's decisions and status).

**Why a slot:** reputation is *persistent ambient state* the player carries
while walking around — exactly the standard's admission test. Trade-screen
information (demand, restock timers, the villager info panel) lives in the
merchant screen and never on the HUD.

**Slot 2 content:** the 16×16 `reputation_badge` balance-scale glyph with a
2px bar beneath it, tinted by reputation tier (emerald ramp for the positive
tiers, white at neutral, the shared orange/red state ramp below it). No text —
the tier name is on the hold-to-peek detail panel and in chat on tier change,
not in the badge.

**Mercantile-specific behavior:** the element renders only when a villager is
within proximity radius (periodic client-side scan), and respects both the
client toggle (`enableReputationHud`) and the server's synced
`enableReputation` config. Hidden under all four of the standard's visibility
rules — F1, any open screen, spectator mode, and the death screen.

**Conformance status** (each item conforms to `HUD-STANDARD.md`):

1. **Sibling stacking offset.** `ReputationHudOverlay` reserves space for
   Tribulation's slot 1 through the reflection-backed `isHudVisible()` /
   `getHudHeight()` accessors (§6), applied only at the default TOP_LEFT
   anchor — never a hardcoded height. Every Tribulation release from 1.0.0
   exposes the accessors, so the pre-accessor fixed reservation §6 allowed has
   been retired; an unresolvable accessor reserves nothing.
2. **`api` package.** The coordination accessors are exposed through
   `com.rfizzle.mercantile.api` (`MercantileAPI`), which presents the
   `isHudVisible()` / `getHudHeight()` facade per `API-STANDARD.md`.
3. **Glyph.** Slot 2 blits the custom `mercantile:reputation_badge` balance-scale
   sprite (master `art/hud-icon-16.png`; see [`ASSETS.md`](ASSETS.md)).
4. **Anchor config.** Configurable via `hudAnchor` (a four-corner enum:
   TOP_LEFT / TOP_RIGHT / BOTTOM_LEFT / BOTTOM_RIGHT) plus `hudOffsetX` /
   `hudOffsetY` pixel offsets, per the standard.

---

## 3. Assets

The full asset manifest — every `.glyph` source under `art/`, the final
resource/site path it ships as, and what is still `MISSING` a glyph source —
lives in [`ASSETS.md`](ASSETS.md). This document owns the *why and the look*
of each asset family:

- **Brand masters** (`art/logo.png`, `art/icon-*.png`, `art/glyphs/icon.glyph`)
  — the market-stall medallion and the balance-scale icon described in §1.
- **HUD glyph** (`art/hud-icon-16.png` → `reputation_badge`) — the balance
  scale again, at 16px, full-color rather than a tintable mask, so the tier
  state lives in the bar beneath it and the emerald reads at every tier.
- **In-game textures and particles** — custom pixel art where a vanilla asset
  cannot carry the meaning (the Sentry Pylon block, the pickup sparkle,
  the trade-screen indicators); vanilla where vanilla is genuinely right
  (villager heads for the pickup item are vanilla player-head skins).

---

## 4. Website & Listing Brand Notes

How the brand lands on Mercantile's public surfaces. The content itself lives
elsewhere — page copy under `site/`, store copy in `site/listing-*.md` — so
this section carries only the brand direction, not the copy.

### Where the content lives

- **Website** — `site/site.json` (identity, nav order, theme accents) plus one
  `site/pages/<slug>.json` per page (home, features, config, commands, guide,
  faq, api, changelog), rendered and deployed by the shared Concord Eleventy
  template at `mercantile.rfizzle.com`. The template owns surfaces, neutrals,
  the SEO/OG scaffolding, and the cross-mod footer; the mod supplies only its
  content and accent colors.
- **Store listings** — `site/listing-curseforge.md` and
  `site/listing-modrinth.md` (plus `listing-summary.txt` and
  `github-description.txt`), authored per the `mc-listing` skill.
- **Release notes** — `changelogs/<version>.md` when curated, otherwise
  generated from the merged PRs (the `mc-changelog` skill).

### Accent usage

Emerald (`#50C878`) and Emerald Bright (`#6DDB94`) carry every branded moment:
hero glow, headings, links, card borders, and the reputation ramp in-game. The
heading gradient runs Emerald → Trade Green (`#00C853`). Base surfaces and
body text stay on the shared Concord neutrals (bone/ash/smoke over
ink/card/elevated) until the tinted pair below is decided. The accents are
declared once in `site.json`'s `theme` block; the full token set lives in
`design/DESIGN-SYSTEM.md`.

### Hero & gallery art direction

The hero leads with the full logo over the green-brickwork field. Gallery
shots (1920×1080, vanilla or a light shader for clarity) should show villagers
as people: a named villager's info panel mid-trade, a picked-up villager in
hand, the reputation badge beside a market, a Sentry Pylon with its golems
at dusk.

### OG image

The full logo on the dark field (`site/assets/og-image.png`), served from an
absolute URL; social cards use the large-summary format.

---

## 5. Concord Context

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
| **Mercantile** | **Trade** | **Emerald / Emerald Bright** | **Market stall, scales, bell, emeralds** | **Slot 2** |
| Prosperity | Discover | Gold / Diamond Cyan | Treasure chest, key | Slot 3 |
| Meridian | Enchant | Arcane Purple / Gold | Compass rose | No slot |
| Respite | Rest | Moonlight Indigo / Candleglow | Hanging lantern | No slot |
| Distillation | Brew | Potion Magenta / Copper | Alchemist's still | No slot |
| Cultivation | Grow | Wheat Amber / Leaf Green | Wheat sheaf, scythe and hoe | No slot |
| Instinct | Raise | Heart Rose / Hide Russet | Paw print | No slot |

Suite-level standards live in the concord repo and are never copied here:
[`VISION.md`](https://github.com/rfizzle/concord/blob/master/VISION.md),
[`API-STANDARD.md`](https://github.com/rfizzle/concord/blob/master/API-STANDARD.md),
[`HUD-STANDARD.md`](https://github.com/rfizzle/concord/blob/master/HUD-STANDARD.md),
[`REPO-LAYOUT.md`](https://github.com/rfizzle/concord/blob/master/REPO-LAYOUT.md),
[`design/DESIGN-SYSTEM.md`](https://github.com/rfizzle/concord/blob/master/design/DESIGN-SYSTEM.md).

All members share: Minecraft 1.21.1 · Java 21 · Fabric · MIT · the dark
neutral web theme · monospace stack · pixel-art logo style · optional
Jade/WTHIT, EMI/REI/JEI, Mod Menu, Cloth Config integrations.

---

## Open Decisions

Recorded so they read as *undecided*, not as omissions:

1. **Tinted surface pair.** Adopt deep emerald-black surfaces
   (suggested `#0a140d` / `#10241a`, `VISION.md` §3.1) or stay on pure
   neutrals? Recommendation in VISION is to adopt; no pair has been chosen.
