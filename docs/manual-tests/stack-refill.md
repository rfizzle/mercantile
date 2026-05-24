# Manual Test Checklist — S-045 Hotbar Stack Refill

Covers `StackRefillHandler` behavior that JUnit cannot exercise (real ticking, inventory
SWAP packets, server sync). Run all sections before closing the sprint. Use a fresh
survival world unless a step says otherwise.

## Setup
- Launch dev client: `./gradlew runClient`
- Confirm `Mod Menu → Mercantile → Quality of Life → Enable Stack Refill` is `true`.

## Golden path

- [ ] **Block placement refill (main hand)**
  - Put 64 cobblestone in hotbar slot 1, another 64 in main inventory slot 18.
  - Select slot 1, place all 64 blocks.
  - Expected: slot 1 refills from slot 18 within ~1 tick of going empty.

- [ ] **Food consumption refill**
  - Put a stack of apples in hotbar slot 3, another stack in main inventory slot 22.
  - Eat until slot 3 empties.
  - Expected: slot 3 refills from slot 22.

- [ ] **Tool break refill — damaged tool**
  - Use `/give @s minecraft:iron_pickaxe{Damage:249}` (1 hit from breaking) into slot 1.
  - Put a spare iron pickaxe in slot 20.
  - Break a block — the held pickaxe shatters.
  - Expected: slot 1 refills with the spare from slot 20.

- [ ] **Tool break refill — enchanted spare**
  - Repeat the previous step but make the spare pickaxe enchanted (any enchantment).
  - Expected: still refills — damageable items match by type, not by enchants/name.

- [ ] **Offhand refill**
  - Put a stack of torches in the offhand, more torches in main inventory slot 19.
  - Place torches from offhand until empty.
  - Expected: offhand refills from slot 19.

## Edge cases (must NOT refill)

- [ ] **Creative mode no-op**
  - Switch to creative. Put a stack in slot 0, spare in slot 18.
  - Place blocks until slot 0 empties.
  - Expected: slot 0 stays empty (creative inventory handles its own restocking).
  - Verifies `player.getAbilities().instabuild` early-return.

- [ ] **Config disabled**
  - Open Mod Menu → set `Enable Stack Refill` to false → save.
  - Repeat the golden-path apple test.
  - Expected: no refill happens.

- [ ] **Manual slot switch (scroll / number keys)**
  - Hold a stack in slot 0, leave slot 5 empty. Scroll to slot 5.
  - Expected: no refill triggered — selected slot changed (prevSelectedSlot guard).

- [ ] **F-swap (swap hands)**
  - Hold a stack in main hand, leave offhand empty. Press F.
  - Expected: stack moves to offhand. Main hand goes empty. NO refill into main hand.
  - Verifies the F-swap guard: `isSameItemSameComponents(offhandStack, prevHeldSnapshot)`.

- [ ] **Source only in another hotbar slot**
  - Put a stack in hotbar slot 1, another stack in hotbar slot 7 (no copies in slots 9-35).
  - Eat slot 1 to empty.
  - Expected: no refill — handler never steals from other hotbar slots.

- [ ] **Screen-open guard**
  - Hold 1 apple. Open inventory (E). Drag the apple into the trash / drop area until
    main hand is empty. Close inventory.
  - Expected: no spurious refill while menu was open and no refill in the tick after
    closing the screen (prevScreenOpen guard).

## Expected refill (intentional)

- [ ] **Q-drop the last item**
  - Hold the last item in a stack with a spare in main inventory. Press Q.
  - Expected: refill DOES happen — Q-drop is intentionally treated like "stack ran out"
    per the comment in `StackRefillHandler#tick`.

## Multiplayer

- [ ] **Dedicated server, mod installed on both sides**
  - Run `./gradlew runServer`, connect with dev client.
  - Repeat the golden-path apple test.
  - Expected: refill works.

- [ ] **Server config sync overrides client**
  - On the dedicated server, set `enableStackRefill = false` in `config/mercantile.json`,
    restart. Client config still says `true`.
  - Repeat the golden-path apple test.
  - Expected: no refill — `ClientMercantileData.getServerConfig()` takes priority
    (handler `isEnabled()` branch).

- [ ] **Client-only install (vanilla server)**
  - Connect to a vanilla 1.21.1 server (no mod server-side) with the mod installed
    client-side only.
  - Repeat the golden-path apple test.
  - Expected: refill still works — SWAP click is vanilla, no server-side mod logic needed.

## Performance / sanity

- [ ] **No log spam while idle**
  - Stand idle in a world for 30 seconds with the mod loaded.
  - Tail `logs/latest.log`.
  - Expected: no per-tick warnings or repeated mod log lines from the handler.

- [ ] **Refill latency visually <1 tick**
  - During any golden-path test, watch the hotbar.
  - Expected: refill appears instantly — no visible empty-slot frame.
