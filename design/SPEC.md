# Mercantile — Feature Spec

Minecraft 1.21.1 Fabric mod. Villager and trade overhaul.

**Asset philosophy:** Custom pixel-art textures for mod-specific visuals (authored through Concord's glyph pipeline — `/glyph`, `mc-textures` skill, concord `design/DESIGN-SYSTEM.md` §8 — with `.glyph` sources kept beside the masters), and vanilla assets where one is genuinely already right. Sounds stay vanilla where the cue is organic (villager voices, bells, anvil, iron-golem foley — which vanilla already nails); custom synthesized cues would be added through the `/sfx` pipeline where a sound benefits from its own identity (concord `design/DESIGN-SYSTEM.md` §9). Custom particle textures are used for mod-specific effects (pickup, trade cycling, follow mode, sentry pylon) to give each feature a distinct visual identity. Visualization features (workstation links, bell radius) use vanilla `dust` particles since they are functional overlays, not themed effects. The sentry pylon has custom block textures (top/side/bottom). Villager pickup items use **player heads** with pre-existing skin textures sourced from minecraft-heads.com and hosted permanently on Mojang's CDN.

---

## 1. Villager Pickup

Players can pick up villagers as items and place them back down.

### Interaction
- **Trigger:** Sneak + right-click with an empty main hand.
- **Result:** Villager is removed from the world and placed into the player's hand as an item.
- **Placement:** Right-click the item on a block to place the villager at the target location. The villager faces toward the player on placement.
- **Feedback:** Custom `mercantile:pickup_sparkle` particle (emerald green starburst) + sweep sound on pickup. Action bar message: "Picked up villager" (or similar).

### Scope
- Works on adult and baby villagers.
- Works on **wandering traders** too. A captured trader carries its despawn countdown frozen in the item and resumes it on placement; each leashed trader llama drops its lead and stays put rather than trailing the vanished trader. Trader pickup is guarded only against another player already trading with it — the raid and follow guards below are villager-specific.

### Data Preservation
- Full NBT is preserved: profession, profession level, XP, trade offers, gossip/reputation data, custom name, health, inventory, and any other entity data.
- Entity data stored in item's custom data component (`CompoundTag` with full entity NBT + entity type + data version tag for future migration).
- **Data version:** Integer field `MercantileDataVersion` stored alongside the entity NBT. Initial version is `1`. On deserialization, if version < current, migration functions are applied in sequence. This allows future updates to transform stored villager data without breaking existing items.

### Item Appearance

**Icon — Profession-specific player heads:**
- The item uses a **player head** (`Items.PLAYER_HEAD`) with a pre-existing skin texture per profession via the `PROFILE` data component.
- Skin textures are sourced from [minecraft-heads.com](https://minecraft-heads.com) — community-created villager head skins already hosted permanently on Mojang's CDN. No custom textures to create or maintain.
- Each profession has a Plains biome variant head. Special heads for: baby villagers, nitwits, and unemployed villagers.
- Texture mapping stored in a registry class (`VillagerHeadTextures`) that maps `ResourceLocation` (profession ID) → Base64-encoded texture value.
**Display name:**
- Format: `"{Name}"` if the villager has a custom name, otherwise `"{Profession} Villager"` (e.g. "Librarian Villager", "Baby Villager").
- Styled yellow, non-italic.

**Tooltip:**
The item lore is built in order:

1. **Instruction line** — `Right-click to place` (dark gray, non-italic).
2. **Profession & level** — e.g. `Librarian — Master` (gray). Omitted for unemployed/nitwit.
3. **Blank separator line** (only if trades exist).
4. **Trades header** — `Trades:` (gray).
5. **Trade lines** — one per `MerchantOffer`, formatted as:
   - `  {cost item} x{count} → {result item} x{count}` (gray, with dark gray arrows).
   - Enchanted books show the enchantment name and level instead of the generic item name (e.g. `Emerald x20 → Mending I`).
   - Locked trades (already purchased, relevant to trade cycling) show a lock indicator.
   - Trades that are out of stock show as ~~strikethrough~~ or dimmed.
6. **Trade count summary** — e.g. `3/5 trades unlocked` (dark gray). Only shown if trade cycling is relevant.

### Cost
- Picking up a villager costs **5 XP levels** (configurable).
- If the player does not have enough XP, the pickup is denied with an action bar message: "Not enough experience".
- **Creative mode:** XP cost is waived.

### Restrictions
- Cannot pick up a villager during an active raid in the village.
- Cannot pick up a villager that another player is currently trading with.
- Cannot pick up a villager that is currently following another player (see section 5).

### Head Texture Source

Textures sourced from [minecraft-heads.com](https://minecraft-heads.com/custom-heads/search?searchterm=villager) — Plains biome variants for each profession. These are community-created skins permanently hosted on Mojang's texture CDN (`textures.minecraft.net`).

**Texture mapping (16 profession heads):**

| Profession | minecraft-heads.com UUID |
|---|---|
| Armorer | `af40e80c-8d4c-4df4-8ac3-7f83fe43b438` |
| Butcher | `c25bcd22-83d9-46fa-92ac-145e4681d08d` |
| Cartographer | `dfa9dc67-8091-4b78-9bbb-00cb4c34cb58` |
| Cleric | `4130d8bc-7d79-498f-8ead-b9e32a86d4a6` |
| Farmer | `1d307c7a-6e3f-4862-9503-740dd9bb92d9` |
| Fisherman | `49ecebb5-0a3c-4e7a-ac18-f7b7c1c6b42b` |
| Fletcher | `85a158e9-23a3-495c-a7cf-0bb4b924bfaf` |
| Leatherworker | `641edbf6-0351-4eb8-a0f2-2ad61bfc93dc` |
| Librarian | `4df8080c-dcb8-4aee-8c1e-3e8d02aa6c4d` |
| Mason | `08d93422-d0e1-4020-a38f-b38995e05e6f` |
| Shepherd | `1e93dceb-d8d8-45c4-88e8-890a54e80b40` |
| Toolsmith | `75cf97b1-4d89-4ac8-94e1-f85fac14871a` |
| Weaponsmith | `e5d0f43c-58d7-4bf0-afc9-ed7a1ab73a8c` |
| Nitwit | `8c23af36-ab8b-4af2-9566-fa11264cc0cd` |
| Unemployed | `0a9e8efb-9191-4c81-80f5-e27ca5433156` |
| Baby | `b3867290-44bd-4ea0-9bcc-bb7734585be8` |

The Base64-encoded texture values for each UUID are hardcoded in `VillagerHeadTextures`. At runtime, a `GameProfile` is constructed with the texture property and set via `DataComponents.PROFILE` on the player head item. The client fetches and caches the skin from Mojang's CDN automatically.

**Modded profession fallback:** Uses the generic unemployed villager head for any profession not in the map.

**Wandering trader head:** Trader pickup (see the Scope note above) uses a dedicated head keyed by `minecraft:wandering_trader` rather than a profession. Its skin is the community wandering-trader head from minecraft-heads.com — texture hash `da0f1519c597e289d4f2bf5ce9643b81d7185c6a5392a77b9ab817a132f3ddbc` on Mojang's CDN — hardcoded in `VillagerHeadTextures` alongside the profession heads. The modded-profession fallback does not apply to it; it is looked up by its own key.

### Implementation Notes
- `VillagerHeadTextures` class maps `ResourceLocation` (profession ID) → Base64 texture value string. Vanilla professions hardcoded at init; modded professions fall back to the unemployed/generic head.
- A `GameProfile` is built per profession with a `"textures"` property containing the Base64 value. The profile is cached — not rebuilt per item.
- Tooltip built in `appendHoverText()` by reading the stored villager NBT to extract profession, level, name, and deserializing `MerchantOffers` for trade lines.
- Mixin or event hook on `UseEntityCallback` for the sneak + interact detection.
- Raid detection via `Raid#isActive()` on the village the villager belongs to.
- On placement, the villager is deserialized from the stored NBT and spawned facing the player. Block-break particles at spawn location.
- **NBT deserialization safety:** If the stored entity data fails to deserialize (malformed NBT, missing required fields, unknown entity type), log a warning and spawn a default villager (no profession, no trades) at the target location rather than silently consuming the item. If the `MercantileDataVersion` is higher than the current mod version supports, deny placement with an action bar message ("Villager data is from a newer version of Mercantile") and keep the item — this prevents data loss on mod downgrade. Unknown NBT keys from other mods are preserved on round-trip (vanilla's `load`/`save` handles this naturally).

---

## 2. Villager Names

All villagers receive randomly generated names drawn from biome-themed pools.

### Assignment
- Name is assigned **on spawn** (entity load, first time only).
- A persistent NBT tag (`MercantileNameAssigned`) prevents re-assignment.
- **Player-given names (nametags) always take priority.** If a villager already has a custom name set by a player via nametag, Mercantile will not overwrite it. The `MercantileNameAssigned` tag is still set to prevent future auto-assignment if the nametag name is later removed.

### Persistence
- Name survives pickup/place cycles (stored in the villager's NBT, which the pickup item preserves in full).
- Name survives restarts (standard custom name persistence).

### Visibility
- Name is **always visible** above the villager (custom name visible = true).

### Name Format
- **First name only** (no surname).
- Name pool is selected based on the **biome the villager spawns in**.
- Each biome (or biome category/tag) maps to a themed name pool. Examples:
  - Plains/forest — common English-style names
  - Desert — Arabic/Middle-Eastern inspired
  - Taiga/snowy — Nordic/Scandinavian inspired
  - Jungle — Mesoamerican inspired
  - Savanna — African inspired
  - Swamp — Old English/Celtic inspired
  - Badlands — Western/frontier inspired
- Pools are data-driven (JSON resource files under `data/mercantile/villager_names/`) so pack makers can override or extend them.
- **Target: 40–60 names per biome pool.** Enough variety to feel organic, small enough to curate quality.
- Fallback pool for biomes without a specific mapping.

### Name Pool Datapack Schema

Files located at `data/mercantile/villager_names/<biome_category>.json`:

```json
{
  "replace": false,
  "names": [
    "Aldric",
    "Berta",
    "Corwin"
  ]
}
```

- `replace`: If `true`, this pack's list replaces the built-in pool entirely. If `false` (default), names are appended to the existing pool.
- `names`: Array of strings. Duplicates are ignored.
- Biome category file names: `plains.json`, `desert.json`, `taiga.json`, `jungle.json`, `savanna.json`, `swamp.json`, `badlands.json`, `fallback.json`.
- Biome-to-category mapping is hardcoded in a registry class. Modded biomes fall through to `fallback.json`.

---

## 3. Trade Cycling

A button in the villager trade GUI that re-rolls the villager's available trades.

### Cost
- **6 emeralds** per cycle (configurable).
- Cost is **flat** — does not scale with villager level or number of cycles performed. Flat cost keeps the mechanic simple and predictable. Scaling adds complexity without clear balance benefit; the natural lock-out of purchased trades already limits cycling value over time.
- **Creative mode:** Emerald cost is waived.

### Behavior
- Pressing the cycle button **re-rolls all unlocked trades** for that villager.
- A trade becomes **locked** once a player has purchased from it at least once. Locked trades are never re-rolled.
- Once every trade slot is locked, the cycle button is disabled/hidden.
- Re-rolling generates new trades from the villager's profession trade pool at the appropriate level, same as if the villager had just leveled up.
- Trade use counters on re-rolled (unlocked) trades reset since they are new offers.

### Cooldown
- No cooldown — the emerald cost is the balancing mechanism.

### Feedback
- **Sound:** Villager "yes" trade sound on successful cycle.
- **Visual:** Custom `mercantile:cycle_glint` particle burst around the villager (gold diamond flash).

### Configuration
- The entire trade cycling feature can be **disabled via ModMenu** config toggle.
- Emerald cost is configurable.

### UI
- Button added to the vanilla merchant trade screen.
- Client sends a C2S packet when clicked; server validates cost, performs the re-roll, and syncs the updated offers back.
- Button is grayed out / hidden when:
  - All trades are locked.
  - Player lacks emeralds.
  - Feature is disabled in config.

### Implementation Notes
- Mixin into the merchant screen to add the cycle button (client side).
- Mixin or accessor into `Villager` to regenerate offers from the profession's trade pool.
- Network packet: `CycleTradesC2S` containing the villager entity ID.
- Server handler validates: villager exists, player is trading with it, has emeralds, has unlockable trades.
- "Locked" state tracked via a **parallel map on the villager entity** (not on `MerchantOffer` itself, since offers have no extensible NBT). The map is keyed by a **stable offer identity hash** derived from the offer's input items and output item type (not count/price, since those can change). The map is stored as a `ListTag` of `CompoundTag` entries in the villager's persistent data via Fabric's Attachment API. On trade completion, the current offer's identity hash is added to the locked set. On cycle, any offer whose hash is in the locked set is skipped during re-roll.

---

## 4. Reputation System

A **global** reputation score per player that affects interactions with **all** villagers.

This is distinct from vanilla's per-villager gossip system. Mercantile reputation is a single number representing how villagers as a whole perceive the player.

### Scope
- **Per-player, global** — one reputation value per player, applied uniformly to all villager interactions.
- Persisted in player-attached data (survives death, dimension changes, etc.).

### Actions That Affect Reputation

| Action | Change | Notes |
|---|---|---|
| Completing a trade | +1 | Per trade execution (bulk trades count each execution individually) |
| Curing a zombie villager | +5 | One-time bonus per villager (tracked by villager UUID). Bypasses the daily cap |
| Attacking a villager | -15 | Per hit |
| Killing a villager | -40 | Stacks with attack penalty from the killing blow |
| Proximity to villagers | +1 per 10 min | Passive accrual while within 16 blocks of any villager. Tracked via an internal tick counter (12,000 ticks = 10 min). Capped at +1 per in-game day |
| Successful trade cycling | +1 | Per cycle |
| Gifting a profession item | +1 | Toss a profession-appropriate item to a villager. Daily-capped (see Gifting below) |
| Defending a village from a raid | +10 | Granted to each player vanilla awards Hero of the Village. Bypasses the daily cap (see below) |

All values are configurable via the reputation config subsection.

**Raid-defense rep is an intentional cap bypass.** Like the cure bonus, winning a raid skips the daily total cap and the per-source sub-caps, and does not count toward the day's earned total — defending a village is a rare, heroic act rewarded in full. It is gated behind both `enableReputation` and `enableRaidReputation`, and triggers off vanilla's Hero of the Village award, so any player vanilla credits for the raid is credited here too.

### Vanilla Gossip Interaction
Mercantile reputation operates as a **parallel system** alongside vanilla gossip. Both independently contribute to the final trade price:
- Vanilla gossip adjusts prices per-villager as normal (curing, hero of the village, etc.).
- Mercantile reputation applies a global modifier on top.
- The two stack additively — a player with high gossip AND high reputation gets both discounts.
- This preserves all vanilla mechanics while adding the new global layer.

### Reputation Storage
- Reputation value is an **integer** (fractional accrual is handled by internal tick counters, not fractional reputation values).
- Stored via **Fabric's Attachment API** (`AttachmentType<CompoundTag>` registered on `ServerPlayerEntity`). Attachment contains: `Score` (int), `ProximityTicks` (int, counter toward next proximity award), `CuredVillagers` (UUID set, prevents duplicate cure bonuses).
- Attachment persists across death, dimension changes, and server restarts automatically.
- The same attachment mechanism is used for all per-player persistent data in Mercantile (reputation, stats).


### Effects of Reputation

**Price Discounts:**
- High reputation → progressive discount on trade prices at tier thresholds.
- Negative reputation → price markup.
- Applied as a percentage modifier to the emerald cost component of trades.

**Exclusive Trades:**
- At high reputation tiers, villagers offer additional trades not normally in their pool.
- Implementation approach: define a set of **bonus offers** per profession that are injected into the villager's trade list when the interacting player meets the reputation threshold.
- These bonus trades are defined in data packs (`data/mercantile/exclusive_trades/<profession>.json`) so they are extensible.
- **Per-profession primarily**, plus a small set of **cross-profession "mercantile" trades** available from any villager at Honored tier. Cross-profession trades are defined in `data/mercantile/exclusive_trades/_mercantile.json`.
- **Wandering traders** also gain a bonus offer at Trusted tier and above, defined in `data/mercantile/exclusive_trades/wandering_trader.json`. Unlike villagers (which can accumulate several exclusive offers), each wandering trader receives exactly **one** bonus offer: the first time a qualifying player opens it, one offer is chosen from the qualifying pool and persisted to the trader's data, so it stays stable across re-opens even if the player's score later drifts. Gated behind `enableWanderingTraderRep`.
- Bonus trades are **player-specific** — they appear for the high-rep player but not for others interacting with the same villager. This requires intercepting the trade list sent to the client and injecting/removing offers based on the player's reputation.

**Trade Refusal:**
- At very low (negative) reputation, villagers may refuse to trade entirely.
- Visual cue: `angry_villager` particles (vanilla thundercloud), head shake animation.

### Reputation Tiers

| Tier | Range | Effect |
|---|---|---|
| Reviled | -200 to -150 | Trade refusal, `angry_villager` particles |
| Distrusted | -149 to -1 | Price markup (+10-25%, scaled linearly within range) |
| Neutral | 0 to 74 | No modifier |
| Liked | 75 to 299 | Small discount (5%) |
| Trusted | 300 to 999 | Moderate discount (10%), profession-specific exclusive trades |
| Honored | 1000+ | Best discount (15%), all exclusive trades including cross-profession |

### Reputation Bounds and Decay
- **Hard cap:** -200 minimum, +1500 maximum. The buffer above the Honored threshold (1000) allows continued earning without wasted actions, while the cap prevents runaway values.
- **Positive reputation does not decay.** Reputation earned through deliberate action is never lost to absence — decay would punish players for exploring or working on other projects.
- **Negative reputation recovers.** Scores below 0 climb back toward 0 at `reputationNegativeDecayPerDay` points per in-game day (default 1), evaluated at day rollover. This gives a passive redemption path that complements active gifting; positive scores are never touched.
- **Player death:** Full reputation is retained. Reputation represents a long-term relationship, not a resource.

### Exclusive Trades Datapack Schema

Files at `data/mercantile/exclusive_trades/<profession>.json`, `data/mercantile/exclusive_trades/_mercantile.json` (cross-profession), and `data/mercantile/exclusive_trades/wandering_trader.json` (wandering-trader bonus offers) — all share the same schema:

```json
{
  "replace": false,
  "min_tier": "trusted",
  "trades": [
    {
      "input_1": { "item": "minecraft:emerald", "count": 64 },
      "input_2": { "item": "minecraft:diamond", "count": 4 },
      "output": {
        "item": "minecraft:diamond_sword",
        "count": 1,
        "components": {
          "enchantments": [
            { "id": "minecraft:sharpness", "level": 5 },
            { "id": "minecraft:unbreaking", "level": 3 }
          ]
        }
      },
      "max_uses": 1,
      "min_tier_override": "honored"
    }
  ]
}
```

- `replace`: If `true`, replaces the built-in exclusive trades for this profession.
- `min_tier`: Default minimum reputation tier for all trades in this file (defaults to `trusted`).
- `min_tier_override`: Per-trade override of the minimum tier.
- `max_uses` (optional, default 12), `xp_gain` (optional, default 1), and `price_multiplier` (optional, default 0.05) tune each trade's stock, villager XP reward, and demand-based price scaling.
- `components` (optional, **output only**): enchants the reward. `enchantments` applies to gear; `stored_enchantments` writes book enchantments onto an `enchanted_book`. Each is an array of `{ "id": "<enchantment>", "level": <int> }` (level defaults to 1). IDs are resolved against the live enchantment registry when the offer is built, so unknown IDs are skipped with a warning rather than failing the pack. Cost items (`input_1`/`input_2`) are plain item + count and take no components.
- `enchant_randomly` + `level` (optional, **output only**): a *generative* enchant-book reward that draws one enchantment at random instead of enumerating a fixed one. `enchant_randomly` is an enchantment tag id (a leading `#` is accepted and stripped, e.g. `#meridian:rarity/common`); at offer-build time the tag is resolved against the live enchantment registry and one enchantment is picked uniformly and stored on the book. `level` is a policy relative to the picked enchantment's own `[min, max]` level range — `mid` (the default; the rounded-up midpoint, floored at min) or `max`. The draw happens once and the rolled offer is **persisted on the villager** (keyed by the trade template), so the enchantment stays fixed across trade-screen re-opens rather than re-rolling — this prevents free re-roll cherry-picking and keeps the offer's identity stable for the buy-lock, pin, and lock-eviction systems. `enchant_randomly` is **mutually exclusive** with a fixed `enchantments`/`stored_enchantments` component — a trade declaring both is skipped with a warning. An unresolved or empty tag warns and leaves a plain book rather than failing the pack.
- `requires_advancement` (optional): a single advancement id (e.g. `minecraft:end/kill_dragon`) the buyer must have completed for the trade to be offered. This gates on player progression *in addition to* the reputation tier. The check needs the opening player, so an advancement-gated trade is never surfaced through the wandering-trader path (which has no player context). An unknown advancement id withholds the trade.
- `fabric:load_conditions` (optional): a [Fabric resource-condition](https://docs.fabricmc.net/develop/data-generation/conditions) array, honored at **both** the file root and the individual trade-entry scope. A trade pack uses this to gate entries on another mod being present — e.g. shipping a sibling-mod trade pack in-jar that only loads when that mod is installed. A failing condition skips the gated entries (or the whole file) silently at load, with no warning. Conditions are re-evaluated on every resource reload (`/reload`, world load). Only **mod-presence** conditions (`fabric:all_mods_loaded`, `fabric:any_mods_loaded`, `fabric:not`, `fabric:and`, `fabric:or`) are evaluated here; registry-dependent conditions (`fabric:registry_contains`, `fabric:tags_populated`) are out of scope because the loader runs without a live registry.

```json
{
  "fabric:load_conditions": [
    { "condition": "fabric:all_mods_loaded", "values": ["meridian"] }
  ],
  "trades": [
    {
      "fabric:load_conditions": [
        { "condition": "fabric:all_mods_loaded", "values": ["tribulation"] }
      ],
      "input_1": { "item": "minecraft:emerald", "count": 16 },
      "output": { "item": "minecraft:book", "count": 1 }
    }
  ]
}
```

### Gifting

An active reputation-gain path: drop a profession-appropriate item near a villager and it walks over to pick it up, awarding the player who tossed it `reputationGiftGain` reputation (default +1) and emitting happy villager particles.

- **Profession-matched.** A villager only accepts (and only rewards) items mapped to its current profession — a Farmer takes crops, an Armorer takes iron, etc. Items not in the villager's mapping are ignored and trigger no pickup.
- **Daily-capped.** Gift reputation is bounded per in-game day by `reputationDailyMaxGiftRep` (default 2) and also counts against the shared `reputationDailyCap`, so gifting cannot bypass the overall daily ceiling.
- **Attribution.** The reward goes to the player the dropped item is targeted at (vanilla item-throw ownership), so only the tosser gains reputation.
- **Data-driven.** Item-to-profession mappings live in datapacks (see Gift Mappings Datapack Schema below) so packs can override or extend them. Defaults ship for armorer, cleric, farmer, librarian, toolsmith, and weaponsmith.
- **Toggle:** `enableGifting` (default true). Gated behind `enableReputation`.

Gifting and passive negative-rep decay together form the **redemption path**: a Reviled player can climb back to Neutral by gifting villagers and/or simply waiting, without grinding trades that may be refused at low tiers.

### Gift Mappings Datapack Schema

Files at `data/mercantile/gift_mappings/<profession>.json`, where `<profession>` is the villager profession path (e.g. `farmer`, `armorer`, `librarian`):

```json
{
  "replace": false,
  "items": [
    "minecraft:wheat",
    "minecraft:potato",
    "minecraft:carrot"
  ]
}
```

- `items`: Item IDs this profession accepts as gifts. Unknown item IDs are skipped with a warning.
- `replace` (optional, default `false`): If `true`, clears any items already mapped to this profession from earlier packs before applying this file's `items`.
- Without `replace`, multiple packs targeting the same profession merge their `items` lists additively.
- Mappings reload with the datapack reload lifecycle (`/reload`), like recipes and loot tables.

### Persistence
- Stored via Fabric's Attachment API (see Reputation Storage above).
- Survives death (reputation is earned, not ephemeral).

### Implementation Notes
- Reputation stored via Fabric Attachment API (`AttachmentRegistry.createPersistent()`) — see Reputation Storage above for schema.
- Hook into `MerchantScreen` open / trade list sync to inject exclusive offers and apply price modifiers on the server before sending to client.
- Events: `UseEntityCallback` (attack detection), trade completion callback, zombie villager conversion event, entity tick (proximity).

---

## 5. Villager Follow Mode

Villagers can be commanded to follow a player, replacing the need for minecarts/boats for transport.

### Interaction
- **Trigger:** Sneak + right-click a villager while holding an **emerald**.
- Emerald is the trigger item because bell is already overloaded (workstation visualization, bell radius). Emerald is thematic — the player is "paying" for the villager's attention.
- **Toggle:** Same action again (sneak + right-click with an emerald) to release the villager from follow mode. Only the player the villager is following can release it.
- Villager follows at a moderate pace, keeping within ~6 blocks of the player.

### Behavior
- Following villager pathfinds toward the player using standard mob AI goals (injected at high priority).
- If the player moves too far (>32 blocks), the villager gives up and stops following (prevents chunk-loading exploits).
- Following villagers ignore their normal schedule (won't wander to workstations or beds until released).
- **Return home on release.** When released, a villager that remembers a bed or workstation walks back to it (bed takes priority over workstation) at a steady pace, stopping once it arrives within ~4 blocks, gets stuck, or finishes pathing. A villager with neither memory simply resumes normal behavior in place. Taking damage cancels the return so it can defend itself or flee. Toggled by `enableSendHome` (independent of `enableFollowMode`).
- **Following villagers are immune to entity collision pushing** (mob-on-mob pushing only — block collision and piston interactions are unaffected). Without this, other entities constantly shove them off their path, making the feature frustrating.
- Visual indicator: Custom `mercantile:follow_trail` particles emitted periodically at the villager's feet while following (teal/cyan ground glow — distinct from trade cycling's gold flash and pickup's green starburst).

### Limits
- A player can have at most **3 villagers** following at once (configurable).
- A villager can only follow **one player** at a time. If a second player attempts to trigger follow on an already-following villager, the action is denied with an action bar message.
- Following state is **not persisted** across logout/restart — villagers revert to normal behavior.

### Implementation Notes
- Custom AI goal (`FollowPlayerGoal`) injected via mixin on `Villager#registerGoals()`.
- Follow state tracked in villager's transient data (not NBT — intentionally ephemeral).
- Particle/visual indicator rendered client-side based on a sync'd entity data accessor.

---

## 6. Pathfinding Improvements

Mixin-level fixes for common vanilla villager pathfinding frustrations.

### Fixes
- **Door navigation:** Villagers properly path through open fence gates and double doors without getting stuck.
- **Stair/slab traversal:** Full staircase navigation — villagers traverse multi-step staircases and slab transitions without freezing. Not limited to single-step slabs.
- **Ladder climbing:** Villagers can climb ladders when the path includes them (vanilla villagers avoid ladders entirely). Enabled for **all villagers**, not just those in follow mode — this is a pathfinding fix, not a follow-mode perk.
- **Water avoidance:** Villagers path around water rather than walking into it and drowning.

### Implementation Notes
- Mixins into `GroundPathNavigation` and/or `WalkNodeEvaluator` to adjust pathfinding node costs and traversal logic.
- Each fix is individually toggleable in config so players can opt into only what they want.

---

## 7. Breeding Information (Jade/WTHIT Integration)

Villager breeding state is surfaced through tooltip overlays via Jade and WTHIT.

### Tooltip Information
When looking at a villager with Jade or WTHIT installed, the tooltip shows:

- **Willingness:** "Willing to breed" / "Not willing" with reason (needs food, no bed, cooldown).
- **Breeding cooldown:** Time remaining until the villager can breed again (e.g. "Can breed in: 3:42").
- **Baby growth:** For baby villagers, time remaining until adulthood (e.g. "Grows up in: 12:30").
- **Food inventory:** Exact item counts — e.g. "Food: 3 Bread, 12 Carrots (needs 3 more Bread to breed)". Players optimizing breeding farms need precision, not vague labels.

### Implementation Notes
- Jade plugin: `IWailaPlugin` registering a `IComponentProvider` for `Villager.class`.
- WTHIT plugin: parallel implementation via `waila_plugins.json`.
- Server-side data provider sends breeding state, cooldown ticks, food counts, and baby age via NBT in the tooltip data.
- Both plugins are optional dependencies — feature is simply absent if neither is installed.

---

## 8. Bulk Trading

Shift-click in the trade GUI to execute a trade as many times as possible in one action.

### Behavior
- **Shift-click** the trade output slot to repeat the trade until:
  - The player runs out of input items, OR
  - The trade runs out of stock, OR
  - The player's inventory is full, OR
  - **64 trades** have been executed (hard cap per bulk action — prevents accidental massive trades and limits server-side processing per click).
- Works for both buying and selling directions.
- All trades execute at the same price (no dynamic repricing mid-bulk).

### UI
- Vanilla shift-click behavior is overridden in the merchant screen.
- A count indicator shows how many trades were executed (brief toast or chat message).

### Implementation Notes
- Mixin into `MerchantMenu#quickMoveStack` to loop the trade logic.
- Must respect trade stock limits (`MerchantOffer#getMaxUses()`).
- Must correctly update demand counters for each execution.

---

## 9. Trade Restock Indicator

Show when a villager's trades will next restock in the trade GUI.

### Display
- Timer shown in the trade GUI near the trade list, displayed as a **game-tick-based estimate converted to minutes:seconds** (e.g. "Restocks in: ~5:23"). Prefixed with "~" since day cycle mods can affect actual timing.
- **Restock count:** Shows restocks remaining today — e.g. "Restocks: 1/2 today".
- If the villager has restocked recently and is at max stock, show "Fully stocked".
- If the villager has no workstation (can't restock), show "No workstation" in red.

### Behavior
- Vanilla villagers restock up to 2x per day when they reach their workstation.
- Timer is an estimate based on remaining day ticks and the villager's workstation access.

### Implementation Notes
- Mixin into the merchant screen to render the restock info.
- Server sends last-restock tick, restock count today, and workstation binding status via a custom payload when the trade screen opens.
- Client calculates the estimated time from day cycle.

---

## 10. Demand Pricing Transparency

Show players why a trade's price is what it is.

### Display
- Hovering over a trade's price in the GUI shows a tooltip breakdown:
  - Base price
  - Demand adjustment (+/- emeralds, from how frequently the trade is used server-wide)
  - Reputation modifier (from mercantile reputation system)
  - Gossip modifier (from vanilla per-villager gossip)
  - Final price
- Color-coded: green for discounts, red for markups.

### Implementation Notes
- Mixin into merchant screen tooltip rendering.
- Server sends the price components (not just the final value) in a custom payload alongside the trade list.
- Requires accessor into `MerchantOffer` demand field and the villager's gossip container.

---

## 11. Workstation Link Visualization

Visual feedback showing which villager is bound to which workstation.

### Trigger
- **While holding a bell:** Particle lines render between each villager and their claimed workstation within render distance.
- Alternatively, right-clicking a workstation block (while sneaking?) highlights its bound villager, and vice versa.

### Visual
All visuals use vanilla particles — no custom textures.

- **Bound links:** `dust` particles (vanilla `DustParticleOptions`) in profession-colored lines from villager to workstation. Color derived from the profession's workstation block (e.g. brown for lectern/librarian, gray for smithing table). Particles are spawned at intervals along the line between the two positions.
- **Unbound villagers** (no workstation): `angry_villager` particle above the villager's head (pulsing).
- **Unbound workstations** (not claimed): `dust` particles in yellow orbiting the block.

### Render Limits
- **Range:** 64 blocks (4 chunks) from the player. Beyond this, lines are not rendered — reduces clutter and GPU cost.
- **Performance:** Particle-based lines with LOD — reduce particle density for links farther from the player (fewer `dust` particles per line). Off-screen links are culled entirely (frustum check).

### Implementation Notes
- Client-side rendering only — server sends a mapping of villager UUID → workstation BlockPos on request.
- Network packet: `WorkstationMapS2C` sent when the player holds a bell or requests the debug view.
- Mixin or accessor into `VillagerAi` / `PoiManager` to read the brain memory for job site.
- Rendered via vanilla `ParticleEngine` using `DustParticleOptions` (configurable color + size). No custom render layer needed.

---

## 12. Profession Lock Protection

Prevent villagers from accidentally losing their profession after trading has occurred.

### Behavior
- Once a player has made **any trade** with a villager, that villager's profession is **locked**.
- A locked villager will not lose their profession if their workstation is broken or moved.
- The villager will still pathfind to a new workstation of the same type if available, but will never revert to unemployed.
- If the workstation is permanently gone, the villager simply has no workstation (and can't restock) but retains profession and trades.

### Visual Indicator
- Locked villagers show a lock symbol (`🔒` / Unicode padlock, or the vanilla lock icon from the difficulty lock button) in the trade GUI header near the profession name. No custom texture — reuses existing vanilla GUI sprites.
- Jade/WTHIT tooltip shows "Profession: Locked" vs "Profession: Unlocked".

### Implementation Notes
- Mixin into the villager AI that handles profession loss (`VillagerMixin` targeting the method that resets profession when no workstation is found).
- "Locked" flag stored in villager's persistent data via Fabric Attachment API once first trade is completed.
- Vanilla already partially does this for villagers at journeyman+ level — this extends it to any villager with at least one completed trade.

---

## 13. Villager Healing Enhancement

Splash and lingering potions of healing are more effective on villagers.

### Behavior
- **Splash and lingering potions only.** Tipped arrows, direct drinking (N/A for villagers), and other sources are not affected. Thematically, the enhanced healing represents villagers responding well to care — shooting them with tipped arrows is not "care."
- Splash/lingering potions of healing restore **double** the normal amount to villagers (configurable multiplier).
- Splash potions of regeneration also apply double duration to villagers.
- **Golden apples are not affected.** They already have strong effects and villagers don't normally consume them.
- This makes it practical to heal up villagers after zombie sieges or accidental damage.

### Implementation Notes
- Mixin into the potion application logic (`LivingEntity#addEffect()` or `AreaEffectCloud` application) to intercept healing/regen effects when the target is a `Villager`.
- Multiplier is configurable (default 2.0x).

---

## 14. Villager Info Panel

Extended information panel in the trade GUI showing detailed villager stats.

### Display
Shown alongside the trade list in the merchant screen:

- **Name** (from the naming system)
- **Profession** and **level** (Novice → Master) with XP bar to next level
- **Reputation standing** with the current player (tier name and numeric value)
- **Total trades completed** with this player
- **Workstation status** (bound / unbound / missing)
- **Profession locked** indicator

### Implementation Notes
- Mixin into `MerchantScreen` to add a panel (left side or top area).
- Server sends villager metadata via a custom payload when the screen opens: profession XP, total trades stat, workstation status, lock state.
- Client renders the panel using the data.

---

## 15. Villager State Indicators (Jade/WTHIT)

Visual indicators for villager needs and state, surfaced via Jade/WTHIT tooltips.

### States Shown

| State | Display | Condition |
|---|---|---|
| Looking for bed | Bed icon + "Needs bed" | Has no claimed bed |
| Looking for workstation | Workbench icon + "Needs workstation" | Has no claimed POI |
| Hungry | Food icon + "Hungry" | Food inventory below breeding threshold |
| Wants to breed | Heart icon + "Willing to breed" | Has enough food + available bed |
| Panicking | Warning icon + "Panicking" | Fleeing from a threat |
| Trading | Emerald icon + "Trading with [player]" | Currently in a trade screen |

### Implementation Notes
- Extends the Jade/WTHIT plugin from section 7 (breeding info).
- Server-side data provider reads villager brain memories (`MemoryModuleType`) for bed/workstation claims, current activity, and food count.
- Icons are vanilla item sprites rendered inline in the tooltip via Jade/WTHIT's built-in item icon support (e.g. bed icon = `Items.RED_BED`, food icon = `Items.BREAD`). No custom icon textures.

---

## 16. Villager Sound Volume

Configurable volume for villager ambient, trade, and hurt sounds.

### Behavior
- A volume slider (0%–100%) in the ModMenu config screen controls villager sound volume.
- Applies to all villager sound events: ambient, hurt, death, trade, yes/no.
- Default: 100% (vanilla behavior).
- Setting to 0% fully mutes villager sounds.

### Implementation Notes
- Mixin into the sound engine or `Villager#playSound()` to multiply the volume by the configured factor.
- Client-side only — each player controls their own volume.
- Stored in client config (separate from server config).

---

## 17. Bell Radius Visualization

When holding or ringing a bell, show the area of effect.

### Behavior
- **While holding a bell item:** Renders the 48-block gathering radius (the vanilla `BellBlockEntity` search radius) as circles. **Holding the bell IS the opt-in** — no additional keybind needed.
  - **Placed bells (actual coverage):** a **gold** circle is drawn around every placed bell within render distance, each centered on its bell block. The 48-block radius is measured from the bell in vanilla, so the visualization is bell-centered too; a village with two bells shows two circles, and their overlap is real coverage information. Bells are discovered client-side (bells are block entities) so no networking is added.
  - **Player-centered preview (hypothetical coverage):** a **dim white** circle is drawn around the player as a placement-scouting tool — the coverage a bell placed where you stand *would* have. Its distinct color keeps a hypothetical from being read as a real bell's coverage.
- **On bell ring (placed bell):** Brief particle burst at the radius boundary centered on the bell block, with villagers inside the radius briefly highlighted (glow effect or particles).

### Rendering Approach
- Render each ring as a **circle on the ground plane** (Y = bell height, or player height for the preview) rather than a full sphere. A flat circle is much cheaper to render and still clearly communicates the radius. Vertical range is implicit — players don't need to see a sphere to understand the area.
- Circle rendered with `dust` particles (`DustParticleOptions`) spawned at evenly spaced points around the circumference — gold for placed bells, dim white for the player preview. No custom shader or texture needed. Emissions are forced so they clear vanilla's 32-block particle cull at the 48-block radius.

### Implementation Notes
- Client-side rendering, driven from the client tick.
- Placed-bell discovery is a **budgeted client-side chunk sweep**: each tick scans a bounded slice of loaded chunks around the player (`ClientChunkCache.getChunkNow` → `LevelChunk.getBlockEntities()`, filtering `BellBlockEntity`) and promotes a full render-distance pass's finds wholesale, so a newly placed or broken bell is reflected within a few seconds. No per-tick full scan, no new networking. The nearest circles are drawn under a per-tick particle budget.
- Bell ring detection via mixin on `BellBlock#onHit()` or a block entity tick observer.
- Glow effect on villagers via the vanilla **entity glow render flag** (`setGlowingTag(true)`, short duration, client-side only). This is the same outline used by spectral arrows — no custom rendering.

---

## 18. Sentry Pylon

A placeable block that passively defends an area by summoning temporary iron golems when hostile mobs are nearby.

### Block: Sentry Pylon (`mercantile:sentry_pylon`)

A crafted block placed in a village (or anywhere). Visually distinct — iron-and-stone pillar with custom particle effects to communicate state.

**Block model:** Custom block model + texture. One block model with **three blockstate variants** differentiated by custom particle effects:
- `idle` — Base model. Custom `mercantile:pylon_mote` particles (light gray, 4x4) drifting upward.
- `active` — Same model with custom `mercantile:pylon_spark` particles (orange-gold, 6x6, jagged) around the top.
- `empty` — Same model, no particles. Visually "dormant."

**Texture assets:** Cold Steel palette (`#8090A0` steel blue, `#606870` dark steel, `#B0C0D0` light steel, `#A0A8B0` pale gray, `#384048` charcoal, `#505860` gunmetal). Three 16x16 block face textures: `sentry_pylon_top.png` (concentric iron rings), `sentry_pylon_side.png` (iron bands with carved stone brick), `sentry_pylon_bottom.png` (heavy stone base with bolt accents). The model itself is JSON-defined (standard Minecraft block model format), not a custom OBJ/mesh.

### Fueling
- The pylon consumes **iron blocks** as fuel to summon golems.
- Iron blocks are inserted by right-clicking the pylon while holding them (no GUI — direct insertion).
- The pylon stores up to **8 iron blocks** internally (configurable).
- Each golem spawn costs **1 iron block** from the pylon's reserve.
- Jade/WTHIT tooltip shows current fuel level (e.g. "Iron: 3/8").
- **Creative mode:** Fuel is not consumed (infinite spawning).

### Detection
- The pylon scans for hostile mobs within a **32-block radius** (configurable).
- Scan runs every ~2 seconds (40 ticks) at the default radius to avoid per-tick cost. The interval scales up with the detection radius — a scan sweeps a volume that grows with the cube of the radius, so a wider-radius pylon scans proportionally less often (linear in radius above the 32-block reference, e.g. ~8 seconds at the 128-block maximum). Each pylon's scan is also phase-offset by its position, so a cluster of pylons spreads its scans across ticks rather than all firing on the same one.
- "Hostile" = any mob targeting a player or villager within range, or any undead/illager entity.
- **Line of sight required:** a hostile only counts as detected when the pylon has an unobstructed line to it (block raycast from the mob to the pylon, testing the mob's eyes and feet — a clear path from either point registers). A mob sealed in a cave below or walled off in a separate room is ignored even when it sits inside the radius, so it never rings the bell endlessly or holds a sentry in place against a threat it cannot reach. Every reaction the pylon makes — spawning a golem, the bell alarm, and the despawn countdown — keys off this same in-sight detection.

### Spawning
- When a hostile mob is detected and fuel is available, the pylon spawns an iron golem at a valid position near the threat (within the detection radius, on solid ground).
- **Line of sight required:** the spawn position must have an unobstructed line to the pylon (block raycast from the candidate to the pylon). This keeps sentries from materializing underground or behind walls when the threat is in a sealed cave or a separate room — the pylon only conjures a golem where it can "see" the spot. If no in-sight position is found near the threat, no golem spawns that cycle.
- Spawned golems are tagged as **sentries** (`MercantileSentry` NBT tag) to distinguish them from natural/player-built golems.
- The pylon spawns at most **1 golem per detection cycle**, even if multiple hostiles are present (prevents mass-spawning).
- Maximum **3 active sentry golems** per pylon at any time (configurable). Additional spawns are blocked until an existing sentry despawns.
- **Sentry golems always spawn at full health.** They are temporary summons, not persistent entities — healing between fights is not applicable.

### Sentry Golem Behavior
- Sentry golems target and attack hostile mobs, including **creepers** — which vanilla iron golems pointedly ignore. A creeper that is fighting a sentry is prevented from priming, so the golem can dispatch it without an explosion levelling what the pylon defends.
- Their aggro range matches the pylon's detection radius (they don't wander beyond it).
- Sentry golems have a **leash range** to their parent pylon — if pushed or pathing beyond the detection radius, they return.
- Sentry golems do **not** drop iron ingots or poppies on death.
- Sentry golems do **not** count toward iron farm mechanics or mob caps. Implemented by tagging sentry golems with `MercantileSentry` and mixin into the mob spawning cap counter to exclude entities with this tag. This is a targeted check (only affects `IronGolem` entity type counting), not a broad mob cap override.

### Despawning
- Once no in-sight hostile remains within the pylon's radius — every threat dead, gone, or sealed out of line of sight — sentry golems begin a **despawn countdown** (default 30 seconds / 600 ticks, configurable).
- If an in-sight threat appears during the countdown, the timer resets and the golem re-engages.
r- Despawn is visual: the golem slowly fades/cracks (reuse iron golem damage texture stages) over the last few seconds, then disappears with iron particles and the iron golem damage sound. See Sound Design section for all sound mappings.
- If a sentry golem is killed in combat, no despawn sequence — it simply dies (no drops).

### Crafting

Shaped recipe (3x3 crafting grid):

```
[ ]  [Carved Pumpkin]  [ ]
[Iron Block]  [Bell]  [Iron Block]
[Stone Bricks]  [Iron Block]  [Stone Bricks]
```

**Materials:** 3 iron blocks + 1 bell + 1 carved pumpkin + 2 stone bricks.
**Cost rationale:** 3 iron blocks (27 ingots) + a bell (village find or gold craft) + carved pumpkin (golem callback) puts this solidly in mid-to-late game. The bell is the gating material for players without village access.

### Pylon Durability
- **Blast resistance: 6.0** (same as obsidian). The pylon's purpose is village defense — it should survive the threats it's designed to counter, including creeper explosions and raid attacks.
- Breakable with a pickaxe (iron+ tier). Drops itself when mined. Does not drop stored fuel — fuel is consumed.

### Multiple Pylons
- **Overlapping radii are allowed.** Each pylon independently tracks its own sentry golems. The per-pylon golem cap (default 3) prevents runaway spawning. Two overlapping pylons can have up to 6 total sentries active — this is intentional and rewards investment.

### Out-of-Fuel Alert
- When the pylon detects a hostile but has no fuel: `dust` particles in red pulse from the pylon and a **note block bass drum sound** (`minecraft:block.note_block.basedrum`) plays at low pitch (once per detection cycle, not continuously). Alerts nearby players to refuel.

### Bell Alarm
- On detecting a hostile, the pylon rings the **nearest village bell** within its detection radius using the standard vanilla bell ring (sound + swing), drawing players to the threat. Located via the village point-of-interest system; if no bell is in range, nothing happens.
- **Extended ring range:** because the villager-glow broadcast accompanying *any* bell ring reaches 96 blocks while the vanilla bell only carries ~16–32, the ring's own sound is amplified to cover the full 96-block radius (volume 6.0 on the variable-range `minecraft:block.bell.use` event ⇒ 16 × 6 = 96 blocks). This is a property of the bell ring itself, not the pylon — every bell ring (player-, pylon-, or otherwise-triggered) carries this far, so a distant player hears the ring and isn't left with silently glowing villagers. Implemented in `BellBlockMixin`; gated on `enableBellRadiusVis` (the same toggle as the glow visualization), so disabling the glow restores the vanilla ~32-block reach.
- The bell ring fires on threat detection regardless of whether a golem is spawned, and is **rate-limited to once per 10 seconds** (200-tick cooldown, persisted across save/load) so a sustained threat does not ring continuously. Detection requires line of sight (see Detection), so a mob sealed in a cave or behind a wall never rings the bell at all.
- Toggled by `enablePylonBellAlarm` (requires the pylon itself to be enabled via `enableSentryPylon`).

### Redstone Interaction
- A redstone signal **disables** the pylon (stops scanning and spawning). Allows players to toggle defense on/off.
- Comparator output reflects fuel level (0–15 signal strength proportional to iron blocks stored).

### Implementation Notes
- Custom block + block entity (`SentryPylonBlock` / `SentryPylonBlockEntity`).
- Block entity handles: fuel storage (simple `int` count), scan tick, golem tracking (list of sentry golem UUIDs), spawn logic.
- Sentry golems are vanilla `IronGolem` entities with an injected NBT tag and added AI goals: `SentryTargetHostilesGoal` (creeper-inclusive target acquisition, gated on the sentry tag) and `ReturnToPylonGoal` (leash back inside the detection radius). A `Creeper` mixin suppresses swell while the creeper's target is a sentry.
- Mixin or subclass to prevent sentry golems from dropping loot.
- Despawn logic runs on the block entity tick — checks tracked golem UUIDs, if no hostiles in range, starts countdown per golem.
- Golem spawn position: find valid spawn pos within detection radius near the closest hostile, using `SpawnPlacements` logic.

---

## 19. Commands

### `/mercantile` Command Tree

All commands use the `mercantile` root. Requires **operator level 2** for admin subcommands.

| Command | Permission | Description |
|---|---|---|
| `/mercantile reputation` | Any player | Shows your own reputation score and tier |
| `/mercantile reputation <player>` | Op level 2 | Shows another player's reputation |
| `/mercantile reputation set <player> <value>` | Op level 2 | Sets a player's reputation to an exact value |
| `/mercantile reputation add <player> <value>` | Op level 2 | Adds (or subtracts, if negative) reputation |
| `/mercantile pins` | Any player | Lists your pinned trades (index, villager, summary, live stock status) |
| `/mercantile pins remove <index>` | Any player | Removes the pin at the given 1-based index |
| `/mercantile pins clear` | Any player | Removes all of your pinned trades |
| `/mercantile reload` | Op level 2 | Reloads config from disk. Localization key: `command.mercantile.reload` |

### Implementation Notes
- Register via Fabric's `CommandRegistrationCallback`.
- `/mercantile reload` re-reads `config/mercantile.json` and pushes updated server config values to all connected clients.
- `/mercantile pins` (the bare list) is gated on `enableTradePinning` and reports the feature as off when disabled; `pins remove` and `pins clear` are intentionally *not* gated, so pins occupying cap slots can always be shed while the feature is off. Listing also lazily prunes pins whose offer is no longer sold by a nearby loaded villager. See Section 30 for the pinning system.

---

## 20. Multiplayer Edge Cases

Rules for interactions that involve multiple players or shared villager state.

### Villager Pickup (Section 1)
- **Cannot pick up a villager another player is trading with.** The villager has an active `tradingPlayer` reference — check it before pickup.
- **Cannot pick up a villager in follow mode with another player.** Check the transient follow state.

### Follow Mode (Section 5)
- **A villager can only follow one player at a time.** Second player's follow attempt is denied.
- **Following state is per-villager, not per-player-per-villager.** The villager knows who it follows; other players see the follow particles but cannot interfere.

### Trade Cycling (Section 3)
- **Only the player currently trading with the villager can cycle.** The C2S packet is validated against the villager's `tradingPlayer`.

### Reputation (Section 4)
- Reputation is per-player. Exclusive trades are injected per-player at trade screen open. Two players trading with the same villager see different trade lists if their reputation tiers differ.

### Sentry Pylon (Section 18)
- Pylons are not player-owned. Any player can fuel or break them. Sentry golems defend all players and villagers equally.

---

## 21. Creative Mode Behavior

Adjustments when the player is in creative mode, to support testing and building.

| Feature | Creative Adjustment |
|---|---|
| Villager Pickup | XP cost waived |
| Trade Cycling | Emerald cost waived |
| Sentry Pylon | Fuel not consumed |
| Bulk Trading | No change (creative players have items anyway) |
| Reputation | No change (admin commands cover testing needs) |
| Follow Mode | No change |

---

## 22. Trade Index (EMI / REI / JEI Integration)

A unified, searchable trade catalog integrated into recipe viewers. Replaces the need for mods like "EMI Trades" with a purpose-built experience that supports search, filtering, and Mercantile-specific data.

### Problem
Existing trade viewer mods (e.g. EMI Trades) paginate trades by villager — one profession at a time, one page at a time. Finding a specific trade means clicking through up to 13 professions and multiple pages per profession. There is no way to ask "who sells Mending?" or "what can I buy with rotten flesh?" without manual scanning.

### Trade Index View

A single, searchable list of **all** villager trades across all professions and levels.

**Entry format (one row per trade):**

```
[Profession Icon]  [Input 1] + [Input 2] → [Output]    Level: Apprentice
```

- Profession icon: small villager head (reuses player head textures from pickup feature).
- Items rendered as standard recipe viewer item slots (hoverable for full tooltip).
- Level badge shows the minimum villager level required for the trade to appear.

**Search integration:**
- Fully indexed by the recipe viewer's search. Typing "mending" in the EMI/REI/JEI search bar shows the librarian trade that sells Mending books. Typing "emerald" shows all trades involving emeralds.
- Works bidirectionally: search for an input item to find what you can buy, or an output item to find what sells it.

**Filtering:**
- **By profession** — tab row or dropdown at the top of the trade index. Click "Librarian" to see only librarian trades. "All" is the default.
- **By level** — filter to a specific villager level (Novice through Master). Useful for trade cycling at a specific level.
- **By availability** — toggle to show/hide locked-behind-reputation trades (exclusive trades from section 4).

### Exclusive Trade Display
- Trades from the reputation system (section 4) appear in the index with a **reputation tier badge** (e.g. gold star for Honored, silver for Trusted).
- Hovering the badge shows: "Requires Trusted reputation or higher".
- These trades are visually distinct (subtle gold border or background tint) so players can tell at a glance what's vanilla vs. what's Mercantile-exclusive.
- **Intentional design:** Exclusive trades are always visible in the index regardless of the player's current reputation. This serves as aspiration/discovery — players can see what trades they're working toward. The tier badge makes the requirement clear.

### Item Lookup Integration
- When viewing any item in EMI/REI/JEI (e.g. right-clicking an enchanted book), the "Uses" and "Recipes" tabs include villager trades:
  - **Recipes tab:** "Villager sells this item" — shows all trades where this item is the output.
  - **Uses tab:** "Villager buys this item" — shows all trades where this item is an input.
- This is the key UX win: natural recipe-viewer integration means players discover villager trades through the same workflow they use for crafting recipes.

### Data Source
- Trade data is extracted from the vanilla `VillagerTrades` registry at runtime (not hardcoded). This automatically picks up any datapack modifications to trade pools.
- Mercantile exclusive trades are loaded from the datapack files (`data/mercantile/exclusive_trades/`).
- Sentry pylon recipe is registered as a normal crafting recipe and appears in recipe viewers automatically.

### Multi-Loader Support
The trade index is implemented as three parallel plugins sharing a common data layer:

| Viewer | Plugin Interface | Priority |
|---|---|---|
| EMI | `EmiPlugin` + `EmiRecipeCategory` | Primary — most popular on Fabric |
| REI | `REIClientPlugin` + `DisplayCategory` | Secondary |
| JEI | `IModPlugin` + `IRecipeCategory` | Tertiary — for players using JEI on Fabric |

A shared `TradeIndexDataSource` class builds the trade list once; each plugin adapter wraps it for its viewer's API.

### Implementation Notes
- All three viewers are compile-only optional dependencies (already in `build.gradle`).
- Plugin classes are registered via their respective entrypoint mechanisms (EMI: `emi` entrypoint in `fabric.mod.json`, REI: `rei_client` entrypoint, JEI: `@JeiPlugin` annotation).
- Trade data is rebuilt on resource reload (captures datapack trade changes).
- Custom `EmiRecipeCategory` / `DisplayCategory` / `IRecipeCategory` named "Villager Trades" with a villager head icon.
- Each trade is one recipe entry. The recipe viewer handles search indexing automatically once items are registered as inputs/outputs.

---

## 23. Third-Party Profession Support

Mercantile must support modded villager professions (e.g. from Create, Farmer's Delight, or custom datapacks). The `BuiltInRegistries.VILLAGER_PROFESSION` registry is the single source of truth — Mercantile never hardcodes the set of vanilla professions.

### Design Principle

Every feature that references professions must work in one of two modes:
1. **Registry-driven** — iterates or queries `BuiltInRegistries.VILLAGER_PROFESSION` at runtime. Automatically picks up modded entries.
2. **Graceful fallback** — where per-profession data is needed (textures, exclusive trades), missing entries get a sensible default rather than crashing or being invisible.

### Per-Feature Impact

**Villager Pickup — Head Textures (Section 1):**
- `VillagerHeadTextures` maps `ResourceLocation` (profession ID) → Base64 texture value for player head skins.
- Vanilla professions use pre-existing skins from minecraft-heads.com, hosted on Mojang's CDN (see section 1, Head Texture Source).
- **Modded profession fallback:** The generic unemployed villager head is used for any profession not in the map.
- **API hook:** Other mods can register head textures for their professions via a public `VillagerHeadTextures.register(ResourceLocation professionId, String base64TextureValue)` method, called during mod initialization.
- The registry is keyed by `ResourceLocation`, not by `VillagerProfession` enum — this naturally supports any namespaced profession.

**Villager Pickup — Display Name (Section 1):**
- Profession display name is resolved via the translation key `"entity.minecraft.villager." + professionId` (vanilla convention) or the mod's own translation key.
- Mercantile does not maintain its own profession name list. If a modded profession has no translation, the raw ID is shown as a fallback (e.g. "create:mechanic" → "Mechanic Villager" via ID parsing).

**Trade Cycling (Section 3):**
- Trade re-rolling calls into the vanilla `VillagerTrades.TRADES` registry (a map of `VillagerProfession` → trade lists per level).
- Modded professions that register their trades through the vanilla system work automatically.
- If a modded profession uses a non-standard trade registration mechanism, cycling produces no trades for that profession — the button is hidden rather than generating empty results.

**Reputation — Exclusive Trades (Section 4):**
- Exclusive trade datapacks use the profession's `ResourceLocation` as the filename: `data/mercantile/exclusive_trades/<namespace>/<profession>.json` (e.g. `data/mercantile/exclusive_trades/create/mechanic.json`).
- Mercantile ships built-in exclusive trades only for vanilla professions. Modded professions have no exclusive trades by default — mod authors or pack makers can add them via datapacks.
- The `_mercantile.json` cross-profession trades apply to all professions, including modded ones.

**Trade Index — EMI/REI/JEI (Section 22):**
- `TradeIndexDataSource` iterates `VillagerTrades.TRADES` at runtime. All registered professions (vanilla and modded) are included automatically.
- Profession filter tabs are generated dynamically from the registry — no hardcoded list.
- Profession icons use `VillagerHeadTextures` with the same fallback as pickup (generic head for unknown professions).
- Profession display names follow the same translation-key resolution as pickup.

**Villager Info Panel (Section 14):**
- Profession name and level display use the same translation-key resolution. No special handling needed — modded professions show their localized name if available, raw ID if not.

**Profession Lock (Section 12):**
- Profession-agnostic by design. The lock flag is on the villager entity, not per-profession. Works for any profession.

**Villager Names (Section 2):**
- Name assignment is biome-based, not profession-based. No profession impact.

### Implementation Notes
- `VillagerHeadTextures` is initialized during `ModInitializer.onInitialize()` with vanilla profession → Base64 texture mappings, then left open for other mods to call `register()`.
- All profession ID comparisons use `ResourceLocation`, never string matching or ordinal comparison.
- The exclusive trades loader scans all loaded datapacks for files matching the `data/mercantile/exclusive_trades/**/*.json` glob pattern — this naturally picks up files from any namespace.

---

## 24. Villager Mood

Villagers carry an intrinsic mood that drifts toward how well their needs are met and feeds back into pricing, restock speed, and ambient particles. Mood is villager-global — identical for every player — and has no direct interaction.

### Mood Value & Tiers
- Score is `0–100` (`MIN_MOOD=0`, `MAX_MOOD=100`, `DEFAULT_MOOD=50`).
- Tiers: **Miserable** (`<25`), **Unhappy** (`<50`), **Content** (`<80`), **Happy** (`≥80`). Only Happy and Miserable carry mechanical effects; Unhappy and Content are neutral.

### Target & Drift
- The target mood is the sum of satisfied condition weights: bed (`HOME` memory) = 20, workstation (`JOB_SITE` memory) = 20, slept recently (`LAST_SLEPT` within `24000` ticks) = 20, well-fed (food points `≥` the willing-to-breed threshold) = 20, not recently hurt (no damage within `2400` ticks / 2 min) = 10, no witnessed death (none within `24000` ticks / 1 day) = 10. All satisfied = 100.
- Mood is recomputed lazily on read (trade open, tooltip, restock check, particle tick), never on a hot tick loop. The first evaluation seeds the drift clock at the stored value rather than snapping to target.
- Each elapsed `moodRecalcIntervalTicks` window moves mood toward target by `DRIFT_PER_RECALC = 2` points, clamped so it never overshoots.

### Effects
- **Price:** magnitude = `max(1, basePrice * moodPriceModifierPercent / 100)`; Happy subtracts it, Miserable adds it, otherwise no change.
- **Restock interval:** base `2400` ticks; Happy shortens to `max(1, base * (100 − moodRestockSpeedPercent) / 100)`, Miserable lengthens to `base * (100 + moodRestockSpeedPercent) / 100`.
- **Ambient particles** (when `moodAmbientParticles`): every 200 ticks, each villager within 16 blocks of a player has a `0.25` chance to emit — Happy shows the vanilla happy-villager effect, Miserable the angry effect; Unhappy/Content emit nothing.

### Souring Triggers
- Any damage `>0` from any source stamps `lastHurtGameTime`.
- On any villager death, every living villager within `DEATH_WITNESS_RANGE = 16` stamps `lastWitnessedDeathGameTime`.

### Persistence
- Stored on the villager attachment (`MercantileVillagerData`): `mood` (default 50), `lastMoodUpdateTime`, `lastHurtGameTime`, `lastWitnessedDeathGameTime`.

### Config
- `enableMood` (true), `moodPriceModifierPercent` (5, clamp 0–50), `moodRestockSpeedPercent` (20, clamp 0–80), `moodRecalcIntervalTicks` (100, clamp 20–24000), `moodAmbientParticles` (true).

---

## 25. Memorials, Mourning & Fear Pricing

Three death-driven systems dispatched from a single `AFTER_DEATH` handler.

### Memorials
- **Trigger:** a *named* villager dies while `enableMemorials` is on and the `doMobLoot` gamerule is true.
- Drops a **Memorial** item at the death position. Its custom-data blob (version 1) records the villager's name, profession, level, and cause of death (the damage source message id).
- Lore lists the profession + level (skipped for babies, unemployed, and nitwits), the localized death message, and a keepsake line.

### Mourning
- Purely cosmetic and session-only (never persisted). When `enableMourning` is on, living villagers within `MOURNING_RANGE = 16` of a death briefly grieve for `MOURNING_DURATION_TICKS = 60` (3 s): they halt navigation, look toward the death position, and emit grief-tear particles every 10 ticks. No witnesses in range → no session is created.

### Fear Pricing
- **Trigger:** `enableFearMarkup` is on and the killer is a player.
- **Village keying:** the kill is recorded against *every* bell POI within `VILLAGE_BELL_RADIUS = 48` of the death (anti-decoy — you cannot dodge fear by planting a distant bell). A death with no bell in range never causes fear.
- **Threshold:** kills are tracked per village within a rolling `fearKillWindowMinutes` window (capped at `MAX_TRACKED_KILLS = 32`). When tracked kills reach `fearKillThreshold`, the village becomes feared and stamps `fearStartGameTime`.
- **Decay:** the fear fraction falls linearly from 1 → 0 over `fearMarkupDurationDays` (evaluated lazily at trade open, no tick loop).
- **Markup:** per offer, `max(1, round(basePrice * fearMarkupPercent / 100 * fraction))`, capped to the item's max-stack headroom.
- A one-time red chat notice fires the first time a player trades with a newly feared village, independent of the demand-transparency toggle.

### Persistence
- Fear state is per-player: `fearByVillage` on `PlayerData` maps a bell-village key → recent kill timestamps, fear-start time, and the notified flag. Entries are LRU-evicted and pruned lazily on read.

### Config
- `enableMemorials` (true), `enableMourning` (true), `enableFearMarkup` (true), `fearKillThreshold` (3, clamp 1–20), `fearKillWindowMinutes` (10, clamp 1–120), `fearMarkupPercent` (25, clamp 0–200), `fearMarkupDurationDays` (3, clamp 1–30).

---

## 26. Baby Feeding (Growth Acceleration)

Feeding a baby villager its breeding foods speeds up its growth toward adulthood, on top of the pickup and tooltip behavior covered elsewhere.

### Interaction
- Right-click a **baby** villager with a villager-breeding food in the main hand (bread, carrot, potato, beetroot — the same food set that grants breeding food points).

### Mechanic
- A newborn needs `FULL_GROWTH_TICKS = 24000` ticks to grow up. Each feed reduces the remaining time by `remainingTicks * babyFeedPercentPerFeed / 100`, weighted by the food's value relative to bread (`BREAD_FOOD_POINTS = 4`) — a 1-point beetroot delivers a quarter of the percentage. The reduction is clamped to `[1, remainingTicks]`.
- A cumulative cap of `FULL_GROWTH_TICKS * babyFeedMaxReductionPercent / 100` limits total acceleration per baby; once reached, feeding fails with a red message and no item is consumed.
- On success the age advances by the reduction (never past 0), the item is consumed (waived in creative), and the feed is banked in the villager's `fedGrowthTicks`.

### Persistence
- `fedGrowthTicks` (default 0) on the villager attachment tracks cumulative acceleration so the cap survives reload.

### Config
- `enableBabyFeeding` (true), `babyFeedPercentPerFeed` (10, clamp 1–100), `babyFeedMaxReductionPercent` (50, clamp 0–100).

---

## 27. Work Orders

Assign an unemployed villager to a specific profession by handing it a workstation, without hauling and placing the block yourself.

### Interaction
- Sneak + right-click an **unemployed adult** villager (profession `NONE`; nitwits and employed villagers are excluded) while holding that profession's workstation block item. The item identifies the job and is never consumed.

### Mechanic
- The held block resolves to a job-site POI via the `ACQUIRABLE_JOB_SITE` tag (beds, bells, and beehives are excluded).
- The mod searches within `SEARCH_RADIUS = 48` for up to `MAX_CANDIDATES = 5` free, reachable workstations of that type and, mirroring vanilla's acquire-POI flow, reserves the ticket up-front and sets the villager's `POTENTIAL_JOB_SITE` memory; the villager then walks over and vanilla assigns the profession on arrival.
- No reachable free workstation → nothing is mutated and no fee is charged. Any prior potential-job-site ticket the villager held is released unless it is the same site.

### Cost & Feedback
- `workOrderEmeraldCost` is charged only after a successful placement (waived in creative). Success plays the villager "yes" sound, shows an action-bar confirmation, and grants the work-order advancement; denials play the "no" sound with a red reason (cost / no workstation).

### Persistence
- None of its own — it rides vanilla POI tickets and brain memory.

### Config
- `enableWorkOrders` (true), `workOrderEmeraldCost` (1, clamp 0+).

---

## 28. Nitwit Rehabilitation

Convert a nitwit back into an unemployed villager so it can take a profession again.

### Interaction
- Use a **golden apple** on an adult nitwit. Requires **Trusted** reputation or higher (when reputation is enabled) plus an emerald fee.

### Mechanic
- Denials (red message + villager "no" sound): a conversion already pending, target is a baby, reputation below Trusted, or cannot afford the fee.
- On payment: consumes 1 golden apple + `nitwitRehabEmeraldCost` emeralds (waived in creative), plays the eating sound, shows a start message, and grants the rehab-started advancement.
- After `CONVERSION_DELAY_TICKS = 60` (3 s) the villager — if still alive and still a nitwit — has its profession-lock flag cleared, profession set to `NONE`, and brain refreshed, with a success sound and message.
- Pending conversions are in-memory only (not persisted); a `GRACE_TICKS = 12000` window covers unloaded villagers before the pending entry is dropped. Pending state is cleared on server stop.

### Config
- `enableNitwitRehab` (true), `nitwitRehabEmeraldCost` (16, clamp 0+).

---

## 29. Market Day

A recurring server-wide trading holiday derived purely from the overworld clock — no interaction, no per-player state.

### Mechanic
- Every `marketDayIntervalDays` calendar days, from dawn (`dayTime 0`) to dusk (`DUSK_DAY_TIME = 12000`), is a market day. Day 0 is excluded, so the first market day is day `marketDayIntervalDays`.
- **Price:** a flat discount of `max(0, basePrice * marketDayDiscountPercent / 100)` — never a markup.
- **Restock:** the daily restock cap rises by one cycle, from vanilla's 2 to 3.

### Announcement
- Once per market day at dawn: a gold action-bar message, a bell sound, and happy-villager particles on villagers within `CELEBRATION_RANGE = 48` of each player. The announcement is deferred on an empty server so the first player to log in that day still sees it.

### Persistence
- `MarketDayState` (a `SavedData` on the overworld, key `mercantile_market_day`) stores `lastAnnouncedDay` so a restart cannot double-announce.

### Config
- `enableMarketDay` (true), `marketDayIntervalDays` (7, clamp 1–1000), `marketDayDiscountPercent` (5, clamp 0–100).

---

## 30. Trade Pins

Pin specific villager offers per player and get notified when they restock. Pins are surfaced in the reputation detail panel and managed with `/mercantile pins` (see Section 19).

### Mechanic
- A pin binds one player to one offer of one villager, identified by a content hash of the offer (two content-identical offers share a hash, so a re-rolled but identical trade stays pinned).
- Toggling a pin is capped at `maxPinnedTradesPerPlayer` (hard upper bound `MAX_PINNED_TRADES = 64`); exceeding the cap shows a red action-bar notice.
- **Restock notify:** when a villager restocks, online players in the same dimension within `pinRestockNotifyRange` whose pin matches a replenished offer get a green action-bar message; the detail-panel summary refreshes for all pin holders regardless of range.
- **Pruning:** pins are removed when the villager dies (offline players' entries become "unknown") and lazily when the offer is no longer sold. Stock resolves to In-Stock, Out-of-Stock, Offer-Gone, or Unresolved.

### Networking / HUD
- Two S2C payloads: an index-aligned boolean array marking which of the open villager's offers are pinned, and a per-pin summary (name, trade summary, status) that drives the reputation detail panel. The summary payload is empty when the feature is disabled.

### Persistence
- `PlayerData.pinnedTrades` is a list of `PinnedTrade` records — villager UUID, offer hash, and server-locale display snapshots of the villager name and trade summary (string lengths clamped; the list is truncated to 64 on load).

### Config
- `enableTradePinning` (true), `maxPinnedTradesPerPlayer` (10, clamp 1–64), `pinRestockNotifyRange` (128, clamp 8–256).

---

## 31. Delivery Contracts

Villagers occasionally offer a "bring me N of item X for Y emeralds and reputation" contract. Gated behind `enableReputation`.

### Rolling
- Eligible villagers are non-baby, alive, and neither unemployed nor nitwits.
- A tick sweep (`SWEEP_INTERVAL_TICKS = 40`, `SWEEP_RANGE = 32`) rolls an offer with per-sweep probability `contractOfferChance/100 * 40/24000`. While an unaccepted offer is live, a speech-bubble cue floats above the villager.
- Offers are drawn from data-driven, weighted pools at `data/mercantile/contracts/<profession>.json` (professions without a pool never roll; emeralds, emerald blocks, the contract item, and damageable items are excluded). A roll picks the item, a count in `[minCount, maxCount]`, and a payment in `[minPayment, maxPayment]` scaled by `contractPaymentScale` (bounds: count ≤ 1024, payment ≤ 4096).
- An unaccepted offer is retracted after `OFFER_WINDOW_TICKS = 12000` (half a day).

### Accept & Deliver
- **Accept:** sneak + right-click the offering villager with **paper** (1 consumed, waived in creative) to receive a written **contract item** stamped with a deadline of `now + contractDeadlineDays`. The item carries its own contract UUID, item/count/payment, deadline, and the villager's name/position/dimension.
- **Deliver:** right-click the *same* villager (matched by contract UUID, not villager UUID) with the contract item. Outcomes: Completed, Wrong-Villager, Expired, Missing-Items, Invalid. On completion the required items are consumed from main + off hand, the emerald payment is paid, and reputation is granted. Expired or now-invalid contracts are voided.
- **Reputation:** completion grants a cap-bypassing `contractRepGain`, bounded to `contractRepPerDay` awards per day; deliveries beyond the daily cap still pay emeralds.

### Persistence
- The active contract is stored on the villager attachment; the daily contract-rep award count lives in the player's daily counters.

### Config
- `enableContracts` (true), `contractOfferChance` (50, clamp 0–100), `contractPaymentScale` (100, clamp 0–1000), `contractRepGain` (3), `contractRepPerDay` (3, clamp 0–50), `contractDeadlineDays` (2, clamp 1–30). Requires `enableReputation`.

---

## 32. Gratitude Gifts

The inverse of gifting villagers: at the top reputation tier, nearby villagers occasionally throw *you* a gift. Requires `enableReputation`.

### Mechanic
- Piggybacks on the reputation proximity tick (roughly once per second) rather than scanning on its own. Each check, an **Honored**-tier player near at least one villager has a `1/180` chance (~3 min average) to receive a gift.
- A daily cap of `gratitudeGiftsPerDay` applies; it is counted separately and does **not** consume the reputation daily total or sub-caps.
- A random nearby villager tosses a profession-flavored item from a weighted, data-driven table (`data/mercantile/gratitude_gifts/<profession>.json`); professions without a table fall back to bread and wheat seeds. The dropped item entity is locked to the recipient so no one else can grab it, with a villager "yes" sound and happy effect.

### Persistence
- The daily gift count lives in the player's daily counters and rolls over per new day.

### Config
- `enableGratitudeGifts` (true), `gratitudeGiftsPerDay` (1, clamp 0–10). Requires `enableReputation`.

---

## Configuration

All features are independently toggleable via ModMenu / Cloth Config screen and a JSON config file (`config/mercantile.json`).

### Server Config

| Key | Type | Default | Description |
|---|---|---|---|
| `enableVillagerPickup` | bool | true | Toggle villager pickup |
| `pickupXpCost` | int | 5 | XP levels required to pick up |
| `enableNames` | bool | true | Toggle random name assignment |
| `enableTradeCycling` | bool | true | Toggle trade cycling button |
| `tradeCycleEmeraldCost` | int | 6 | Emeralds per cycle |
| `enableReputation` | bool | true | Toggle reputation system |
| `reputationTradeGain` | int | 1 | Rep gained per trade-gain pulse |
| `reputationCureGain` | int | 5 | Rep gained for curing a zombie villager (bypasses daily cap) |
| `reputationAttackLoss` | int | 15 | Rep lost per villager attack |
| `reputationKillLoss` | int | 40 | Rep lost for killing a villager |
| `reputationCycleGain` | int | 1 | Rep gained per trade cycle |
| `reputationDailyCap` | int | 5 | Max total rep earnable per in-game day from trades + cycles + gifts (range 1–50) |
| `reputationTradesPerGain` | int | 5 | Completed trades per trade-gain pulse (range 1–20) |
| `reputationDailyMaxTradeRep` | int | 2 | Daily sub-cap on trade rep (range 1–10) |
| `reputationDailyMaxCycleRep` | int | 1 | Daily sub-cap on cycle rep (range 1–10) |
| `enableRaidReputation` | bool | true | Toggle reputation gain for defending raids |
| `reputationRaidWinGain` | int | 10 | Rep granted for winning a raid (bypasses daily cap) |
| `enableWanderingTraderRep` | bool | true | Toggle wandering-trader bonus offer at high reputation |
| `enableGifting` | bool | true | Toggle accepting profession-matched item gifts for rep |
| `reputationGiftGain` | int | 1 | Rep gained per accepted gift item |
| `reputationDailyMaxGiftRep` | int | 2 | Daily sub-cap on gift rep (range 1–10) |
| `reputationNegativeDecayPerDay` | int | 1 | Points a negative score recovers toward 0 per in-game day (0 disables) |
| `enableFollowMode` | bool | true | Toggle villager follow mode |
| `maxFollowingVillagers` | int | 3 | Max villagers following a player |
| `enableSendHome` | bool | true | Released villagers walk back to bed/workstation |
| `enablePathfindingFixes` | bool | true | Toggle pathfinding improvements |
| `enablePathfindingDoors` | bool | true | Door/gate fix |
| `enablePathfindingStairs` | bool | true | Stair/slab fix |
| `enablePathfindingLadders` | bool | true | Ladder climbing |
| `enablePathfindingWater` | bool | true | Water avoidance |
| `enableBulkTrading` | bool | true | Toggle shift-click bulk trades |
| `enableProfessionLock` | bool | true | Toggle profession lock protection |
| `enableHealing` | bool | true | Toggle boosted potion healing for villagers |
| `healingMultiplier` | float | 2.0 | Potion healing multiplier for villagers (range 1.0–10.0) |
| `enableRestockIndicator` | bool | true | Toggle restock timer in trade GUI |
| `enableDemandTransparency` | bool | true | Toggle price breakdown tooltip |
| `enableBreedingTooltip` | bool | true | Toggle breeding-status tooltip (Jade/WTHIT) |
| `enableStateIndicators` | bool | true | Toggle villager state indicators (Jade/WTHIT) |
| `enableSentryPylon` | bool | true | Toggle sentry pylon block |
| `enablePylonBellAlarm` | bool | true | Pylon rings the nearest village bell on threat detection |
| `pylonDetectionRadius` | int | 32 | Sentry pylon hostile scan radius |
| `pylonMaxFuel` | int | 8 | Max iron blocks a pylon can hold |
| `pylonMaxGolems` | int | 3 | Max active sentry golems per pylon |
| `sentryDespawnSeconds` | int | 30 | Seconds before idle sentry golems despawn |

### Client Config

| Key | Type | Default | Description |
|---|---|---|---|
| `villagerSoundVolume` | float | 1.0 | Villager sound volume (0.0-1.0) |
| `enableWorkstationVis` | bool | true | Toggle workstation link particles |
| `enableBellRadiusVis` | bool | true | Toggle bell radius visualization |
| `enableInfoPanel` | bool | true | Toggle extended info panel in trade GUI |
| `enableReputationHud` | bool | true | Toggle the reputation tier HUD indicator |
| `hudAnchor` | enum | TOP_LEFT | HUD corner anchor: TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT |
| `hudOffsetX` | int | 4 | HUD horizontal inset from the anchored edge (range 0–10000) |
| `hudOffsetY` | int | 4 | HUD vertical inset from the anchored edge (range 0–10000) |

---

## Compatibility

### Required
- Fabric Loader >=0.16.10
- Fabric API
- Minecraft 1.21.1

### Optional Integrations
- **Jade** — Breeding info, villager state indicators, profession lock status, sentry pylon fuel
- **WTHIT** — Same as Jade (parallel plugin)
- **ModMenu + Cloth Config** — Config screen
- **EMI / REI / JEI** — Searchable villager trade index with profession/level filtering and exclusive trade display

---

## Public API

Mercantile exposes one stable, read-only public package, `com.rfizzle.mercantile.api`,
conforming to the [Concord API Standard v1](https://github.com/rfizzle/concord/blob/master/API-STANDARD.md).
Every type in it carries the mod's own `@Stable` marker (a local annotation, per
the suite's no-shared-jar rule — there is no shared library mod). Signatures in
this package survive minor and patch releases; a breaking change requires a major
version bump and a changelog entry naming the broken signature. Everything outside
the package — attachments, managers, mixins — is internal and may change without
notice.

### Consumption

Soft dependency only: compile against Mercantile with `modCompileOnly` and guard
every call site with `FabricLoader.getInstance().isModLoaded("mercantile")`, so the
consuming mod loads cleanly whether or not Mercantile is present. All read
accessors are authoritative **server-side only**.

### `MercantileAPI` — read-only accessors

A static facade over Mercantile's server-side state; it never mutates that state.

| Method | Returns |
|---|---|
| `getReputation(ServerPlayer)` | The player's reputation score, clamped to `[-200, 1500]`; `0` (NEUTRAL) for a player with no reputation history. |
| `getReputationTier(ServerPlayer)` | The player's `ReputationTier`, derived from the score. |
| `isSentryGolem(Entity)` | `true` if the entity is a pylon-spawned, temporary sentry golem. |
| `isProfessionLocked(Villager)` | `true` if the villager's profession is locked (locks permanently after its first trade). |
| `isTradeLocked(Villager, MerchantOffer)` | `true` if that specific offer is locked (locked offers survive trade cycling); offers are matched by item/count identity, not object instance. |
| `isHudVisible()` | HUD coordination accessor (HUD-STANDARD §6): whether the reputation HUD element is currently drawn. Reflection-backed; safe to call from common code. Sentinel `false` on a dedicated server or when the element is hidden. |
| `getHudHeight()` | HUD coordination accessor: the element's height contribution in px for lower-priority slots to offset past — `0` when not visible, `22` (20px element + 2px gap) when visible. |

### `ReputationTier`

The six standings a player can hold, ordered best to worst: `HONORED`, `TRUSTED`,
`LIKED`, `NEUTRAL`, `DISTRUSTED`, `REVILED`. Public methods: `fromScore(int)`,
`minScore()`, `displayName()`, and `priceModifierForScore(int score, int basePrice)`.
The constants, their ordering, and these methods are stable; the exact score
thresholds are gameplay tuning and may shift in a minor release — consumers should
compare tiers, not hardcode score numbers.

### Events

Both are Fabric array-backed events, fired **server-side only**, from the single
internal choke point for their domain. A listener that throws is caught and logged;
it cannot corrupt the underlying flow, but it may stop listeners registered after
it from seeing that event.

- `ReputationChangedCallback.EVENT` → `onReputationChanged(ServerPlayer, int oldScore, int newScore)`
  — fires whenever a player's score actually changes. Not fired when a change is
  fully absorbed by clamping, nor by the one-shot legacy save-format rescale.
- `TradeExecutedCallback.EVENT` → `onTradeExecuted(ServerPlayer, AbstractVillager, MerchantOffer)`
  — fires after a completed trade with a villager or a wandering trader. A bulk
  (shift-click) trade fires once per executed trade.

---

## Sound Design

Every cue here is **organic** — villager voices, bells, anvil, iron-golem foley — which vanilla already nails, so all current sounds map to vanilla `SoundEvent`s; pure synthesis would only make them feel fake. Custom synthesized cues (via the `/sfx` pipeline) are added where a sound benefits from its own identity, per concord `design/DESIGN-SYSTEM.md` §9.

### Sound Mapping

| Feature | Event | Vanilla Sound |
|---|---|---|
| Villager Pickup — pickup | Sweep | `minecraft:entity.player.attack.sweep` |
| Villager Pickup — placement | Block break particles | `minecraft:block.stone.break` |
| Trade Cycling — success | Villager yes | `minecraft:entity.villager.yes` |
| Trade Cycling — denied | Villager no | `minecraft:entity.villager.no` |
| Reputation — trade refusal | Angry villager | `minecraft:entity.villager.no` |
| Follow Mode — start following | Villager yes | `minecraft:entity.villager.yes` |
| Follow Mode — stop following | Villager ambient | `minecraft:entity.villager.ambient` |
| Sentry Pylon — golem spawn | Iron golem repair | `minecraft:entity.iron_golem.repair` |
| Sentry Pylon — golem despawn | Iron golem damage | `minecraft:entity.iron_golem.damage` |
| Sentry Pylon — out of fuel | Note block bass drum | `minecraft:block.note_block.basedrum` |
| Sentry Pylon — fuel inserted | Anvil use | `minecraft:block.anvil.use` |
| Bell Radius — ring highlight | Bell ring | (vanilla bell ring, no additional sound) |

### Particle Mapping

Which effect fires on which event. Mod-specific effects use custom particles;
visualization overlays (workstation links, bell radius) use vanilla particles
since they are functional, not themed. The custom particle **texture files and
their pixel specs** are catalogued in [`design/ASSETS.md`](ASSETS.md).

| Feature | Effect | Particle |
|---|---|---|
| Villager Pickup — pickup | Emerald star burst | `mercantile:pickup_sparkle` |
| Villager Pickup — placement | Block break | `minecraft:block` (stone variant) |
| Trade Cycling — success | Gold coin flash | `mercantile:cycle_glint` |
| Reputation — refusal | Angry cloud above villager | `minecraft:angry_villager` |
| Follow Mode — following indicator | Teal ground glow at feet | `mercantile:follow_trail` |
| Workstation Links — bound lines | Colored particle lines | `minecraft:dust` (profession-colored) |
| Workstation Links — unbound villager | Angry pulsing | `minecraft:angry_villager` |
| Workstation Links — unclaimed POI | Yellow orbit | `minecraft:dust` (yellow) |
| Bell Radius — circle | Gold ring on ground | `minecraft:dust` (gold) |
| Bell Radius — ring highlight | Villager glow | Vanilla entity glow flag |
| Sentry Pylon — idle | Drifting motes | `mercantile:pylon_mote` |
| Sentry Pylon — active | Jagged energy sparks | `mercantile:pylon_spark` |
| Sentry Pylon — out of fuel | Red pulse | `minecraft:dust` (red) |
| Sentry Pylon — golem despawn | Iron shard crumbling | `mercantile:golem_shard` |

---

## Localization

All user-facing text uses translation keys in `assets/mercantile/lang/en_us.json`. No hardcoded strings in code.

### Key Conventions

| Pattern | Example | Used For |
|---|---|---|
| `mercantile.action.*` | `mercantile.action.pickup_success` | Action bar messages |
| `mercantile.action.*.denied` | `mercantile.action.pickup_denied.xp` | Denial messages with reason |
| `mercantile.tooltip.*` | `mercantile.tooltip.pickup.instruction` | Item tooltip lines |
| `mercantile.tier.*` | `mercantile.tier.honored` | Reputation tier display names |
| `mercantile.gui.*` | `mercantile.gui.restock_timer` | Trade GUI text (restock, demand, info panel) |
| `mercantile.gui.cycle.*` | `mercantile.gui.cycle.button` | Trade cycling button/status |
| `config.mercantile.*` | `config.mercantile.pickupXpCost` | Cloth Config screen labels |
| `config.mercantile.*.tooltip` | `config.mercantile.pickupXpCost.tooltip` | Cloth Config field descriptions |
| `command.mercantile.*` | `command.mercantile.reload` | Command feedback messages |
| `mercantile.jade.*` | `mercantile.jade.willing_to_breed` | Jade/WTHIT tooltip lines |
| `mercantile.pylon.*` | `mercantile.pylon.fuel_level` | Sentry pylon status text |
| `mercantile.trade_index.*` | `mercantile.trade_index.category_name` | EMI/REI/JEI trade index UI |

Parameterized messages use `String.format` style (`%s`, `%d`) — e.g. `"mercantile.action.pickup_denied.xp": "Not enough experience (need %d levels)"`.

---

## Testing Strategy

### Unit Tests (JUnit + `fabric-loader-junit`)
Fast, no Minecraft runtime needed. Located in `src/test/`.

- Config parsing and serialization (round-trip JSON)
- Name pool loading from JSON (including `replace` flag, duplicates, missing files)
- Data version migration chain (apply migrations in sequence, detect version gaps)
- Reputation tier calculation (boundary values, cap enforcement)
- Trade offer identity hashing (stability across price changes, instability across item changes)
- Exclusive trade datapack parsing (component format, `min_tier_override`)

### Gametests (Fabric Gametest API)
Require a running server instance. Located in `src/gametest/`.

- Villager pickup round-trip: pick up, verify NBT preservation, place, verify entity state matches
- Profession lock: trade with villager, break workstation, verify profession retained
- Reputation accrual: execute trades, verify score changes, verify tier thresholds
- Trade cycling: cycle trades, verify unlocked trades change, verify locked trades preserved
- Sentry pylon: place pylon, fuel it, spawn hostile, verify golem spawns, verify despawn after threat cleared
- Follow mode: trigger follow, verify villager pathfinds toward player, verify max follower cap
- Bulk trading: shift-click trade, verify correct quantity executed, verify stock limits respected

### Manual Testing
Features that require visual/UI verification and can't be automated:

- Trade GUI mixin rendering (cycle button placement, restock timer, demand tooltip, info panel)
- Visualization features (workstation links, bell radius)
- Jade/WTHIT tooltip rendering
- EMI/REI/JEI trade index UI and search integration
- Sentry pylon block model and visual states
