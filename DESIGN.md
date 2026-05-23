# Mercantile — Design Specification

> Villager & Trade Overhaul for Minecraft 1.21.1 Fabric

---

## 1. Brand Identity

### Narrative

Mercantile reimagines Minecraft's villagers as living, nameable companions with a reputation-driven economy. The name evokes medieval commerce — bustling market squares, haggling merchants, and the weight of emeralds. The visual language draws from **medieval trade**, **merchant stalls**, **balance scales**, and **lush vine-wrapped stonework** — commerce growing organically within a living world.

### Tagline

*"Trade with meaning."*

### Logo Description

**Full Logo (`Mercantile-Logo.png`):** A circular stone frame wrapped in emerald-studded vines encloses a wooden market stall with a striped green-and-cream awning. The stall displays goods — burlap sacks, gold coins, stacked emerald currency, clay pots, produce. Above the stall, a golden balance scale hangs from a vine-wrapped branch with emerald leaves. Flanking the scene are parchment scrolls with trade symbols. Below, "MERCANTILE" in a blocky pixel font on a stone tablet, with "MINECRAFT VILLAGER OVERHAUL" subtitle. Background is dark stone brickwork with scattered chests and coins.

**Icon (`Mercantile-Icon.png`):** The market stall and balance scale isolated within the vine-wrapped stone circle. Rich emerald glow radiating outward. No text. Dense with trade goods detail.

**In-Game Icon (`assets/mercantile/icon.png`):** A pixel-art balance scale — green/emerald weighted pans on a dark background, simple and iconic.

### Color Palette

| Role | Color | Hex | Usage |
|------|-------|-----|-------|
| Primary | Deep Forest | `#0d1f0d` | Backgrounds, dark surfaces |
| Secondary | Dark Moss | `#1a2e1a` | Mid-tones, card backgrounds |
| Accent 1 | Emerald Green | `#50C878` | Glows, highlights, interactive elements |
| Accent 2 | Trade Gold | `#DAA520` | Coins, scales, warm accents |
| Bright | Emerald Bright | `#00C853` | Hover states, emphasis |
| Glow | Villager Green | `#2E8B57` | Particle effects, reputation indicators |
| Warm | Stall Wood | `#8B6914` | Wooden elements, earthy accents |
| Text Primary | Bone | `#e8e0d4` | Body text |
| Text Secondary | Ash | `#a89f93` | Muted text, descriptions |
| Text Tertiary | Smoke | `#6b6359` | Disabled, placeholder |
| Surface Base | Obsidian | `#0a0a0a` | Page backgrounds |
| Surface Card | Dark Stone | `#1a1a1a` | Cards, panels |
| Surface Elevated | Stone | `#222222` | Elevated surfaces, hover cards |

### Typography

- **Headings:** Pixel/blocky display font in gradient (`#50C878` → `#00C853`)
- **Body:** Monospace stack: SF Mono, Cascadia Code, Fira Code, Consolas
- **Website gradient animation:** `emerald-pulse` keyframes (4s ease-in-out, brightness 1→1.15)

---

## 2. Asset Inventory

### Existing Assets

| Asset | Location                                        | Size | Status |
|-------|-------------------------------------------------|------|--------|
| Full Logo | `logo.png`                                         | ~6MB | Final |
| Icon (large) | `Mercantile-Icon.png`                           | ~6MB | Final |
| In-Game Icon | `src/main/resources/assets/mercantile/icon.png` | 128×128 | Final — pixel-art balance scale |

### Needed Assets

| Asset | Generator | Priority | Spec |
|-------|-----------|----------|------|
| Recipe browser icon (EMI/REI/JEI tab) | PixelLab | High | 16×16 or 32×32, emerald/scales motif |
| HUD reputation indicator | PixelLab | High | 16×16 icon + compact text — single persistent HUD element showing current reputation tier |
| Profession head icons (documentation) | PixelLab | Medium | 16×16 pixel art per villager profession for docs/guides |
| Reputation tier icons | PixelLab | Medium | 16×16 set of 6 icons (Reviled → Honored) |
| Website hero background | Gemini | Medium | 1920×600 — stone brickwork with scattered emeralds and trade goods |
| Open Graph image | Gemini | Medium | 1200×630, logo centered on dark background |
| CurseForge gallery screenshots | Screenshot | High | 1920×1080, showing pickup, naming, reputation, trade cycling |
| Favicon (`.ico` / `.svg`) | Derived | Low | 32×32 / 16×16 from icon |
| Apple Touch Icon | Derived | Low | 180×180 from icon |
| Discord embed banner | Gemini | Low | 1280×640, logo on dark background |
| Website (`docs/` directory) | Manual | High | Full site — currently missing |
| CNAME file | Manual | High | `mercantile.rfizzle.com` |

