<p align="center">
  <img src="art/logo.png" alt="Mercantile logo" width="600">
</p>

<p align="center"><strong>Every villager remembers.</strong></p>

<p align="center">
  <a href="https://www.minecraft.net/"><img alt="Minecraft 1.21.1" src="https://img.shields.io/badge/Minecraft-1.21.1-62B47A?logo=minecraft&logoColor=white"></a>
  <a href="https://fabricmc.net/"><img alt="Fabric" src="https://img.shields.io/badge/Mod_Loader-Fabric-DBB69B"></a>
  <a href="LICENSE"><img alt="License: MIT" src="https://img.shields.io/github/license/rfizzle/mercantile"></a>
  <a href="https://github.com/rfizzle/mercantile/releases"><img alt="Latest release" src="https://img.shields.io/github/v/release/rfizzle/mercantile?include_prereleases"></a>
  <a href="https://github.com/rfizzle/mercantile/actions/workflows/ci.yml"><img alt="CI" src="https://github.com/rfizzle/mercantile/actions/workflows/ci.yml/badge.svg"></a>
</p>

A villager and trade overhaul for Minecraft 1.21.1 (Fabric). Mercantile turns
villagers from disposable trade machines into mobile, named, persistent
characters — with pickup, biome-themed names, a five-tier reputation system,
emerald-based trade cycling, and an iron-fueled sentry block that defends your
village. Vanilla-coherent design: custom pixel-art textures where they raise the
bar, vanilla assets where they already map cleanly; no balance-breaking shortcuts.

## Download

