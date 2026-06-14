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

Adopted from `VISION.md` §2, which codifies the suite tagline pattern (a short
declarative sentence about the player's relationship to the system) and proposed
this line to replace the older feature-description copy ("Turn villagers from
disposable trade machines into…"). It states the reputation thesis in three
words and deliberately echoes Prosperity's "Every chest, yours to discover."

> **Adoption status:** Adopted 2026-06-12. The README masthead, the site hero
> lede, and both store listings lead with the tagline; descriptive copy
> ("villager and trade overhaul…") remains as the supporting line and in SEO
> metadata, per the VISION §2 pattern.

### Logo Description

**Full Logo (`art/logo.png`):** A pixel-art market stall — green-and-cream
striped awning, brass scales hanging from the frame, a coin pile and chest on
the counter — set inside a circular stone medallion rimmed with an emerald
glow, on a field of glowing green brickwork with floating emerald particles.
Below, "MERCANTILE" in blocky pixel type with the subtitle "MINECRAFT VILLAGER
OVERHAUL". Vines frame the upper corners.

**Icon (`art/icon-128.png`):** The circular medallion and market stall isolated
on the green brickwork — no text. This is the master for the in-jar
`assets/mercantile/icon.png` and store listing icons.

> **Formula note:** `VISION.md` §3.2 specs the suite logo formula as *dark
> stone brickwork frame, one central glowing motif object, name in blocky pixel
> type* (it also floats a "stone market arch framing a bell above a stack of
> emeralds" as a Mercantile proposal — written before this logo existed). The
> current logo matches the formula's bones (pixel art, stone frame, glowing
> central motif, pixel-type wordmark) but uses a circular medallion on *green*
> brick rather than an arch on dark stone, and a market-stall motif rather
> than a bell. Whether the current art is grandfathered or regenerated closer
> to the formula is an open decision (§9). The market stall / scales motif
> itself is established — it is what siblings already cite for Mercantile
> (Meridian `design/DESIGN.md` §7).

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

> **Tinted surface pair — undecided.** Mercantile is currently the only
> Concord mod sitting on pure neutral surfaces; siblings layer a tinted dark
> pair (Meridian violet `#1a0a3e`/`#2a1a6e`, Tribulation crimson, Prosperity
> bronze). `VISION.md` §3.1 recommends Mercantile adopt deep emerald-blacks
> (suggested `#0a140d` / `#10241a`) to match its siblings' depth. Not yet
> chosen or implemented anywhere — see §9.

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

## 2. Asset Inventory

### Existing Assets

| Asset | Location | Status |
|-------|----------|--------|
| Full logo (master) | `art/logo.png` | Final |
| Mod icon 128×128 (master) | `art/icon-128.png` | Final |
| Web logo | `docs/logo.png` | Derived copy |
| Web icon | `docs/icon.png` | Derived copy |
| In-jar icon | `src/main/resources/assets/mercantile/icon.png` | Final |
| OG image (1200×630) | `docs/og-image.png` | Final |
| Apple touch icon | `docs/apple-touch-icon.png` | Final |
| Favicons | `docs/favicon.ico`, `docs/favicon-32.png` | Final |
| Sentry pylon block textures | `assets/mercantile/textures/block/` | Final — the only custom block textures |
| Custom particle textures | `assets/mercantile/textures/particle/` | Final (pickup, trade cycling, follow, pylon, profession-tinted link mote) |
| GUI sprites | `assets/mercantile/textures/gui/sprites/` | Final (info/close buttons, merchant lock/unlock glyphs, reputation HUD emerald gem) |
| HUD glyph master + sources | `art/hud-icon-16.png`, `art/glyphs/*.glyph` | Final — emerald-gem glyph; sources regenerate via concord's glyph tool |

### Needed Assets

| Asset | Generator | Priority | Spec |
|-------|-----------|----------|------|
| Store gallery screenshots | Screenshot | Medium | 1920×1080, 3–5 shots per `VISION.md` §4 listing standard; at least one captioned sibling-integration shot when those land |
| `art/exploration/` | — | Low | Directory for style explorations and generation prompts, per `REPO-LAYOUT.md` §1 — empty until the logo-formula question (§9) is worked |

---

## 3. Generation Prompts

### HUD Glyph — shipped (single emerald gem)

The bell-vs-emerald motif question (§9) is **resolved: a single cut emerald
gem**, the most legible motif at 16px and a direct tie to the reputation =
emerald economy. Authored as an ASCII glyph spec with concord's glyph tool;
master at `art/hud-icon-16.png`, source at `art/glyphs/hud-emerald-gem.glyph`.
To regenerate or revise:

```
Subject: single cut emerald gem, one centered motif, no text
Size: 16x16
Colors: Emerald (#50C878) body, Emerald Bright (#6DDB94) top facet, deeper
        emerald (#2C8A57) lower-right facet shadow, ink (#0A0A0A) outline,
        bone (#E8E0D4) glint; must read on a 50–60% black HUD box
Notes: Stays legible next to Tribulation's skull and Prosperity's chest in
       the HUD stack. Edit art/glyphs/hud-emerald-gem.glyph, then run
       `python3 scripts/glyph.py <spec>` in the concord repo.
```

Logo regeneration prompts (if §9 resolves toward the stone-frame formula) belong
in `art/exploration/` alongside their outputs, per `REPO-LAYOUT.md`.

---

## 4. Image References

| Image | Reference Source | Notes |
|-------|----------------|-------|
| Market stall motif | `art/icon-128.png` | Awning, scales, coin pile, chest |
| Emerald glow style | `art/logo.png` | Soft radial green glow on brick, floating particles |
| Brickwork field | `art/logo.png` background | Green-tinted brick — divergence from suite dark-stone, see §9 |
| Pixel type treatment | `art/logo.png` wordmark | Blocky pixel font, emerald fill |
| Pixel art item style | `assets/mercantile/icon.png` | Sets the in-game pixel density |
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

**Conformance gaps (tracked, not aspirational hand-waving):**

1. **Hardcoded sibling offset.** `ReputationHudOverlay` uses
   `TRIBULATION_RESERVED_HEIGHT = 22` keyed on bare `isModLoaded("tribulation")`
   — explicitly non-conformant per `HUD-STANDARD.md` §6 (goes stale when the
   Tribulation user disables or moves their HUD). Must migrate to the
   `isHudVisible()` / `getHudHeight()` accessor pattern.
2. **No `api` package yet.** The accessors live in `com.rfizzle.mercantile.api`,
   which does not exist; creating it is Mercantile roadmap item 1
   (`VISION.md` §5.3, §6).
3. ~~**Glyph.** Currently blits the full mod icon scaled to 16×16; needs the
   dedicated `hud-icon-16.png` (§2).~~ **Done** — slot 2 now blits the custom
   `mercantile:reputation_badge` emerald-gem sprite (master `art/hud-icon-16.png`).
4. **Anchor config.** Top-left only today; the standard wants a corner anchor
   enum + pixel offsets.

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
  overlays (workstation links, bell radius, village boundary), which are
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
  `docs/` is retired in favor of structured `site/` content rendered by the
  shared Concord template; Mercantile's migration is pending and `docs/`
  stays until the `site/` build is verified live.
- **Pages:** Home (`index.html`), Features, Config, Commands, Changelog, FAQ,
  Guide — matching the suite-standard page set (`VISION.md` §4), minus a
  dedicated API page, which arrives with the `api` package.
- **Theme:** Tailwind with the §1 tokens (`--color-emerald: #50C878`,
  `--color-emerald-bright: #6DDB94` over the shared neutrals);
  `emerald-pulse` heading animation; `.pixelated` rendering for pixel art.
- **SEO:** title `Mercantile — Villager Overhaul for Minecraft`; absolute-URL
  `og-image.png`; `twitter:card` `summary_large_image`. (Title moves to the
  `Mod — Tagline` pattern when the §1 tagline is adopted.)
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

> **Not publicly listed yet.** The bare `mercantile` slug is taken by an
> unrelated mod on both stores (discovered 2026-06-12), so the suite slug
> convention is `<mod>-<domain>-overhaul` (`VISION.md` §4) —
> `mercantile-villager-overhaul`. Meridian and Tribulation are already live
> on CurseForge under this pattern; the Modrinth project exists as a draft —
> `mercantile-villager-overhaul` (`Bnp3Drhe`) — awaiting submission and
> review (slugs/IDs for all three: `VISION.md` §4). CurseForge registration
> is still pending.
> README/site link only to GitHub Releases until this mod's listings are
> publicly live, then the store links, download badges, and listing
> cross-links come back under the final slugs/IDs.

README badges: MC 1.21.1 · Fabric · MIT · release · CI (store download badges
return when the listings are publicly live).

---

## 9. Open Decisions

Recorded so they read as *undecided*, not as omissions:

1. **Tinted surface pair.** Adopt deep emerald-black surfaces
   (suggested `#0a140d` / `#10241a`, `VISION.md` §3.1) or stay on pure
   neutrals? Recommendation in VISION is to adopt; no pair has been chosen.
2. ~~**HUD glyph motif.** Bell vs. emerald.~~ Resolved 2026-06-14 — **single
   emerald gem** chosen (most legible at 16px; ties to reputation = emerald).
   Shipped as `art/hud-icon-16.png` / `mercantile:reputation_badge` (§2, §3).
3. ~~**Logo vs. stone-frame formula.**~~ Resolved 2026-06-12 — the shipped
   market-stall medallion is ratified as-is in `VISION.md` §3.2; the
   bell-over-emeralds arch proposal is retired. The stone-frame formula
   remains the spec for *new* logos.
4. ~~**Tagline rollout.**~~ Resolved 2026-06-12 — "Every villager remembers."
   adopted across README, site hero, and listings (see §1 Tagline).

---

## 10. Concord Context

Mercantile is a member of **Concord** — *a Vanilla+ collection. Install any,
combine all.* (That sentence is the entire cross-promotion allowance per
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
