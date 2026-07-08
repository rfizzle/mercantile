# Mercantile — Villager & Trade Overhaul

**_Every villager remembers._**

![Mercantile logo](https://raw.githubusercontent.com/rfizzle/mercantile/master/art/logo.png)

**Also on [CurseForge](https://www.curseforge.com/minecraft/mc-mods/mercantile-villager-overhaul)
and [GitHub Releases](https://github.com/rfizzle/mercantile/releases).**
Visit the [website](https://mercantile.rfizzle.com) for the full feature
list, config reference, and command guide.

---

Mercantile is a villager and trade overhaul for **Minecraft 1.21.1 (Fabric)**.
It turns villagers from disposable trade machines into mobile, named,
persistent characters — with pickup, biome-themed names, a six-tier
reputation system, emerald-based trade cycling, and an iron-fueled sentry
block that defends your village.

**Restrained by design.** Custom art is scoped to what earns its place — the
sentry pylon block, a set of bespoke effect particles, and a handful of HUD/GUI
glyphs in the Concord palette; everything else leans on vanilla. No
balance-breaking shortcuts, no bundled dependencies you didn't ask for. Every
sound is a vanilla sound, and every villager head uses a community skin already
hosted on Mojang's CDN.

## At a glance

- Minecraft **1.21.1**, **Fabric** loader (0.16.10+), **Fabric API** required.
- Required on the **server**. The reputation HUD is the only client-only
  feature — install it client-side too if you want the readout.
- Every feature is **individually toggleable** through Mod Menu / Cloth Config
  or `config/mercantile.json`.
- MIT licensed.

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

### Work Orders

Stop playing workstation roulette. Sneak-right-click an unemployed adult
villager while holding a profession's workstation item — a lectern, composter,
smithing table — and it walks to the nearest unclaimed job site of that type
and takes the matching profession. The held item only names the job and is
never consumed; the block must already be placed. Each order costs a small
emerald fee (1 by default, waived in Creative); if no free workstation is in
range the order is refused and nothing is charged. Professions resolve from the
block's point-of-interest, so modded jobs work automatically.

### Villager Mood

Every villager tracks a mood from its living conditions — a claimed bed and
workstation, recent sleep, food on hand, staying unharmed, and not witnessing a
neighbor's death — settling into one of four tiers: Miserable, Unhappy,
Content, or Happy. Mood is a nudge, not a gate: Happy villagers give a small
discount and restock sooner; Miserable ones add a small markup and restock
later. The tier shows in the info panel and Jade/WTHIT tooltips and appears as
its own line in the price breakdown. Mood belongs to the villager — the same
for every player — and persists across pickup and reload.

### Reputation

A **single global reputation score per player** that affects every villager,
running parallel to vanilla gossip.

| Tier | Range | Effect |
|---|---|---|
| Reviled | -200 to -150 | Trade refusal, angry villager particles |
| Distrusted | -149 to -1 | Price markup (10-25%) |
| Neutral | 0 to 74 | No modifier |
| Liked | 75 to 299 | Small discount (5%) |
| Trusted | 300 to 999 | Moderate discount (10%), profession-exclusive trades |
| Honored | 1000+ | Best discount (15%), all exclusive trades |

Reputation goes up for trading, curing zombie villagers, cycling trades,
spending time near villagers, gifting profession-appropriate items, and
defending the village from raids (any player awarded Hero of the Village gains
a full bonus, outside the daily cap); it goes down for hitting and killing
them. Fallen out of favor? Negative reputation slowly recovers toward neutral
on its own, and gifting villagers speeds the climb — even a Reviled player has
a clear path back. Exclusive trades — per-profession, cross-profession, and a
sticky bonus offer from wandering traders at Trusted tier and up — appear in
your trade list when you hit the right tier. Other players trading with the
same villager see their own list at their own tier. At Honored the gifting runs
both ways — a nearby villager will occasionally walk over and toss you a small
profession-flavored thank-you.

### Delivery Contracts

An employed villager occasionally posts a delivery request, a speech bubble
floating over its head. Sneak-use paper on it to sign: the paper becomes a
contract item naming a profession-appropriate haul (32 wheat for a farmer, coal
for an armorer), an emerald payment, and a deadline (2 in-game days by
default). While the contract is in hand its villager glows through walls, so
you can find them again. Right-click the payee with the goods anywhere in your
inventory to deliver — items and contract are consumed, emeralds paid, and you
earn a reputation bonus that bypasses the daily cap (up to a few deliveries a
day). Expired contracts crumble with no penalty. Request pools are
datapack-driven per profession.

### Nitwit Rehabilitation

Nitwits stop being dead weight. At Trusted standing and above, use a golden
apple on an adult nitwit and pay an emerald fee (16 by default) — after a short
pause it sheds the green robe and becomes an unemployed villager, ready to
claim a workstation like any other; its name and gossip carry over. The
conversion is one-way and checked at use time: below Trusted you're told what
standing it takes, and nothing is charged on a denied attempt. With reputation
disabled, only the apple and emerald cost apply.

### Trade Cycling

A button in the merchant screen that re-rolls a villager's unlocked trades
for 6 emeralds (configurable). Once you've purchased from a trade, that trade
is locked and never re-rolled — so cycling becomes more efficient as you fill
in the slots you want to keep. No cooldown; the emerald cost is the balance.

### Trade Pinning

Pin the trades you're waiting on. Each row in the merchant screen carries a
small pin toggle; pinned trades are remembered per player and survive
relogging. When a villager restocks a pinned trade that had sold out and you're
within range (128 blocks by default), an action-bar note names the villager and
the trade. `/mercantile pins` lists your pins with live stock status, and the
reputation detail panel shows the same list. Pins are capped per player (10 by
default) and pruned automatically when a villager dies or re-rolls the trade
away.

### Market Day

Every 7 in-game days (configurable) the world holds a market day from dawn to
dusk. Every trade takes a global discount (5% by default) that stacks with
reputation and gossip, and villagers gain an extra restock cycle. The day opens
with a bell ring, happy particles, and an action-bar announcement for everyone
online. The schedule is world-wide and predictable, so it's easy to plan
around.

### Memorials, Mourning & Fear

Villager deaths carry weight. When a named villager dies it drops a memorial
keepsake — a custom skull whose tooltip records its name, profession, level,
and cause of death (a keepsake, not a revive; unnamed villagers drop nothing).
Nearby villagers mourn for a few seconds, turning toward where their neighbor
fell and shedding grief particles — purely cosmetic. And villages remember who
did it: kill several villagers in one village within a short window (3 in 10
minutes by default) and its survivors fear you — a price markup (25% by
default) that applies to the killer alone, in that village alone, fading over a
few in-game days. It shows as its own "Fear" line in the price breakdown.

### Follow Mode

Sneak-right-click a villager while holding an emerald to make them follow
you. Same action again releases them — and a released villager walks back to
its bed or workstation instead of standing where you left it. Up to 3
villagers can follow at once (configurable). Following villagers ignore their
schedule, pathfind to you at moderate pace, and are immune to mob-on-mob
pushing — no more being shoved off the path. Out of range (>32 blocks) they
give up and stop.

### Sentry Pylon

A craftable defense block (3 iron blocks + 1 bell + 1 carved pumpkin + 2
stone bricks). Insert iron blocks as fuel. When a hostile mob enters its
32-block radius, the pylon spends one iron block to spawn a temporary iron
golem that fights and then despawns after 30 seconds of peace. Up to 3 active
sentries per pylon. Sentry golems don't drop loot, don't count toward iron
farms, and don't count toward mob caps. On spotting a threat the pylon also
rings the nearest village bell to call players to the fight. Comparator output
reflects fuel level; a redstone signal disables the pylon entirely.

### Quality-of-Life

- **Bulk trading** — shift-click the trade output to repeat a trade up to 64
  times in one action (or until your inventory fills).
- **Restock indicator** — the merchant screen shows a `Restocks in: ~m:ss`
  estimate and remaining restocks for the day.
- **Demand transparency** — hover the trade price to see the full breakdown:
  base, demand, reputation, mood, gossip, market-day discount, fear markup,
  final.
- **Pathfinding fixes** — villagers properly traverse fence gates and double
  doors, multi-step staircases, ladders, and route around water instead of
  drowning. Each fix is independently toggleable.
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
- **Baby feeding** — right-click a baby villager with bread, carrot, potato, or
  beetroot to shave a percentage off its remaining growth time (10% per bread
  by default), up to a configurable cap.
- **Tutorial advancements** — a dedicated Mercantile advancement tab teaches the
  mod's gestures as you play; each advancement's description spells out the
  exact interaction that grants it, so hidden features are discoverable without
  a wiki.
- **In-game hints** — item and block tooltips explain the mod's gestures where
  you'd reach for them — the sentry pylon, delivery contracts — so the how is
  in your hand, not on a wiki.

### Visualization

- **Workstation links** — hold a bell to see profession-colored particle
  lines between villagers and their workstations. Unbound villagers pulse
  with angry particles; unclaimed workstations glow yellow.
- **Bell radius** — hold a bell to see its 48-block gathering area as a
  particle circle on the ground. Ring a placed bell to highlight every
  villager in range.

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

## Commands

Player commands: `/mercantile pins` lists your pinned trades (`pins remove <n>`,
`pins clear`). Operator commands cover reputation adjustment
(`/mercantile reputation set|add`) and hot-reloading config
(`/mercantile reload`). Full reference:
[mercantile.rfizzle.com/commands.html](https://mercantile.rfizzle.com/commands.html)

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
- [Meridian](https://meridian.rfizzle.com) — with Meridian installed,
  high-standing librarians sell salvage tomes, shelf materials, and rolled
  Meridian enchanted books, widening with your reputation. Pure data; nothing
  changes when it isn't installed.

## Requirements

- Minecraft **1.21.1**
- Fabric Loader **0.16.10+**
- Fabric API
- Java **21+**
- Required on the **server**. Install on the client as well if you want the
  reputation HUD.

## Installation

1. Install [Fabric Loader](https://fabricmc.net/use/) for 1.21.1.
2. Drop [Fabric API](https://modrinth.com/mod/fabric-api) into your `mods/`
   folder.
3. Download Mercantile from this Modrinth page (via the Modrinth App, Prism
   Launcher's Modrinth tab, or a manual jar drop) and place it into `mods/`
   as well.
4. *(Optional)* Add Mod Menu and Cloth Config for the in-game settings
   screen.

## Links

- **Website:** <https://mercantile.rfizzle.com>
- **CurseForge:** <https://www.curseforge.com/minecraft/mc-mods/mercantile-villager-overhaul>
- **GitHub:** <https://github.com/rfizzle/mercantile>
- **Report an issue:** <https://github.com/rfizzle/mercantile/issues>
- **Changelog:** <https://mercantile.rfizzle.com/changelog.html>

## Companion mods

Mercantile is part of [Concord](https://github.com/rfizzle/concord) — a
modular collection of system overhauls. Install any, combine all:

- [Meridian](https://meridian.rfizzle.com) — Chart your enchantments.
- [Tribulation](https://tribulation.rfizzle.com) — Survive what comes next.
- [Prosperity](https://prosperity.rfizzle.com) — Every chest, yours to discover.

## License & credits

Licensed under the [MIT License](https://github.com/rfizzle/mercantile/blob/master/LICENSE).
© 2026 rfizzle. Mercantile is not affiliated with Mojang Studios or
Microsoft.

Villager head skins are community-created textures sourced from
[minecraft-heads.com](https://minecraft-heads.com), permanently hosted on
Mojang's CDN. Every sound is vanilla, and the custom art — the sentry pylon,
the effect particles, and the HUD/GUI glyphs — ships inside the jar, so there's
no resource-pack download and no surprises.