| [GitHub Releases](https://github.com/rfizzle/mercantile/releases) | [Website](https://mercantile.rfizzle.com) | [Report an issue](https://github.com/rfizzle/mercantile/issues) |
| --- | --- | --- |

## Features

- **Villager Pickup** — sneak-right-click a villager to pick it up. Full NBT
  (profession, trades, gossip, XP) is preserved on the held item.
- **Villager Names** — biome-themed names are auto-assigned on spawn and stay
  visible above every villager.
- **Trade Cycling** — refresh a villager's trade pool with emeralds instead of
  breaking and replacing their workstation.
- **Reputation** — a five-tier global standing with persistent gossip that
  travels with villagers across the world. Defend raids and gift villagers to
  climb the tiers (or let negative standing decay back from Reviled); high
  standing even unlocks a wandering trader's exclusive offer.
- **Sentry Pylon** — an iron-fueled defense block that spawns temporary iron
  golems to guard your village.
- **Reputation HUD** — a compact on-screen tier readout shown next to nearby
  villagers, so you can read the room at a glance.

See the [full feature list](https://mercantile.rfizzle.com/features.html) on
the website for every behavior, tuning knob, and edge case.

## Installation

**Requirements**

- Minecraft 1.21.1
- Fabric Loader 0.16+
- Fabric API

**Steps**

1. Install [Fabric Loader](https://fabricmc.net/use/) for 1.21.1.
2. Drop [Fabric API](https://modrinth.com/mod/fabric-api) into your `mods/`
   folder.
3. Download `mercantile-<version>.jar` from
   [GitHub Releases](https://github.com/rfizzle/mercantile/releases) and drop
   it into `mods/` as well.
4. (Optional) Install [Mod Menu](https://modrinth.com/mod/modmenu) and
   [Cloth Config](https://modrinth.com/mod/cloth-config) to access the in-game
   settings screen.

Mercantile is required on the server. The HUD overlay is the only client-side
feature; install it on the client for the readout to appear.

## Controls

| Control | Default | Description |
|---------|---------|-------------|
| **Peek Reputation Detail** | _Unbound_ | Hold to overlay a framed panel showing your reputation score, standing, progress to the next standing, the perks that standing grants, and the villager professions standing nearby. Release to dismiss. |

Bind it under **Options → Controls → Key Binds**, in the **Mercantile**
category. It is unbound by default — assign a key to use it. The panel never
captures the mouse or pauses the game (like vanilla's hold-Tab player list) and
hides automatically during F1, open screens, and spectator mode.

## Optional integrations

Mercantile detects and integrates with these mods when present (none are
bundled):

- [Mod Menu](https://modrinth.com/mod/modmenu) — config screen entry
- [Cloth Config](https://modrinth.com/mod/cloth-config) — settings GUI
- [Jade](https://modrinth.com/mod/jade) /
  [WTHIT](https://modrinth.com/mod/wthit) — villager tooltip overlays
- [EMI](https://modrinth.com/mod/emi) /
  [REI](https://modrinth.com/mod/rei) /
  [JEI](https://www.curseforge.com/minecraft/mc-mods/jei) — recipe viewer
  support for trade-cycling recipes

## Links

- Website: <https://mercantile.rfizzle.com>
- Releases: <https://github.com/rfizzle/mercantile/releases>
- Issues: <https://github.com/rfizzle/mercantile/issues>
- Changelog: <https://mercantile.rfizzle.com/changelog.html>

## For Mod Developers

Mercantile exposes a stable, read-only API and two server-side events in
`com.rfizzle.mercantile.api`, following the
[Concord API Standard](https://github.com/rfizzle/concord/blob/master/API-STANDARD.md).
Use it as a soft dependency: compile against the mod with `modCompileOnly` and
guard every call with `FabricLoader.isModLoaded("mercantile")`. Everything
outside the `api` package is internal and may change in any release.

**The stable surface**

- `MercantileAPI.getReputation(ServerPlayer)` — the player's reputation score
- `MercantileAPI.getReputationTier(ServerPlayer)` — the derived
  `ReputationTier` (`HONORED` … `REVILED`)
- `MercantileAPI.isSentryGolem(Entity)` — whether an entity is a pylon-spawned
  sentry golem
- `MercantileAPI.isProfessionLocked(Villager)` — whether a villager's
  profession is locked (first trade locks it)
- `MercantileAPI.isTradeLocked(Villager, MerchantOffer)` — whether a specific
  offer is locked (locked offers survive trade cycling)
- `ReputationChangedCallback` — fired server-side whenever a player's score
  changes (trades, cycling, cures, proximity, attacks, commands)
- `TradeExecutedCallback` — fired server-side when a player completes a trade
  with a villager or wandering trader

### Gradle Setup

```gradle
dependencies {
    modCompileOnly "maven.modrinth:mercantile:<version>"
}
```

### Usage Examples

**Reading Reputation:**

```java
if (FabricLoader.getInstance().isModLoaded("mercantile")) {
    int score = com.rfizzle.mercantile.api.MercantileAPI.getReputation(serverPlayer);
    var tier = com.rfizzle.mercantile.api.MercantileAPI.getReputationTier(serverPlayer);
}
```

**Listening for Reputation Changes:**

```java
if (FabricLoader.getInstance().isModLoaded("mercantile")) {
    com.rfizzle.mercantile.api.ReputationChangedCallback.EVENT.register((player, oldScore, newScore) -> {
        // react to the standing change
    });
}
```

**Listening for Completed Trades:**

```java
if (FabricLoader.getInstance().isModLoaded("mercantile")) {
    com.rfizzle.mercantile.api.TradeExecutedCallback.EVENT.register((player, villager, offer) -> {
        // villager is a Villager or WanderingTrader; fires once per executed trade
    });
}
```

**Checking Villager / Golem State:**

```java
if (FabricLoader.getInstance().isModLoaded("mercantile")) {
    boolean sentry = com.rfizzle.mercantile.api.MercantileAPI.isSentryGolem(entity);
    boolean locked = com.rfizzle.mercantile.api.MercantileAPI.isProfessionLocked(villager);
}
```

## Part of Concord

Part of [Concord](https://github.com/rfizzle/concord) — a modular collection of system overhauls.
Install any, combine all.

- [Meridian](https://meridian.rfizzle.com) — Chart your enchantments.
- [Tribulation](https://tribulation.rfizzle.com) — Survive what comes next.
- [Prosperity](https://prosperity.rfizzle.com) — Every chest, yours to discover.

## Building from source

```bash
git clone https://github.com/rfizzle/mercantile.git
cd mercantile
./gradlew build
```

The built jar lands in `build/libs/`. See [CLAUDE.md](CLAUDE.md) for the full
source layout, available Gradle tasks, and conventions.

## License

Licensed under the [MIT License](LICENSE). © 2026 rfizzle. Mercantile is not
affiliated with Mojang Studios or Microsoft.
