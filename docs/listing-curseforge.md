# Mercantile — Villager & Trade Overhaul

![Mercantile logo](https://mercantile.rfizzle.com/logo.png)
<!-- TODO: confirm hosted logo URL renders on CurseForge before publishing -->

[![Minecraft 1.21.1](https://img.shields.io/badge/Minecraft-1.21.1-62B47A?logo=minecraft&logoColor=white)](https://www.minecraft.net/)
[![Fabric](https://img.shields.io/badge/Mod_Loader-Fabric-DBB69B)](https://fabricmc.net/)
[![License: MIT](https://img.shields.io/github/license/rfizzle/mercantile)](https://github.com/rfizzle/mercantile/blob/main/LICENSE)

**Also on [Modrinth](https://modrinth.com/mod/mercantile).** Visit the
[website](https://mercantile.rfizzle.com) for the full feature list, config
reference, and command guide.

---

Mercantile is a villager and trade overhaul for **Minecraft 1.21.1 (Fabric)**.
It turns villagers from disposable trade machines into mobile, named,
persistent characters — with pickup, biome-themed names, a five-tier
reputation system, emerald-based trade cycling, and an iron-fueled sentry
block that defends your village.

**Vanilla-first by design.** No custom textures beyond the sentry pylon, no
balance-breaking shortcuts, no bundled dependencies you didn't ask for. Every
sound is a vanilla sound. Every villager head uses a community skin already
hosted on Mojang's CDN.

## At a glance

- Minecraft **1.21.1**, **Fabric** loader (0.16+), **Fabric API** required.
- Required on the **server**. The reputation HUD is the only client-only
  feature — install it client-side too if you want the readout.
- Every feature is **individually toggleable** through Mod Menu / Cloth Config
  or `config/mercantile.json`.
- MIT licensed.

## Screenshots

<!-- TODO: replace these placeholders with real hosted PNGs before publishing -->

![Picking up a villager](https://mercantile.rfizzle.com/screenshots/pickup.png)
*Sneak-right-click a villager to pick them up. Full NBT — profession, trades,
gossip, XP — is preserved on the head item in your hand.*

![Reputation HUD over villagers](https://mercantile.rfizzle.com/screenshots/reputation-hud.png)
*A compact tier readout shown next to nearby villagers so you can read the
room at a glance.*

![Trade cycling in the merchant screen](https://mercantile.rfizzle.com/screenshots/trade-cycling.png)
*Spend emeralds to re-roll a villager's unlocked trades. No more breaking and
replacing workstations.*

![Sentry pylon defending a village](https://mercantile.rfizzle.com/screenshots/sentry-pylon.png)
*The Sentry Pylon — an iron-fueled defense block that summons temporary iron
golems when hostiles approach.*

![Village boundary visualization](https://mercantile.rfizzle.com/screenshots/village-boundary.png)
*Hold a bell to see the village center, boundary, and every claimed bed,
workstation, and bell as colored particles.*

## Features

### Villager Pickup

Sneak-right-click a villager with an empty hand to pick them up. The villager
becomes a profession-themed player head in your inventory. Place them down
with a right-click; they spawn facing you. Full NBT survives the round trip —
profession, level, XP, trade offers, gossip, custom name, health, inventory.

The tooltip shows profession and level, every trade in `cost → result` form,
locked/out-of-stock indicators, and a `n/m trades unlocked` summary. Costs 5
XP levels by default (configurable, waived in Creative). Won't work during a
raid, on a villager being traded with, or on one currently following another
player.

### Biome-Themed Names

Every villager is auto-named on spawn from a biome-themed pool — common
English names in plains and forests, Arabic in deserts, Nordic in taiga,
Mesoamerican in jungle, African in savanna, Old English in swamps,
frontier-era in badlands. Names are always visible above the villager, persist
across pickup and restart, and respect player-given nametags. Pools are
datapack-driven, so you can override or extend them.

### Reputation

A **single global reputation score per player** that affects every villager,
running parallel to vanilla gossip.

| Tier | Range | Effect |
|---|---|---|
| Reviled | -100 to -50 | Trade refusal, angry villager particles |
| Distrusted | -49 to -1 | Price markup (10-25%) |
| Neutral | 0 | No modifier |
| Liked | 1 to 49 | Small discount (5%) |
| Trusted | 50 to 99 | Moderate discount (10%), profession-exclusive trades |
| Honored | 100+ | Best discount (15%), all exclusive trades |

Reputation goes up for trading, curing zombie villagers, cycling trades, and
spending time near villagers; it goes down for hitting and killing them.
Exclusive trades — both per-profession and cross-profession — appear in your
trade list when you hit the right tier. Other players trading with the same
villager see their own list at their own tier.

### Trade Cycling

A button in the merchant screen that re-rolls a villager's unlocked trades
for 6 emeralds (configurable). Once you've purchased from a trade, that trade
is locked and never re-rolled — so cycling becomes more efficient as you fill
in the slots you want to keep. No cooldown; the emerald cost is the balance.

### Follow Mode

Sneak-right-click a villager while holding an emerald to make them follow
you. Same action again releases them. Up to 3 villagers can follow at once
(configurable). Following villagers ignore their schedule, pathfind to you at
moderate pace, and are immune to mob-on-mob pushing — no more being shoved
off the path. Out of range (>32 blocks) they give up and stop.

### Sentry Pylon

A craftable defense block (3 iron blocks + 1 bell + 1 carved pumpkin + 2
stone bricks). Insert iron blocks as fuel. When a hostile mob enters its
32-block radius, the pylon spends one iron block to spawn a temporary iron
golem that fights and then despawns after 30 seconds of peace. Up to 3 active
sentries per pylon. Sentry golems don't drop loot, don't count toward iron
farms, and don't count toward mob caps. Comparator output reflects fuel
level; a redstone signal disables the pylon entirely.

### Quality-of-Life

- **Bulk trading** — shift-click the trade output to repeat a trade up to 64
  times in one action (or until your inventory fills).
- **Restock indicator** — the merchant screen shows a `Restocks in: ~m:ss`
  estimate and remaining restocks for the day.
- **Demand transparency** — hover the trade price to see the breakdown: base,
  demand, reputation modifier, gossip modifier, final.
- **Pathfinding fixes** — villagers properly traverse fence gates and double
  doors, multi-step staircases, ladders, and route around water instead of
  drowning. Each fix is independently toggleable.
- **Double door sync** — open one half of a double door (or double fence
  gate) and the other half swings with it. Same for closing.
- **Healing enhancement** — splash and lingering potions of healing and
  regeneration are 2× more effective on villagers (configurable).
- **Profession lock** — once you've made any trade with a villager, their
  profession is locked. Breaking the workstation won't wipe them.
- **Villager info panel** — the merchant screen shows name, profession,
  level + XP bar, your reputation with this villager, total trades, and
  workstation status.
- **Reputation HUD** — a compact tier readout next to nearby villagers.
- **Sound volume slider** — separate volume for villager ambient, trade, and
  hurt sounds (0–100%).

### Visualization

- **Workstation links** — hold a bell to see profession-colored particle
  lines between villagers and their workstations. Unbound villagers pulse
  with angry particles; unclaimed workstations glow yellow.
- **Bell radius** — hold a bell to see its 48-block gathering area as a
  particle circle on the ground. Ring a placed bell to highlight every
  villager in range.
- **Village boundary** — hold a bell or run `/mercantile village` to see the
  village center, its bounding box, and every claimed POI color-coded by
  type (blue = bed, yellow = workstation, green = bell).

### Trade Index (EMI / REI / JEI)

A unified, searchable catalog of every villager trade across every
profession and level. Search "mending" in your recipe viewer to find the
librarian trade that sells it. Filter by profession, by level, or by whether
the trade is reputation-locked. Each entry also shows the workstation block
that unlocks the profession — click a workstation (lectern, composter,
smoker, …) in your recipe viewer to list every trade for that profession.
Item lookup integrates naturally — the "Uses" and "Recipes" tabs on any
item include matching villager trades. Works with **EMI**, **REI**, and
**JEI**.

## Optional integrations

Mercantile detects and integrates with these mods when present. **None are
bundled** — install whichever you already use.

- [Mod Menu](https://modrinth.com/mod/modmenu) — config screen entry
- [Cloth Config](https://modrinth.com/mod/cloth-config) — settings GUI
- [Jade](https://modrinth.com/mod/jade) / [WTHIT](https://modrinth.com/mod/wthit)
  — villager tooltip overlays (breeding state, contextual indicators,
  pylon fuel)
- [EMI](https://modrinth.com/mod/emi) / [REI](https://modrinth.com/mod/rei) /
  [JEI](https://www.curseforge.com/minecraft/mc-mods/jei) — recipe viewer
  integration for the trade index

## Requirements

- Minecraft **1.21.1**
- Fabric Loader **0.16+**
- Fabric API
- Required on the **server**. Install on the client as well if you want the
  reputation HUD.

## Installation

1. Install [Fabric Loader](https://fabricmc.net/use/) for 1.21.1.
2. Drop [Fabric API](https://www.curseforge.com/minecraft/mc-mods/fabric-api)
   into your `mods/` folder.
3. Download Mercantile from this CurseForge page (or via the CurseForge
   app / your launcher of choice) and drop it into `mods/` as well.
4. *(Optional)* Add Mod Menu and Cloth Config for the in-game settings
   screen.

## Links

- **Website:** <https://mercantile.rfizzle.com>
- **Modrinth:** <https://modrinth.com/mod/mercantile>
- **GitHub:** <https://github.com/rfizzle/mercantile>
- **Report an issue:** <https://github.com/rfizzle/mercantile/issues>
- **Changelog:** <https://mercantile.rfizzle.com/changelog.html>

## Companion mods

Mercantile is part of the rfizzle mod suite. If you like it, you may also
enjoy:

- [Meridian](https://meridian.rfizzle.com)
- [Tribulation](https://tribulation.rfizzle.com)
- [Prosperity](https://prosperity.rfizzle.com)

## License & credits

Licensed under the [MIT License](https://github.com/rfizzle/mercantile/blob/main/LICENSE).
© 2025 rfizzle. Mercantile is not affiliated with Mojang Studios or
Microsoft.

Villager head skins are community-created textures sourced from
[minecraft-heads.com](https://minecraft-heads.com), permanently hosted on
Mojang's CDN. All sounds and particles (outside the sentry pylon) are
vanilla — no resource-pack download, no surprises.
