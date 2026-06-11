# Code Review — Mercantile Mod (Sprint 1–4)

Comprehensive per-story code review covering code quality, test quality, edge cases, risks, and feature conflict notes.

---

## Table of Contents

1. [Initial Scaffolding](#1-initial-scaffolding)
2. [Config System](#2-config-system)
3. [Persistence Infrastructure](#3-persistence-infrastructure)
4. [Networking Infrastructure](#4-networking-infrastructure)
5. [Profession Head Textures](#5-profession-head-textures)
6. [Command Tree](#6-command-tree)
7. [Villager Name System](#7-villager-name-system)
8. [Profession Lock System](#8-profession-lock-system)
9. [Villager Healing Boost](#9-villager-healing-boost)
10. [Villager Pathfinding Improvements](#10-villager-pathfinding-improvements)
11. [Bulk Trading](#11-bulk-trading)
12. [Villager Pickup and Placement (S-015)](#12-villager-pickup-and-placement-s-015)
13. [Reputation System (S-017)](#13-reputation-system-s-017)
14. [Trade Cycling (S-016)](#14-trade-cycling-s-016)
15. [Pickup Sparkle Particle](#15-pickup-sparkle-particle)
16. [Villager Follow Mode (S-018)](#16-villager-follow-mode-s-018)

---

## 1. Initial Scaffolding

**Commit:** `0df0f8e` — Initial scaffolding for mercantile mod

**Summary:** Clean, well-structured Fabric mod scaffold with good build practices; a handful of missing assets, a dangling reference, and minor build hygiene issues.

### Positive Findings
- Version computation (`computeModVersion`/`runGitDescribe`) handles all edge cases (no tags, dirty tree, post-tag commits, bare-SHA fallback) with graceful fallback.
- Repository content filtering with `content {}` blocks prevents slow/leaky resolution.
- Split source sets done correctly with proper classpath isolation.
- Test classpath isolation correctly excludes `fabric-api` from JUnit.
- Compat deps are all `modCompileOnly` + `modLocalRuntime` — correct for optional integrations.
- `Mercantile.id()` utility establishes the `ResourceLocation` convention early.

### Issues

| Severity | Issue |
|----------|-------|
| Medium | **Missing `scripts/release.sh`** — `make release` will fail. Include the script or stub the target. |
| Medium | **Missing `icon.png`** — `fabric.mod.json` declares it but the file does not exist. Fabric Loader will log a warning. |
| Low | **No gametest or test source directories** — `./gradlew test` silently passes with zero tests. Add a smoke test. |
| Low | **Duplicate `loom {}` blocks** (lines 106, 122) — works but confusing. Consolidate. |
| Low | **`runGitDescribe` has no timeout** on `proc.waitFor()`. A hung git process blocks Gradle config indefinitely. |
| Nit | `en_us.json` ships forward-looking translation keys that may become stale. |
| Nit | `contact` is an empty object in `fabric.mod.json` — populate or remove. |
| Nit | License year is 2025 but the commit date is 2026. |

### Risk Assessment
Low risk. No runtime logic beyond a log message. The two missing files are the only things that would cause visible failures.

---

## 2. Config System

**Commit:** `7dec136` — Add config system with JSON persistence, Cloth Config screen, and tests

**Summary:** Solid config implementation with clean Gson persistence, good Cloth Config integration, and thorough test coverage. Validation gaps and one thread-safety concern are the main items.

### Issues

| Severity | Issue |
|----------|-------|
| High | **Thread-safety on singleton INSTANCE** — `get()` uses lazy init with no synchronization. `reload()` reassigns from the server thread while Cloth Config save consumers mutate from the render thread. Use `volatile` + synchronized, or immutable-copy-on-save. |
| High | **`save()` is not atomic** — `Files.writeString` directly to the target file. Crash mid-write corrupts config. Write to `.tmp` then `Files.move` with `ATOMIC_MOVE`. |
| Medium | **No server-side validation of numeric fields from JSON** — `clamp()` only covers `healingMultiplier`. Hand-edited JSON can set `pylonDetectionRadius = 999999` or `maxFollowingVillagers = 0`. Expand `clamp()` to cover all numeric fields. |
| Medium | **Cloth Config `healingMultiplier` min is 0.0f but `clamp()` enforces 1.0f** — GUI allows values the server rejects silently. Constraints should be consistent. |
| Medium | **Config not written to disk on first load when file is missing** — user has no file to discover or edit until they open the Cloth Config screen. Generate defaults on first run. |
| Medium | **Cloth Config screen does not call `clamp()` before saving.** |
| Low | All 30 config fields are public and mutable with no encapsulation. |
| Low | `reload()` does not notify the client of the new config (caller must send sync packet). |

### Test Quality
**Strengths:** Good coverage of defaults, round-trip, missing/unknown keys, empty/null JSON, file I/O, corrupt files, partial files, clamp boundaries.

**Gaps:** No `save(Path)` test, no `fromJson()` test, no round-trip verification of unknown key preservation, no concurrent access test, no negative-value tests for integer fields.

### Feature Conflict Potential
- Server/client config split: all 30 fields in one flat class; sync sends everything including client-only fields.
- Per-world config: file is in global Fabric config dir; no per-world override support.

---

## 3. Persistence Infrastructure

**Commit:** `c0c401d` — Add persistence infrastructure with Fabric attachments for player and villager data

**Summary:** Correctly uses Fabric attachment API with Codec-based persistence. Data classes are well-encapsulated with defensive copies and unmodifiable views. A few naming, serialization, and testing gaps.

### Issues

| Severity | Issue |
|----------|-------|
| High | **Class name collision with `net.minecraft.world.entity.npc.VillagerData`** — every mixin touching both must use fully-qualified names. Rename to `MercantileVillagerData` or similar. |
| High | **`PlayerData.CODEC` does not clamp score on deserialization** — constructor assigns `this.score = score` directly. Out-of-range values from NBT edits persist until the next `setScore()`. |
| Medium | **`tradeStats` map grows unboundedly** — one entry per unique villager traded with, serialized every save tick. No eviction or cap. |
| Medium | **`VillagerData.lockedTrades` serialization has non-deterministic ordering** — `HashSet` iteration order varies, causing unnecessary chunk dirtying. Use sorted copy. |
| Medium | **No `PlayerData` gametest for NBT persistence or `copyOnDeath`**. |
| Low | Empty `init()` as class-loading trigger is undocumented. |
| Low | `proximityTicks` has no bounds validation. |

### Test Quality
**Strengths:** Defensive copying tested, unmodifiable views enforced, codec round-trips through JsonOps verified, VillagerData gametest covers full NBT cycle.

**Gaps:** No `PlayerData` save/load gametest, no `copyOnDeath` test, no `lastProximityDay` round-trip test.

---

## 4. Networking Infrastructure

**Commit:** `9d4a3c0` — Add networking infrastructure with typed payloads, handlers, and codec tests

**Summary:** Well-structured networking layer with clean payload records, correct codec patterns, and thorough round-trip tests. Unbounded collections/strings in S2C payloads and an unbounded client-side map are the main concerns.

### Issues

| Severity | Issue |
|----------|-------|
| High | **`ClientMercantileData.pylonStates` and `followStates` grow without bound and are never cleared** — no disconnect hook calls `clear()`. Register `ClientPlayConnectionEvents.DISCONNECT` callback. |
| High | **`ConfigSyncS2CPayload.configJson` uses `ByteBufCodecs.STRING_UTF8` with no length cap** — default max 32767 chars. Tighten to ~2 KB or encode fields individually. |
| Medium | **`VillageBoundsS2CPayload` and `WorkstationMapS2CPayload` don't cap collection sizes** — malicious server can OOM client. Add size guards. |
| Medium | **`readUtf()` without explicit max length in manual codecs** — use `readUtf(64)` for known-short fields. |
| Medium | **`RequestWorkstationMapC2SPayload` and `RequestVillageBoundsC2SPayload` have no rate limiting** — client can spam expensive server-side POI queries. |
| Low | Inconsistent `StreamCodec` type parameter (`ByteBuf` vs `FriendlyByteBuf`). |
| Low | `payloadRegistrationComplete` gametest is vacuous (just calls `succeed()`). |
| Low | Gametest buffer not released on test failure path (no try/finally). |

### Test Quality
**Strengths:** 24 JUnit round-trip tests cover all 13 payload types. Buffer consumption verified. Gametests use real entities.

**Gaps:** No config JSON round-trip test, no large collection / truncated buffer tests.

---

## 5. Profession Head Textures

**Commit:** `b4b884a` — Add profession head texture registry with cached profiles and display names

**Summary:** Clean static registry mapping villager professions to player-head skin textures. Good test coverage with a thread-safety concern.

### Issues

| Severity | Issue |
|----------|-------|
| High | **Thread safety on mutable `HashMap` instances** — `TEXTURES` and `PROFILE_CACHE` are plain `HashMap`s with `computeIfAbsent`. Concurrent access can corrupt the table. Use `ConcurrentHashMap` or document init-only mutation. |
| Medium | **`register()` does not validate inputs** — null profession ID or texture value silently causes NPEs later in `getProfile()`. |
| Medium | **Test pollution from static state** — no cleanup between tests; entries persist across tests. |
| Low | `GameProfile` created with empty-string name — some tooltip mods may show blank. |
| Low | `getDisplayName` translation fallback only handles underscore-delimited names. |
| Nit | `displayNameReturnsComponent` test only asserts `assertNotNull` — essentially a no-op. |

### Feature Conflict Potential
- `VillagerPickupHelper` is the primary consumer — API aligns well.
- Empty `GameProfile` name could cause blank tooltips in Jade/WTHIT head rendering.

---

## 6. Command Tree

**Commit:** `e239fdd` — Add /mercantile command tree with reputation, village, and reload subcommands

**Summary:** Solid command registration with good test coverage and correct permission gating. Command tree structure deviates from spec, duplicated clamping logic.

### Issues

| Severity | Issue |
|----------|-------|
| High | **Command syntax deviates from SPEC.md** — spec says `/mercantile reputation set <player> <value>`, implementation produces `/mercantile reputation <player> set <value>`. Either update the spec or the implementation. |
| High | **Duplicated clamping logic with magic numbers** — `addReputation` manually clamps with `Math.max(-100, Math.min(200, ...))` instead of using `PlayerData.addScore()` which already clamps via `MIN_SCORE`/`MAX_SCORE`. |
| Medium | **`getTierName` is in the wrong class** — `ReputationManager.syncToClient()` calls `MercantileCommands.getTierName()`, creating a dependency inversion. Should be in `ReputationManager` or a `ReputationTier` enum. |
| Medium | **`showVillage` is a stub that silently succeeds** — returns 1 with no feedback. Should either show "not implemented" or not be registered. |
| Medium | **No error handling around `reloadConfig`** — corrupt config silently falls back to defaults with no indication. |
| Medium | **`reloadRefreshesConfigFromDisk` test mutates shared state without try/finally isolation.** |
| Low | `showOwnReputation` returns score as command result — 0 reputation = "failure" to command blocks. |
| Low | `add` argument has no bounds (unbounded `IntegerArgumentType.integer()`). |
| Low | Tier name strings are hardcoded English, not translatable. |

### Feature Conflict Potential
- Tier names defined in three separate places (commands, exclusive trades, price modifiers) with no shared source of truth.

---

## 7. Villager Name System

**Commit:** `d78dbb8` — Add villager name system with biome-themed pools and auto-assignment

**Summary:** Solid data-driven naming system with clean design and thorough tests. One high-severity interaction with the pickup display system.

### Issues

| Severity | Issue |
|----------|-------|
| High | **Pickup item display name always shows quoted name, never profession** — every named villager triggers the `hasCustomName()` branch. Players can't distinguish professions at a glance from pickup head items. Consider showing `"Name" - Profession`. |
| High | **Thread safety of static `NAME_POOLS` HashMap** — mutated by reload listener, read from entity load. Use volatile reference-swap with immutable map. |
| Medium | **`replaced` local variable assigned but never read** — dead code in `loadNamePools`. |
| Medium | **Cross-pool name duplicates** — "Gareth", "Jasper", "Orin" etc. appear in multiple biome pools, weakening biome identity. |
| Medium | **Name collision probability** — ~50 names per pool, birthday problem gives 50% collision at ~8 villagers per village. No dedup-at-assignment mechanism. |
| Low | `BIOME_TO_CATEGORY` map is mutable `HashMap` but never modified after static init. |
| Low | Only `mercantile` namespace supported for datapack overrides. |

### Test Quality
**Strengths:** 14 JUnit tests with parameterized biome mapping, JSON validation, replace/append semantics, dedup, null handling, empty pool graceful degradation. 4 gametests cover core flows.

**Gaps:** No test for `enableNames=false`, no pickup/place round-trip name preservation test, no baby villager naming test.

---

## 8. Profession Lock System

**Commit:** `03a636a` — Add profession lock system with trade GUI icon and tooltip integrations

**Summary:** Correct core locking mechanics with good test coverage. WTHIT integration has a likely data-flow bug, and the `setVillagerData` cancellation mixin is over-broad.

### Issues

| Severity | Issue |
|----------|-------|
| High | **WTHIT provider data flow is broken** — `appendDataContext` accesses `VILLAGER_DATA` attachment client-side, but the attachment has no client sync. Always returns default (`professionLocked = false`). No `IDataProvider` registered for server-to-client data. Compare to the correctly-structured Jade integration. |
| High | **`VillagerProfessionLockMixin` cancels entire `setVillagerData`** — discards non-profession changes (level, type) bundled in the same call. Use `@ModifyVariable` to replace just the profession field instead of cancelling. |
| Medium | **`VillagerTradeOpenMixin` uses vanilla `getPlayerReputation` instead of mod's reputation system** — shows inconsistent data if both systems diverge. |
| Medium | **Info panel packet sent unconditionally** without checking `enableInfoPanel` config. |
| Medium | **`MerchantScreenMixin` reads stale `ClientMercantileData`** — no validation that info payload matches current merchant entity ID. |
| Medium | **4 separate `@Mixin(Villager.class)` classes** — increases ordering sensitivity and conflict potential. Consider consolidating. |
| Low | Gametests rely on default config (`enableProfessionLock = true`) without explicitly setting it. No "disabled config" test. |
| Low | No test for interaction with trade cycling on a locked-profession villager. |
| Low | Jade tooltip shows "Profession: Unlocked" for every non-locked villager — adds clutter for the default state. |

### Feature Conflict Potential
- **Trade Cycling:** No conflict — cycling uses current profession's trade pool.
- **Bulk Trading:** No conflict — bulk calls `notifyTrade` which triggers lock correctly.
- **Reputation:** No conflict — info panel includes both reputation and lock status.
- **Pickup/Placement:** No conflict — attachment data round-trips correctly.

---

## 9. Villager Healing Boost

**Commit:** `c273647` — Add villager healing boost for splash and lingering potions

**Summary:** Solid implementation with a clean context-flag pattern, but has a critical thread-safety flaw, a missing spec requirement, and several issues around the AreaEffectCloud mixin.

### Issues

| Severity | Issue |
|----------|-------|
| Critical | **`VillagerHealingContext` is not thread-safe** — bare `static boolean` with no synchronization. Integrated client runs server and client on separate threads. Use `ThreadLocal<Boolean>`. |
| High | **Spec requires regeneration duration doubling, but implementation only boosts heal amount** — regen effect duration is never modified. Spec deviation. |
| High | **`AreaEffectCloudMixin` sets context for ALL cloud ticks** — every area effect cloud (poison, slowness, etc.) sets the healing context to `true`. Any cloud that triggers `heal()` on a villager gets incorrectly boosted. |
| High | **No exception safety in cloud/splash mixins** — if anything throws between HEAD and RETURN injections, `exit()` never runs and context flag is permanently stuck `true`. |
| Medium | **No `enableHealing` boolean toggle** — inconsistent with every other feature's config surface. |
| Medium | **Mod Menu allows `healingMultiplier` below 1.0** but `clamp()` enforces 1.0. |
| Medium | **`healBoosted` persisted in VillagerData but is transient state** — adds codec fragility. |
| Medium | **Tipped arrow regen overwrites existing boosted state from splash potion** — applying non-boosted regen cancels an active boosted regen. |
| Low | `tippedArrowHealingNotMultiplied` test doesn't actually test tipped arrows — just tests heal without context. |
| Nit | Healing config field placed in "Trading" category in Mod Menu. |

### Test Quality
**Gaps:** No codec round-trip test for `healBoosted = true`, no tipped arrow integration test, no test for overlapping regen sources.

---

## 10. Villager Pathfinding Improvements

**Commits:** `0452b70` — Pathfinding improvements; `bb06d1d` — Fix array bounds check in ladder neighbor injection

**Summary:** Well-structured, config-gated pathfinding overhaul. The bugfix commit reveals a capacity assumption that should have been caught initially. Potential infinite recursion in double-door handling.

### Issues

| Severity | Issue |
|----------|-------|
| Critical | **Infinite recursion in `DoorBlockMixin.mercantile$handleDoubleDoor`** — `setOpen` on partner door triggers this mixin again. State guard prevents it normally, but another mod re-opening the door as a side effect creates stack overflow. Add a `ThreadLocal` or static reentrancy guard. |
| High | **`findAcceptedNode` HEAD injection bypasses ALL vanilla logic for stair/slab blocks** — skips vanilla collision checks, hazard detection, entity size validation. Use `@At("RETURN")` and only override when vanilla returned null/BLOCKED. |
| High | **Two `@Inject` methods target same `getPathType` at `RETURN`** — ordering is by declaration order, fragile to refactoring. Combine into a single injection or add explicit priority. |
| Medium | **Cooldown logic in `InteractWithFenceGate` appears inverted** — throttles node transitions instead of same-node re-checks. |
| Medium | **`gatesToClose` set leaks entries when villager dies** — opened gates remain open forever. |
| Medium | **`areOtherMobsComingThrough` removes gate from close-set even when holding open** — gate permanently removed from tracking. |
| Medium | **No test for double-door behavior** despite recursion risk. |
| Low | Ladder test doesn't verify path actually traverses the ladder node (only checks `path != null`). |
| Low | `configDisableRevertsToVanilla` has config mutation race condition with other test batches. |

### The Bug Fix
The `bb06d1d` fix adds `if (count >= nodes.length) return count;` for the ladder neighbor array. The original code assumed there would always be room in the fixed-size `Node[]` array — a classic capacity oversight. The existing ladder gametest used a constrained area with few neighbors and didn't catch it.

### Feature Conflict Potential
- **WalkNodeEvaluator mixins** are high-conflict targets with other pathfinding mods (Guard Villagers, MineColonies, etc.). The `instanceof Villager` guards reduce conflict surface.
- **DoorBlock.setOpen** conflicts with door-related mods (Supplementaries, Quark double doors).
- **Follow Mode:** Brain suppression during follow means following villagers won't use any of these pathfinding improvements (fence gates, ladders).

---

## 11. Bulk Trading

**Commit:** `171c42a` — Add bulk trading with shift-click to repeat trades up to 64 times

**Summary:** Solid implementation with good happy-path test coverage. Missing tests for two-ingredient trades and a notable interaction with reputation gain accumulation.

### Issues

| Severity | Issue |
|----------|-------|
| Medium | **`specialPriceDiff` lock is defensive but undocumented** — captures price at loop start and resets each iteration. Correct now but fragile to future changes. Needs a comment. |
| Medium | **No test for two-ingredient (dual-cost) trades** — refill logic for slot 1 follows a different code path via `ifPresent`. Significant coverage gap. |
| Medium | **No test for config toggle `enableBulkTrading = false`.** |
| Medium | **`refillSlot` refills up to `maxStackSize` instead of `cost.count()`** — may leave excess items in payment slot if loop breaks early. Items aren't lost but unexpected. |
| Low | **Reputation gain amplified by bulk trading** — up to +64 per shift-click. Design decision, but should be intentional. |
| Low | Trade lock recorded N times for same offer (redundant `addLockedTrade` calls). |
| Low | Slot range 3-38 hardcoded as magic numbers. |
| Nit | Translation key uses `%s` instead of numbered `%1$s` format specifiers. |

### Feature Conflict Potential
- **Reputation System:** Each bulk trade triggers `notifyTrade` N times, granting N reputation. A single shift-click can yield up to 64 reputation points. This is a significant acceleration of reputation gain.
- **Trade Cycling:** No conflict — cycling respects locks set during bulk.
- **Profession Lock:** No conflict — lock triggers on first trade, subsequent calls are no-ops.

---

## 12. Villager Pickup and Placement (S-015)

**Commit:** `756e725` — Add villager pickup and placement system with full NBT round-trip

**Summary:** Well-structured implementation with solid NBT round-trip. One critical hash function duplication bug and several medium-severity entity duplication risks.

### Issues

| Severity | Issue |
|----------|-------|
| Critical | **Duplicated and incompatible `offerIdentityHash` logic** — `VillagerPickupHelper.offerIdentityHash()` uses `+`/`=` delimiters and `getCostB()`, while the canonical `OfferIdentityHash.compute()` uses `\|` delimiters and `getItemCostB()`. Lock indicators on pickup item lore will never match. Delete the duplicate and call `OfferIdentityHash.compute()`. |
| High | **Entity duplication risk on placement** — `addFreshEntity(villager)` called before `stack.shrink(1)`. Crash between these lines duplicates the villager. Shrink item first. |
| High | **Pickup creates head item before deducting XP** — crash window for item/entity duplication or loss. Reorder so `discard()` is last irreversible action. |
| High | **`VillagerPlacementMixin` targets `BlockItem.useOn`** — runs for every block placement in the game. Target `StandingAndWallBlockItem` or `PlayerHeadItem` for narrower scope. |
| Medium | **Client-side early return fires before `enableVillagerPickup` config check** on placement. |
| Medium | **Malformed NBT fallback spawns default villager silently** — player loses all stored data with only a server-side log. Consider keeping item + warning. |
| Medium | **UUID preserved on round-trip** — potential for UUID collision if original wasn't properly removed. Consider stripping UUID from stored NBT. |
| Medium | **`pickupXpCost` config not clamped** — negative value causes `giveExperienceLevels` to add levels on pickup. |
| Low | `malformedNbtSpawnsDefault` gametest doesn't test the actual placement mixin fallback path. |
| Low | `placedVillagerFacesPlayer` gametest is tautological — tests `Math.atan2`, not the mixin. |
| Low | No test for creative mode XP waiver, insufficient XP denial, raid denial, or config toggle. |

### Test Coverage

| Area | Status |
|------|--------|
| NBT round-trip (profession, level, XP, trades, attachments) | Covered |
| XP deduction | Covered |
| Wandering trader exclusion | Covered |
| Malformed NBT / future data version | Partial (doesn't test mixin path) |
| Creative mode / insufficient XP / raid / config toggle | Not covered |

---

## 13. Reputation System (S-017)

**Commit:** `605c48a` — Add reputation system with score tracking, price modifiers, and exclusive trades

**Summary:** Solid implementation with clean tier logic and good test coverage. Critical price modifier stacking bug, duplicate tier-mapping definitions, and several multiplayer interaction issues.

### Issues

| Severity | Issue |
|----------|-------|
| Critical | **Price modifier stacks permanently on `specialPriceDiff`** — every trade screen open adds the reputation modifier again on top of previous value. Open screen 10 times = 10x discount/markup. No reset between opens. Fix: set absolute value instead of incrementing. |
| High | **Kill triggers both AFTER_DAMAGE and AFTER_DEATH** — actual penalty is -35 (-10 attack + -25 kill), not the documented -25. Clarify intent or add a dead-entity guard. |
| High | **Duplicate tier boundary definitions in 3 places** — `MercantileCommands.getTierName()`, `ExclusiveTradesManager.getMinScoreForTier()`, `ReputationManager.getPriceModifier()`. Extract a `ReputationTier` enum. |
| High | **`INJECTED_OFFERS` WeakHashMap is not thread-safe.** |
| Medium | **`tickCounter` is static, not reset on server restart** in integrated client. |
| Medium | **Proximity day-cap bypassed by `/time set`** — `getDayTime()` is manipulable. Use `getGameTime()` instead. |
| Medium | **Exclusive trades not gated behind `enableReputation` config.** |
| Medium | **Bulk trading interaction: reputation gain fires per-item** — up to +64 per shift-click. |
| Medium | **`curedVillagers` set grows without bound.** |
| Low | Datapack parsing silently accepts zero/negative `max_uses` and `count`. |
| Low | No test for price modifier stacking, kill double-penalty, proximity day-cap, or datapack reload. |

### Test Quality
**Strengths:** Excellent parametric coverage of tier boundaries, clamping, accumulation. Good invariant test (`discountNeverExceedsBasePrice`). Gametests exercise full mixin chain.

**Gaps:** No test for the critical stacking bug (open screen twice), kill double-penalty, proximity day-cap enforcement, datapack reload, or malformed datapack entries.

---

## 14. Trade Cycling (S-016)

**Commit:** `051d8c5` — Add trade cycling with lock tracking, cycle_glint particles, and tests

**Summary:** Solid implementation with good architectural separation. Two bugs: duplicate hash function causing incorrect lock display, and sync ordering issue dropping exclusive trades after cycling.

### Issues

| Severity | Issue |
|----------|-------|
| Critical | **Duplicate, incompatible `OfferIdentityHash` implementations** — `OfferIdentityHash.compute()` uses `\|` delimiters, `VillagerPickupHelper.offerIdentityHash()` uses `+`/`=`. Locked trades show as unlocked on pickup item lore. Delete the duplicate. |
| High | **Sync packet sent before exclusive trades are re-injected** — `syncOffers` at line 130 runs before `injectOffers` at line 132. Client won't see exclusive trades after cycling until screen is reopened. Move `syncOffers` after `injectOffers`. |
| High | **No server-side rate limiting on cycle packets** — client can spam cycles, farming reputation (+2 each) and consuming CPU for trade generation. Add per-player cooldown. |
| High | **Hash collisions between vanilla trades are real** — `inputA|costB|result` excludes counts, so `emerald||bread` at different levels (different quantities) share the same hash. Trading one locks both. Document or include level/slot in hash. |
| Medium | **`canCycle()`/`cycle()` TOCTOU** — separate check-then-act with no atomicity guarantee. |
| Medium | **`countEmeralds`/`removeEmeralds` only scan main inventory** — offhand emeralds not counted. |
| Medium | **Candidate generation may produce fewer trades than `slotsToFill`** — villager ends up with fewer total trades. |
| Medium | **Exclusive trades can become permanently locked** — hash persists in `VillagerData` even after reputation drops below threshold. Set grows unboundedly. |
| Low | Unrelated texture files bundled into this commit. |
| Low | `gui.mercantile.cycle_trades.cost` lang key appears unused. |

### Test Quality
**Strengths:** 5 gametests cover lock preservation, emerald deduction, disabled states. 10 unit tests comprehensively cover hash invariance and distinctness.

**Gaps:** No test for sync ordering bug, nitwit/wandering trader cycling, candidate shortfall, creative mode bypass, or hash agreement with `VillagerPickupHelper`.

---

## 15. Pickup Sparkle Particle

**Commit:** `7e96848` — Add pickup_sparkle particle effect on villager pickup

**Summary:** Clean, minimal, spec-compliant commit. Correct client/server separation, complete registration, consistent patterns. No significant issues.

### Issues

| Severity | Issue |
|----------|-------|
| Low | **Code duplication** — `PickupSparkleParticle` is structurally identical to `CycleGlintParticle` and `FollowTrailParticle`. Consider extracting a `BaseModParticle`. Livable at 3 particle types. |
| Low | **Alpha fade could divide by zero if lifetime were 0** — impossible with current randomized range (12-19) but a defensive `Math.max(1, ...)` would be future-proof. |
| Low | **No dedicated particle emission test** — understandable since asserting on `sendParticles` in gametests requires packet capture. |
| Nit | Magic numbers in `sendParticles` call (count=18, spread=0.3/0.5/0.3, speed=0.03). |

### Verdict
Ship it. Small, focused, correct.

---

## 16. Villager Follow Mode (S-018)

**Commit:** `00b879e` — Add villager follow mode with emerald toggle, brain suppression, and tests

**Summary:** Solid feature with clean manager/goal/mixin separation. Critical state desync bug, mixin ordering ambiguity, and network handler validation gap.

### Issues

| Severity | Issue |
|----------|-------|
| Critical | **`stopFollowing(UUID)` does not clear synced entity data** — `SynchedEntityData` boolean remains `true` on unload, causing phantom client-side particles. The `Villager` overload does this correctly; the UUID overload silently skips it. Use `ENTITY_UNLOAD` to call the Villager overload instead. |
| Critical | **`startFollowing` silently rejects already-following villager, but emerald is consumed regardless** — mixin doesn't check return value before consuming emerald. |
| High | **Two `@Inject(method = "mobInteract", at = @At("HEAD"))` with no priority** — undefined ordering between `VillagerFollowMixin` and `VillagerPickupMixin`. Add explicit priority or consolidate. |
| High | **Network handler skips all mixin validation** — `handleFollowVillager` doesn't check shift, emerald, baby status, or max cap. Malicious client can toggle follow without emerald cost. |
| Medium | **Brain suppression is total** — villagers won't panic from raids/zombies, pick up items, or decay gossip while following. Consider selective suppression. |
| Medium | **`FollowPlayerGoal` uses goal-based AI on a brain-based mob** — works because brain is suppressed, but fragile if suppression is relaxed. |
| Medium | **`VillagerFollowPushMixin` targets `LivingEntity.doPush`** — applies instanceof check to every living entity in the game. Target `Villager.class` specifically. |
| Low | `saveReloadClear` gametest doesn't actually test save/reload — just tests UUID-based `stopFollowing`. |
| Low | No test for toggle-off interaction, baby villager rejection, or creative mode bypass. |
| Low | `distanceRelease` test relies on a single `tick()` call — fragile to brain suppression changes. |

### Feature Conflict Potential
- **Villager Pickup:** Well-integrated. Pickup correctly checks and stops follow state. Mixin ordering (H3) is the only concern.
- **Pathfinding Improvements:** Brain suppression means following villagers won't use mod-added fence gate/ladder behaviors.
- **Reputation:** Gossip memory decay pauses during brain suppression.
- **Trade Cycling:** No conflict — trading requires the villager to not be following.

---

## Cross-Cutting Concerns

### Thread Safety
Multiple systems use static mutable `HashMap`s without synchronization: `VillagerHeadTextures`, `VillagerNameManager.NAME_POOLS`, `FollowManager`, `ExclusiveTradesManager.INJECTED_OFFERS`, `VillagerHealingContext`. While Minecraft's single-threaded server tick model usually prevents issues, the integrated client's dual-thread environment and potential mod interactions make this a systemic risk. **Recommendation:** Audit all static mutable state and either use `ConcurrentHashMap`/`volatile` reference-swap or document thread-safety contracts.

### Mixin Ordering
Four separate `@Mixin(Villager.class)` classes inject into `mobInteract` at HEAD. Two inject into `Villager.setVillagerData`. Multiple inject into `WalkNodeEvaluator` methods. None have explicit priority ordering. **Recommendation:** Add `priority` to all mixins targeting the same class, or consolidate related mixins.

### Config Validation
`clamp()` only covers `healingMultiplier`. All other numeric config fields can be set to nonsensical values via hand-edited JSON. **Recommendation:** Expand `clamp()` to cover all fields with the same bounds the Cloth Config screen enforces.

### Duplicate Hash Function
`VillagerPickupHelper.offerIdentityHash()` and `OfferIdentityHash.compute()` produce different hashes for the same offer. This affects lock display on pickup items and lock tracking in trade cycling. **Recommendation:** Delete the duplicate immediately.

### Reputation Tier Definitions
Tier boundaries are defined independently in three places with no shared source of truth. **Recommendation:** Extract a `ReputationTier` enum as the single source of truth.