---

## 3. Generation Prompts

### Gemini Prompts (Logos / High-Res Art)

**Open Graph / Social Card:**
```
Pixel art style, 1200x630 banner image for a Minecraft mod called "Mercantile".
Center the logo: a circular stone frame wrapped in emerald-studded vines,
enclosing a wooden market stall with a green-and-cream striped awning,
displaying trade goods. A golden balance scale hangs above. The word
"MERCANTILE" in blocky pixel font below. Dark forest green (#0d1f0d)
background. Emerald green glow and gold coin particle effects. Style
consistent with the existing Mercantile logo.
```

**Website Hero Background:**
```
Pixel art tileable background texture, 1920x600. Dark stone brickwork
(#0d1f0d to #1a2e1a gradient) with subtle emerald gems embedded in mortar
joints. Faint vine tendrils creeping across the surface. A few scattered
gold coins catching light. Very subtle — this is a background behind text.
Minecraft pixel art style, 16-pixel grid aligned.
```

**Discord Banner:**
```
Pixel art banner, 1280x640. The Mercantile market stall icon centered on a
dark forest green (#0d1f0d) background. Soft emerald glow radiating from
center. Vine border framing. "Mercantile" in emerald-green pixel font below
the icon. "Villager Overhaul" subtitle in lighter text. Clean, minimal.
```

### PixelLab Prompts (Pixel Art)

**Recipe Browser Icon (EMI/REI/JEI Tab):**
```
Theme: Medieval trade / commerce
Subject: Balance scale with emerald gems
Style: Minecraft item icon, pixel art
Size: 32x32
Colors: Gold (#DAA520) scale frame, emerald green (#50C878) gems in pans,
        dark background
Notes: Must read clearly at 16x16 downscale. No text. Single centered motif.
       Should suggest weighing/trading at a glance.
```

**HUD Reputation Indicator:**
```
Theme: Social standing / reputation
Subject: Small emerald or balance scale icon for persistent HUD display
Style: Minecraft HUD icon, pixel art, minimal and flat
Size: 16x16
Colors: Emerald green (#50C878) base, tier-dependent tint
        (red for Reviled → gray Neutral → gold for Honored)
Notes: The ONE persistent HUD element for Mercantile. Displays as a small
       icon + reputation tier name or value. Must be compact enough to sit
       alongside the other mods' HUD indicators without cluttering the screen.
       Could use a single icon tinted by code, or match the 6-variant
       Reputation Tier Icons set. Transparent background.
```

### HUD Philosophy

Mercantile has exactly **one persistent HUD element** — a compact reputation tier
indicator. All detailed villager info (name, profession, trades, workstation links)
is surfaced through Jade/WTHIT overlays. Trade cycling, pickup, and following
mechanics use vanilla-style action feedback (particles, sounds, chat). The HUD
indicator gives at-a-glance reputation awareness; details are accessed by looking
at villagers (Jade) or interacting directly.

**Reputation Tier Icons (set of 6):**
```
Theme: Social standing / reputation
Subject: Six 16x16 icons representing reputation tiers:
  1. Reviled — red broken emerald, angry particles
  2. Distrusted — orange/yellow caution, cracked emerald
  3. Neutral — gray emerald, plain
  4. Liked — green emerald, small glow
  5. Trusted — bright green emerald with gold trim
  6. Honored — golden crowned emerald, radiant glow
Style: Minecraft item icons, pixel art, consistent set
Size: 16x16 each
Colors: Progress from red (#DC143C) through gold (#DAA520) to bright
        emerald (#50C878), with the emerald gem as unifying motif
```

**Villager Profession Icons (set of 16):**
```
Theme: Minecraft villager professions
Subject: Sixteen 16x16 icons representing each profession:
  Armorer, Butcher, Cartographer, Cleric, Farmer, Fisherman, Fletcher,
  Leatherworker, Librarian, Mason, Nitwit, Shepherd, Toolsmith,
  Weaponsmith, Unemployed, Baby
Style: Minecraft item icons, pixel art, consistent set
Size: 16x16 each
Colors: Each profession uses its vanilla-associated colors. Gold (#DAA520)
        border or accent unifying the set. Simple tool/item motif per
        profession (e.g., anvil for armorer, book for librarian).
```

---

## 4. Image References

| Image | Reference Source | Notes |
|-------|----------------|-------|
| Market stall motif | Mercantile-Icon.png | Wooden stall, striped awning, trade goods |
| Balance scale | Mercantile-Logo.png, in-game icon.png | Golden scales — key brand symbol |
| Vine wrapping | Mercantile-Icon.png outer ring | Emerald-studded vine on stone circle |
| Emerald color | Mercantile-Icon.png glow | The specific green tone of the emerald accents |
| Trade goods style | Mercantile-Logo.png stall contents | Burlap, coins, pots — warm earth tones |
| Pixel density | `assets/mercantile/icon.png` | Simple balance scale — sets in-game pixel style |
| Background texture | Mercantile-Logo.png background | Dark stone brickwork, scattered chests |

---

## 5. Website Specification

### Domain & Hosting

- **Domain:** `mercantile.rfizzle.com`
- **Hosting:** GitHub Pages from `docs/` directory
- **CNAME:** `docs/CNAME` → `mercantile.rfizzle.com`
- **Status:** Not yet created — needs full site build

### Pages to Create

| Page | File | Content |
|------|------|---------|
| Home | `index.html` | Hero with logo, feature overview (pickup, naming, reputation, cycling), download links |
| Features | `features.html` | Detailed feature breakdown — pickup system, naming, trade cycling, reputation tiers, sentry pylon |
| Config | `config.html` | Configuration reference — all toggles, thresholds, costs |
| Commands | `commands.html` | Command reference |
| Getting Started | `guide.html` | Installation, first pickup, understanding reputation |
| FAQ | `faq.html` | Common questions, vanilla compatibility |
| Changelog | `changelog.html` | Version history |

### Website Design Tokens (Tailwind)

```javascript
colors: {
    base: '#0a0a0a',
    card: '#1a1a1a',
    elevated: '#222222',
    emerald: { DEFAULT: '#50C878', dark: '#2E8B57' },
    gold: { DEFAULT: '#DAA520', bright: '#FFD700' },
    bone: '#e8e0d4',
    ash: '#a89f93',
    smoke: '#6b6359',
}
```

### SEO & Social

- **Title pattern:** `{Page} — Mercantile | Villager Overhaul for Minecraft`
- **og:image:** Absolute URL (`https://mercantile.rfizzle.com/logo.png`)
- **twitter:card:** `summary_large_image`
- **Favicon:** `<link rel="icon" type="image/png" href="icon.png">`
- **Apple Touch:** `<link rel="apple-touch-icon" href="apple-touch-icon.png">`

### Cross-Mod Navigation

Footer section linking to all companion mods:
```
Part of the rfizzle mod suite:
[Meridian] [Mercantile] [Tribulation] [Prosperity]
```

---

## 6. Distribution Listings

### CurseForge / Modrinth

**Description Template:**
1. Logo image (centered)
2. One-paragraph summary
3. Feature list with headers (Villager Pickup, Biome Names, Trade Cycling, Reputation, Sentry Pylon)
4. Screenshot gallery (3–5 images)
5. Requirements section (Fabric Loader, Fabric API, Cloth Config)
6. Optional dependencies (EMI/REI/JEI, Jade/WTHIT)
7. Links to companion mods

**Screenshot Standards:**
- Resolution: 1920×1080
- Shader: Complementary Shaders (or vanilla for clarity)
- HUD: Visible but not cluttered
- Subjects: (1) Picking up a villager (sneak + right-click), (2) Villager with biome name visible, (3) Trade cycling GUI, (4) Reputation tier tooltip, (5) Sentry pylon in a village

**Changelog Format:**
```markdown
## [0.1.0] — 2025-XX-XX
### Added
- Feature description
### Changed
- Change description
### Fixed
- Fix description
```

### README Badges

```markdown
![Minecraft](https://img.shields.io/badge/Minecraft-1.21.1-green)
![Fabric](https://img.shields.io/badge/Loader-Fabric-blue)
![License](https://img.shields.io/badge/License-MIT-yellow)
![GitHub](https://img.shields.io/github/v/release/rfizzle/mercantile)
```

---

## 7. Companion Mod Context

Mercantile is part of a four-mod suite. Each mod overhauls a different Minecraft system:

| Mod | Domain | Color Signature | Icon Motif |
|-----|--------|----------------|------------|
| **Meridian** | Enchanting | Violet / Gold | Compass rose |
| **Mercantile** | Villagers & Trade | Green / Emerald | Market stall / scales |
| **Tribulation** | Difficulty & Scaling | Crimson / Red | Hourglass with hearts |
| **Prosperity** | Loot & Containers | Gold / Green | Trophy chalice |

All four share:
- Minecraft 1.21.1, Java 21, Fabric
- Dark base website theme (`#0a0a0a` / `#1a1a1a` / `#222222`)
- Bone/Ash/Smoke text palette
- Monospace font stack
- Pixel art logo style (Gemini-generated)
- Same website structural pattern (hero → features → config → commands)
- MIT license
- Optional Jade/WTHIT, EMI/REI/JEI, ModMenu, Cloth Config integrations
