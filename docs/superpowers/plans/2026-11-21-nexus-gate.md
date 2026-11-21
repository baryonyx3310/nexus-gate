# Nexus Gate Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the Nexus Gate NeoForge 1.21.1 mod: tech-styled teleport stations with anti-escape rules, XP cost, admin-set public stations, a Locator item and optional Xaero waypoints.

**Architecture:** All rules (charge time, combat lock, XP cost, request validation, per-tick charge state machine, arrival spot, locator arrow, list sorting) live in a **pure Java core** (`com.nexusgate.core`, no Minecraft imports) tested with plain JUnit. A thin Minecraft layer (blocks, SavedData, attachments, events, payloads, screens) gathers facts from the world, feeds them to the core and applies the result. Server is authoritative; the client only displays.

**Tech Stack:** Java 21, NeoForge 21.1.267, ModDevGradle 2.0.148 (template `NeoForgeMDKs/MDK-1.21.1-ModDevGradle`), Parchment mappings from the template, JUnit 5, Python 3 (stdlib only) for texture generation.

**Spec:** `docs/superpowers/specs/2026-11-21-nexus-gate-design.md` — read it before any task.

## Global Constraints

- Minecraft 1.21.1, NeoForge 21.1.x, Java 21. JDK 21 is at `C:\Program Files\Eclipse Adoptium\jdk-21.0.8.9-hotspot` (default `java` is 26; set `JAVA_HOME` to the 21 JDK when running Gradle, or rely on the toolchain).
- Mod id `nexusgate`, base package `com.nexusgate`, display name "Nexus Gate", project folder `nexus-gate/` inside the working directory. `git init` inside `nexus-gate/`.
- No required dependency besides NeoForge. Xaero's Minimap optional; the mod must load and work without it.
- **Nothing copied from Waystones** (code, textures, sounds, names). Original textures; vanilla sounds only.
- All player-facing text via translation keys in `assets/nexusgate/lang/pt_br.json` and `en_us.json` (both complete).
- Every rule value comes from server config `nexusgate-server.toml` with defaults: `chargeSeconds=5`, `perHostileSeconds=1`, `hostileRadius=8`, `maxChargeSeconds=12`, `crossDimensionExtraSeconds=3`, `combatLockSeconds=10`, `maxMoveDistance=1.5`, `stationUseRange=4`, `allowCrossDimension=true`, `xpCostEnabled=true`, `xpBaseCost=1`, `xpBlocksPerLevel=500`, `xpMaxSameDimension=10`, `xpCrossDimensionCost=5`, `publicStationPermissionLevel=2`.
- No teleport may start except from within `stationUseRange` of an existing station. No pocket teleport of any kind.
- 20 ticks = 1 second everywhere.

## Review Focus

1. **Station broken while a player has the list open, or while charging** — selecting it gives "Estação offline" / charge cancels with "Sincronização interrompida"; never a crash or a teleport into air. Pinned in Task 3 (`validator_rejectsMissingTarget`, `session_cancelsWhenTargetGone`).
2. **Player loses XP mid-charge** (enchanting, death of XP) — completion fails with "XP insuficiente", no negative levels. Pinned in Task 3 (`validator_completionRecheckFailsWithoutXp`).
3. **Player dies or disconnects while charging** — session discarded, no teleport of a dead/offline player. Pinned in Task 3 (`session_cancelsWhenPlayerGone`).
4. **Forged packet from a non-OP client** trying to set a station public, or a teleport request from far away / for an unactivated private station — server rejects. Pinned in Task 3 (`validator_rejectsTooFar`, `validator_rejectsUnknownPrivateTarget`) and Task 10 (`canSetPublic_requiresLevel`).
5. **Destination fully enclosed** (all four sides blocked) — player lands on top of the station, not inside blocks. Pinned in Task 4 (`arrival_fallsBackToTop`).

---

## File Structure

```
nexus-gate/
  build.gradle, settings.gradle, gradle.properties, gradlew(.bat), gradle/   (from MDK template)
  tools/gen_textures.py                         – writes the PNG textures
  src/main/java/com/nexusgate/
    NexusGate.java                              – @Mod entry; wires registries, config, payloads, events
    registry/ModBlocks.java, ModItems.java, ModBlockEntities.java, ModAttachments.java, ModComponents.java, ModCreativeTab.java
    config/NexusConfig.java                     – ModConfigSpec + toChargeSettings()/toXpSettings()
    core/   (pure, no net.minecraft imports)
      ChargeSettings.java, XpSettings.java      – records
      ChargeRules.java                          – chargeTicks, movedTooFar, progressBar
      CombatLock.java                           – remainingTicks
      XpCost.java                               – levels
      TeleportRequest.java, RequestValidator.java, RequestResult.java
      ChargeSession.java, TickInput.java, ChargeOutcome.java
      SafeArrival.java, LocatorArrow.java, StationSorter.java, StationEntry.java
    station/StationRecord.java, StationNetwork.java (SavedData), ActivatedStations.java (attachment)
    block/TeleportStationBlock.java, TeleportStationBlockEntity.java
    item/LocatorItem.java
    teleport/CombatEvents.java, ChargeManager.java, TeleportExecutor.java, StationService.java
    net/Payloads.java + one record per payload (see Task 8)
    command/NexusCommand.java
    client/SelectionScreen.java, RenameScreen.java, ClientPayloadHandlers.java, XaeroCompat.java
  src/main/resources/assets/nexusgate/{blockstates,models/block,models/item,textures/block,textures/item,lang}/
  src/main/resources/data/nexusgate/{recipe,loot_table/blocks}/, data/minecraft/tags/block/{mineable/pickaxe.json,needs_stone_tool.json}
  src/main/templates/META-INF/neoforge.mods.toml (from template)
  src/test/java/com/nexusgate/core/*Test.java
```

---

### Task 1: Project scaffold

**Files:**
- Create: `nexus-gate/` from the MDK template zip `https://github.com/NeoForgeMDKs/MDK-1.21.1-ModDevGradle/archive/refs/heads/main.zip` (download into an empty scratch dir, copy contents in; delete the template's example mod classes).
- Modify: `nexus-gate/gradle.properties` (`mod_id=nexusgate`, `mod_name=Nexus Gate`, `mod_group_id=com.nexusgate`, `mod_version=1.0.0`, `mod_license=All Rights Reserved` unless the user says otherwise, `mod_authors=Lucas Viana`, `neo_version=21.1.267`, description in pt from spec intro).
- Modify: `nexus-gate/build.gradle` — keep template; add `testImplementation 'org.junit.jupiter:junit-jupiter:5.10.2'`, `testRuntimeOnly 'org.junit.platform:junit-platform-launcher'`, `test { useJUnitPlatform() }`.
- Create: `nexus-gate/src/main/java/com/nexusgate/NexusGate.java` (`@Mod("nexusgate")`, `public static final String MOD_ID = "nexusgate"`, constructor `(IEventBus modBus, ModContainer container)`).
- Test: `nexus-gate/src/test/java/com/nexusgate/core/SmokeTest.java` (one `assertTrue(true)`; delete in Task 2).

- [ ] **Step 1:** Download/extract template, set properties, write `NexusGate`, add JUnit, `git init`, add `.gitignore` from template.
- [ ] **Step 2:** Run `./gradlew build` (with `JAVA_HOME` = JDK 21). Expected: `BUILD SUCCESSFUL`, `build/libs/nexusgate-1.0.0.jar` exists, SmokeTest ran.
- [ ] **Step 3:** Commit `chore: scaffold Nexus Gate NeoForge 1.21.1 project`.

---

### Task 2: Pure rules — charge time, combat lock, XP cost, progress bar

**Files:**
- Create: `core/ChargeSettings.java`, `core/XpSettings.java`, `core/ChargeRules.java`, `core/CombatLock.java`, `core/XpCost.java`
- Test: `src/test/java/com/nexusgate/core/ChargeRulesTest.java`, `CombatLockTest.java`, `XpCostTest.java`

**Interfaces:**
- Produces:
  - `record ChargeSettings(int chargeSeconds, int perHostileSeconds, int maxChargeSeconds, int crossDimensionExtraSeconds, double maxMoveDistance, int combatLockSeconds, double stationUseRange, boolean allowCrossDimension)` with `static ChargeSettings defaults()` (spec values).
  - `record XpSettings(boolean enabled, int baseCost, int blocksPerLevel, int maxSameDimension, int crossDimensionCost)` with `static XpSettings defaults()`.
  - `ChargeRules.chargeTicks(int hostileCount, boolean crossDimension, ChargeSettings s) -> int` — `min(base + hostiles*perHostile + (cross ? extra : 0), max) * 20`.
  - `ChargeRules.movedTooFar(double dx, double dy, double dz, double max) -> boolean` — strictly greater than `max`.
  - `ChargeRules.progressBar(int elapsedTicks, int totalTicks) -> String` — 10 cells `■`/`□`, floor, clamped.
  - `CombatLock.NEVER = Long.MIN_VALUE`; `CombatLock.remainingTicks(long lastCombatTick, long nowTick, int lockSeconds) -> int` (≥0).
  - `XpCost.levels(double distance, boolean crossDimension, XpSettings s) -> int` — disabled → 0; cross → `crossDimensionCost`; else `min(base + floor(distance / blocksPerLevel), maxSameDimension)`.

- [ ] **Step 1: Write failing tests** (defaults):
```java
assertEquals(100, ChargeRules.chargeTicks(0, false, d));   // 5 s
assertEquals(160, ChargeRules.chargeTicks(3, false, d));   // 8 s
assertEquals(240, ChargeRules.chargeTicks(10, false, d));  // capped 12 s
assertEquals(160, ChargeRules.chargeTicks(0, true, d));    // 5+3
assertEquals(240, ChargeRules.chargeTicks(5, true, d));    // 13 -> 12
assertFalse(ChargeRules.movedTooFar(1.5, 0, 0, 1.5));
assertTrue(ChargeRules.movedTooFar(1.2, 0, 1.0, 1.5));
assertEquals("[■■■■■□□□□□] 50%", ChargeRules.progressBar(50, 100));
assertEquals("[□□□□□□□□□□] 0%", ChargeRules.progressBar(0, 100));
assertEquals("[■■■■■■■■■■] 100%", ChargeRules.progressBar(150, 100));
assertEquals(0, CombatLock.remainingTicks(CombatLock.NEVER, 1000, 10));
assertEquals(150, CombatLock.remainingTicks(950, 1000, 10));
assertEquals(0, CombatLock.remainingTicks(800, 1000, 10));
assertEquals(1, XpCost.levels(0, false, x));
assertEquals(1, XpCost.levels(499, false, x));
assertEquals(2, XpCost.levels(500, false, x));
assertEquals(3, XpCost.levels(1250, false, x));
assertEquals(10, XpCost.levels(100_000, false, x));
assertEquals(5, XpCost.levels(10, true, x));
assertEquals(0, XpCost.levels(5000, false, new XpSettings(false,1,500,10,5)));
```
- [ ] **Step 2:** `./gradlew test` → FAIL (classes missing).
- [ ] **Step 3:** Implement the signatures above. Delete `SmokeTest`.
- [ ] **Step 4:** `./gradlew test` → PASS.
- [ ] **Step 5:** Commit `feat(core): charge, combat lock and XP cost rules`.

---

### Task 3: Pure request validation and charge state machine

**Files:**
- Create: `core/TeleportRequest.java`, `core/RequestResult.java`, `core/RequestValidator.java`, `core/TickInput.java`, `core/ChargeOutcome.java`, `core/ChargeSession.java`
- Test: `RequestValidatorTest.java`, `ChargeSessionTest.java`

**Interfaces:**
- Consumes: Task 2 records and rules.
- Produces:
  - `record TeleportRequest(boolean sourceExists, double distanceToSource, boolean targetExists, boolean targetKnown /* activated by player OR public */, boolean sameStation, boolean crossDimension, int combatRemainingTicks, int playerXpLevels, boolean creative, int xpCost)`
  - `enum RequestResult { OK, NOT_AT_STATION, TARGET_OFFLINE, TARGET_UNKNOWN, SAME_STATION, CROSS_DIMENSION_DISABLED, IN_COMBAT, NOT_ENOUGH_XP }` — each has `translationKey()` returning `"msg.nexusgate.<lowercase name>"`.
  - `RequestValidator.validateStart(TeleportRequest r, ChargeSettings s) -> RequestResult` — check order exactly as the enum order.
  - `RequestValidator.validateCompletion(int playerXpLevels, boolean creative, int xpCost) -> RequestResult` — `OK` or `NOT_ENOUGH_XP`.
  - `record TickInput(boolean playerPresent /* online and alive */, boolean tookDamage, double dx, double dy, double dz /* from start pos */, boolean sourceExists, boolean targetExists)`
  - `enum ChargeOutcome { CONTINUE, COMPLETE, CANCEL_DAMAGE, CANCEL_MOVED, CANCEL_STATION, CANCEL_GONE }`
  - `final class ChargeSession` — `ChargeSession(int totalTicks, double maxMove)`; `ChargeOutcome tick(TickInput in)`; `int elapsed()`; `int total()`. Check order per tick: playerPresent → damage → stations → moved → increment elapsed → `COMPLETE` when `elapsed >= total`.

- [ ] **Step 1: Write failing tests:**
  - `validator_okForValidRequest` (source exists, dist 2, target known/exists, combat 0, xp 30, cost 3 → OK)
  - `validator_rejectsTooFar` (dist 4.5 → NOT_AT_STATION; dist 4.0 → OK)
  - `validator_rejectsMissingTarget` → TARGET_OFFLINE
  - `validator_rejectsUnknownPrivateTarget` (targetKnown=false) → TARGET_UNKNOWN
  - `validator_rejectsSameStation` → SAME_STATION
  - `validator_rejectsCrossDimWhenDisabled` (allowCrossDimension=false) → CROSS_DIMENSION_DISABLED
  - `validator_rejectsInCombat` (combat 1) → IN_COMBAT
  - `validator_rejectsWithoutXp` (xp 2, cost 3) → NOT_ENOUGH_XP; `creative=true` → OK
  - `validator_completionRecheckFailsWithoutXp` (`validateCompletion(2,false,3)` → NOT_ENOUGH_XP; `(3,false,3)` → OK)
  - `session_completesAfterTotalTicks` (total 100: 99 ticks CONTINUE, 100th COMPLETE)
  - `session_cancelsOnDamage`, `session_cancelsWhenMoved` (dx 1.6), `session_cancelsWhenTargetGone`, `session_cancelsWhenSourceGone`, `session_cancelsWhenPlayerGone` (takes priority over damage)
- [ ] **Step 2:** `./gradlew test` → FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** `./gradlew test` → PASS.
- [ ] **Step 5:** Commit `feat(core): request validation and charge session`.

---

### Task 4: Pure helpers — arrival spot, locator arrow, list sorting

**Files:**
- Create: `core/SafeArrival.java`, `core/LocatorArrow.java`, `core/StationEntry.java`, `core/StationSorter.java`
- Test: `SafeArrivalTest.java`, `LocatorArrowTest.java`, `StationSorterTest.java`

**Interfaces:**
- Produces:
  - `SafeArrival.pick(IntBinaryPredicate free /* (offsetX, offsetZ) -> both blocks at that column y and y+1 are passable and block below is solid */) -> int[] {ox, oz, oy}` — tries in order south (0,1), north (0,-1), east (1,0), west (-1,0) with `oy=0`; if none free returns `{0, 0, 2}` (on top of the 2-tall station). Define `@FunctionalInterface interface IntBinaryPredicate { boolean test(int x, int z); }` inside `SafeArrival`.
  - `LocatorArrow.arrow(double dx, double dz, float yawDegrees) -> String` — Minecraft yaw (0 = facing +Z, 90 = facing −X); returns one of `↑ ↗ → ↘ ↓ ↙ ← ↖` for the target relative to view, 45° sectors centred on each arrow.
  - `record StationEntry(String id, String name, String dimension, boolean isPublic, double distance /* -1 if other dim */, int xpCost, boolean affordable)`
  - `StationSorter.sort(List<StationEntry> in, String currentDimension) -> List<StationEntry>` — current dimension first, then others; inside each group case-insensitive name order.

- [ ] **Step 1: Write failing tests:**
  - `arrival_prefersSouth` (all free → `{0,1,0}`), `arrival_skipsBlocked` (only west free → `{-1,0,0}`), `arrival_fallsBackToTop` (none free → `{0,0,2}`)
  - `arrow_straightAhead` (yaw 0, dz +10 → `↑`), `arrow_eastWhenFacingSouth` (yaw 0, dx +10 → `←`), `arrow_eastWhenFacingNorth` (yaw 180, dx +10 → `→`), `arrow_behind` (yaw 0, dz −10 → `↓`), `arrow_diagonal` (yaw 180, dx +10, dz −10 → `↗`), `arrow_negativeYaw` (yaw −180 same as 180)
  - `sorter_currentDimFirstThenAlpha` (input: `[Zeta/nether, beta/overworld, Alpha/overworld]`, current overworld → `Alpha, beta, Zeta`)
- [ ] **Step 2:** `./gradlew test` → FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** `./gradlew test` → PASS.
- [ ] **Step 5:** Commit `feat(core): arrival, locator arrow and sorting helpers`.

---

### Task 5: Config

**Files:**
- Create: `config/NexusConfig.java`
- Modify: `NexusGate.java` — `container.registerConfig(ModConfig.Type.SERVER, NexusConfig.SPEC)`.

**Interfaces:**
- Produces: `NexusConfig.SPEC`; `NexusConfig.chargeSettings() -> ChargeSettings`; `NexusConfig.xpSettings() -> XpSettings`; `NexusConfig.hostileRadius() -> int`; `NexusConfig.publicPermissionLevel() -> int`. Keys and defaults exactly as Global Constraints, with comments in English describing each (and ranges: seconds 0–60, radius 0–32, permission 0–4).

- [ ] **Step 1:** Implement.
- [ ] **Step 2:** `./gradlew build` → SUCCESS.
- [ ] **Step 3:** Commit `feat: server config`.

---

### Task 6: Station network persistence and player activations

**Files:**
- Create: `station/StationRecord.java`, `station/StationNetwork.java`, `station/ActivatedStations.java`, `registry/ModAttachments.java`
- Modify: `NexusGate.java` (register attachment registry)
- Test: `src/test/java/com/nexusgate/station/StationRecordTest.java`

**Interfaces:**
- Produces:
  - `record StationRecord(UUID id, String name, ResourceKey<Level> dimension, BlockPos pos, UUID owner, boolean isPublic)` with `CompoundTag save()`, `static StationRecord load(CompoundTag)`, `withName(String)`, `withPublic(boolean)`.
  - `class StationNetwork extends SavedData` — `static StationNetwork get(MinecraftServer)` (stored on overworld, file id `nexusgate_stations`); `add(StationRecord)`, `remove(UUID)`, `Optional<StationRecord> get(UUID)`, `Optional<StationRecord> at(ResourceKey<Level>, BlockPos)`, `Collection<StationRecord> all()`, `update(StationRecord)`; every mutation calls `setDirty()`.
  - `ActivatedStations` — attachment type `ModAttachments.ACTIVATED` holding a `Set<UUID>` with a Codec (list of UUID strings), `.copyOnDeath()`; static helpers `isActivated(ServerPlayer, UUID)`, `activate(ServerPlayer, UUID)`, `Set<UUID> of(ServerPlayer)`.
- `StationRecord.dimension` saved as the dimension id string.

- [ ] **Step 1: Write failing test** `record_roundTripsThroughNbt` — build a record (`Level.NETHER`, pos 10/64/-20, public=true), `load(save())` equals original. (If `ResourceKey` creation needs bootstrap, enable MDG unit tests: `neoForge { unitTest { enable(); testedMod = mods.nexusgate } }` and add `testImplementation 'net.neoforged:testframework:${neo_version}'`.)
- [ ] **Step 2:** `./gradlew test` → FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** `./gradlew test` → PASS.
- [ ] **Step 5:** Commit `feat: station network SavedData and player activations`.

---

### Task 7: Teleport Station block, Locator item registration, assets and data

**Files:**
- Create: `block/TeleportStationBlock.java`, `block/TeleportStationBlockEntity.java`, `item/LocatorItem.java` (registration only; behavior in Task 11), `registry/ModBlocks.java`, `ModItems.java`, `ModBlockEntities.java`, `ModComponents.java`, `ModCreativeTab.java`
- Create: `tools/gen_textures.py` → `textures/block/station_base.png`, `station_core.png`, `station_top.png`, `station_side.png`, `textures/item/locator.png` (16×16, palette: metal `#2B2F36`/`#3C424B`/`#555D69`, rivets `#7A8494`, cyan core `#00E5FF`/`#7FF6FF`, dark slots `#14171C`). Pure stdlib PNG writer (zlib + struct).
- Create: blockstate `teleport_station.json` (variants by `half=lower|upper` × `facing`), models `block/teleport_station_lower.json`, `block/teleport_station_upper.json` (element-based: lower = wide base + column with core; upper = column + ring/antenna), `item/teleport_station.json`, `item/locator.json`.
- Create: `data/nexusgate/loot_table/blocks/teleport_station.json` (drops only for `half=lower`), recipes `teleport_station.json` (pattern from spec: `IGI/QEQ/IRI`) and `locator.json` (pattern ` I `/`ICI`/`RIQ`: I = iron ingot, C = compass, R = redstone, Q = quartz), tags `mineable/pickaxe`, `needs_stone_tool`.
- Create: `lang/pt_br.json`, `lang/en_us.json` — all keys used by any task (block, item, creative tab, every `msg.nexusgate.*`, screen strings, `station.nexusgate.default_name` = `"Estação %s, %s"` / `"Station %s, %s"`).

**Interfaces:**
- Produces:
  - `TeleportStationBlock` — properties `HALF` (DoubleBlockHalf), `FACING` (horizontal); strength 5.0/6.0, `requiresCorrectToolForDrops`, `PushReaction.BLOCK`, light level 7 on lower, `noOcclusion`. Placing places both halves (fails if upper space is not replaceable); breaking either half removes both. Block entity only on lower half. `animateTick` spawns occasional `ParticleTypes.ELECTRIC_SPARK`. `useWithoutItem` and `onRemove` delegate to `StationService` (Task 8/9) — leave TODO-free stubs calling `StationService.onUse(...)` / `StationService.onRemoved(...)` which Task 8 creates; in this task create `StationService` with empty bodies.
  - `TeleportStationBlockEntity` — field `UUID stationId` saved under `"StationId"`; `getStationId()`, `setStationId(UUID)`.
  - `ModComponents.LINKED_STATION` — `DataComponentType<UUID>` (persistent, `UUIDUtil.CODEC`, network `UUIDUtil.STREAM_CODEC`).
  - Item ids: `nexusgate:teleport_station`, `nexusgate:locator`; creative tab `nexusgate:main`.

- [ ] **Step 1:** Write `gen_textures.py`, run `python tools/gen_textures.py`; check the 5 PNGs exist and are 16×16.
- [ ] **Step 2:** Implement classes, JSON assets/data, both lang files.
- [ ] **Step 3:** Validate every JSON parses: `python -c "import json,glob;[json.load(open(f,encoding='utf-8')) for f in glob.glob('src/main/resources/**/*.json',recursive=True)]"` → no error. Also check pt_br and en_us have identical key sets.
- [ ] **Step 4:** `./gradlew build` → SUCCESS.
- [ ] **Step 5:** Commit `feat: teleport station block, locator item and assets`.

---

### Task 8: Station lifecycle, payloads and screens

**Files:**
- Create: `teleport/StationService.java` (fill in), `net/Payloads.java`, `net/OpenSelectionPayload.java`, `net/SelectStationPayload.java`, `net/OpenRenamePayload.java`, `net/RenameStationPayload.java`, `net/SetPublicPayload.java`, `net/XaeroWaypointPayload.java`, `client/SelectionScreen.java`, `client/RenameScreen.java`, `client/ClientPayloadHandlers.java`
- Modify: `NexusGate.java` (register `RegisterPayloadHandlersEvent` listener `Payloads::register`, protocol version `"1"`)

**Interfaces:**
- Consumes: `StationNetwork`, `ActivatedStations`, `StationSorter`, `StationEntry`, `XpCost`, `CombatLock`, `NexusConfig`, `ChargeManager.start(...)` (Task 9 — in this task `SelectStationPayload` handler calls `ChargeManager.requestStart(ServerPlayer, UUID sourceId, UUID targetId)`; create `ChargeManager` with that method as a stub that only validates via `RequestValidator` and sends the result message).
- Produces:
  - `OpenSelectionPayload(String sourceId, String sourceName, boolean canRename, boolean isOp, boolean sourcePublic, int combatRemainingTicks, List<StationEntry> entries)` S2C, with `StreamCodec`.
  - `SelectStationPayload(UUID source, UUID target)` C2S; `OpenRenamePayload(UUID station, String currentName)` S2C; `RenameStationPayload(UUID station, String name)` C2S (name trimmed, 1–32 chars, only owner or OP); `SetPublicPayload(UUID station, boolean value)` C2S; `XaeroWaypointPayload(String name, int x, int y, int z, String dimension)` S2C.
  - `StationService.onPlaced(ServerLevel, BlockPos, ServerPlayer)`: new UUID, default name from `station.nexusgate.default_name` resolved with x/z, add to network, set BE id, activate for placer, send `OpenRenamePayload`.
  - `StationService.onUse(ServerLevel, BlockPos, ServerPlayer) -> InteractionResult`: unknown & not public → activate, sound `BEACON_ACTIVATE`, message `msg.nexusgate.synced`, send `XaeroWaypointPayload`; else send `OpenSelectionPayload` built from all activated + public stations except current, dropping ids whose record no longer exists, sorted with `StationSorter`.
  - `StationService.onRemoved(ServerLevel, BlockPos)`: remove record.
  - `SelectionScreen`: terminal look (black `#0A0E12` panel, 1px cyan `#00E5FF` border, header `> NEXUS GATE // <name>`), scrollable list rows `name  [PÚBLICA]  dimension  distance m  N XP`; public rows gold `#FFC94A`; unaffordable rows red `#FF5555` and disabled; when `combatRemainingTicks > 0` all rows disabled and header shows `INTERFERÊNCIA DETECTADA — <s>s` (counts down client-side). Buttons: `Renomear` (if `canRename`) and `Tornar pública/privada` (if `isOp`). Clicking a row sends `SelectStationPayload` and closes.
  - `RenameScreen`: EditBox max 32 + confirm; sends `RenameStationPayload`.
  - All client classes referenced only via `ClientPayloadHandlers` registered for `Dist.CLIENT`.

- [ ] **Step 1:** Implement.
- [ ] **Step 2:** `./gradlew build` → SUCCESS.
- [ ] **Step 3:** `./gradlew runClient`, single-player creative: place station → rename screen opens; place second, right-click → list shows first one with distance/XP. Note result in commit message body.
- [ ] **Step 4:** Commit `feat: station lifecycle, network payloads and terminal screens`.

---

### Task 9: Charging, combat tracking and teleport execution

**Files:**
- Create: `teleport/CombatEvents.java`, `teleport/TeleportExecutor.java`
- Modify: `teleport/ChargeManager.java` (full implementation), `NexusGate.java` (register game-bus listeners)

**Interfaces:**
- Consumes: `RequestValidator`, `ChargeSession`, `TickInput`, `ChargeRules`, `CombatLock`, `XpCost`, `SafeArrival`, `StationNetwork`, `NexusConfig`.
- Produces:
  - `CombatEvents`: on `LivingDamageEvent.Post` record `lastCombatTick` (server game time) for victim if `ServerPlayer`, and for `source.getEntity()` if `ServerPlayer` and victim is `LivingEntity`; also flags `tookDamage` for the victim's active session. Store `Map<UUID, Long>`; `static long lastCombat(UUID)`; cleared on logout.
  - `ChargeManager.requestStart(ServerPlayer, UUID sourceId, UUID targetId)`: builds `TeleportRequest` (distance from player to source lower-half centre; `targetKnown` = activated or public; hostile count = `Enemy` instances within `hostileRadius`; xp cost from `XpCost`), `validateStart`; on failure send `translationKey()` to action bar; on OK create `ChargeSession(chargeTicks, maxMove)`, remember start pos, play `RESPAWN_ANCHOR_CHARGE`. A new request replaces an existing session.
  - On `ServerTickEvent.Post`: for each session build `TickInput` and `tick`. `CONTINUE` → action bar `ChargeRules.progressBar`, rising spiral of `ELECTRIC_SPARK` + `END_ROD` via `ServerLevel.sendParticles` (visible to others), `BEACON_AMBIENT` every 20 ticks with pitch rising 0.5→2.0. `COMPLETE` → `validateCompletion`; on OK `TeleportExecutor.teleport`, deduct levels (`player.giveExperienceLevels(-cost)`, skip creative). Any `CANCEL_*` → `BEACON_DEACTIVATE`, message `msg.nexusgate.interrupted`; `CANCEL_DAMAGE` needs no extra code (CombatEvents already set the lock).
  - `TeleportExecutor.teleport(ServerPlayer, StationRecord target) -> boolean`: resolve target `ServerLevel` (missing dimension → false, `msg.nexusgate.target_offline`), add a 1-tick-delay `TicketType.PORTAL`-style ticket on target chunk, dismount (`stopRiding`), choose spot via `SafeArrival.pick` relative to target lower pos, `player.teleportTo(level, x+0.5, y, z+0.5, yaw, pitch)`, arrival particles `REVERSE_PORTAL` + `ENDERMAN_TELEPORT` sound.

- [ ] **Step 1:** Implement.
- [ ] **Step 2:** `./gradlew build` → SUCCESS; `./gradlew test` still PASS.
- [ ] **Step 3:** `runClient` survival: teleport between two stations (5 s bar, XP deducted); hit by zombie during charge → cancelled and next attempt shows interference countdown; walk 2 blocks → cancelled; break target during charge → cancelled.
- [ ] **Step 4:** Commit `feat: anti-escape charging, combat lock and teleport execution`.

---

### Task 10: Public stations (admin)

**Files:**
- Create: `command/NexusCommand.java`, `core/Permissions.java`, test `PermissionsTest.java`
- Modify: `StationService.java` (SetPublic handler, break protection), `NexusGate.java` (`RegisterCommandsEvent`)

**Interfaces:**
- Produces:
  - `Permissions.canSetPublic(int playerPermissionLevel, int required) -> boolean` (`>=`).
  - `SetPublicPayload` handler: re-check `canSetPublic(player.permissionLevel, NexusConfig.publicPermissionLevel())`, else ignore + log warn; on success update record, message `msg.nexusgate.now_public` / `now_private`.
  - `/nexusgate public <true|false>` (requires level from config): ray-trace 6 blocks for a station, apply same update; else `msg.nexusgate.no_station_in_sight`.
  - Break protection: in `TeleportStationBlock.playerWillDestroy` / `onDestroyedByPlayer`, if record public and breaker lacks permission and not creative-OP → cancel, message `msg.nexusgate.protected`. Explosions do not destroy public stations (`getExplosionResistance` → 3_600_000 when public, via BE flag synced from record).

- [ ] **Step 1: Write failing test** `canSetPublic_requiresLevel` (`(0,2)` false, `(2,2)` true, `(4,2)` true).
- [ ] **Step 2:** `./gradlew test` → FAIL. **Step 3:** Implement. **Step 4:** `./gradlew test` → PASS.
- [ ] **Step 5:** `runClient` (LAN/cheats on): mark public via button and command; second non-OP check is manual for the user (note in final checklist).
- [ ] **Step 6:** Commit `feat: admin-only public stations`.

---

### Task 11: Locator behavior

**Files:**
- Modify: `item/LocatorItem.java`, `StationService.java`

**Interfaces:**
- Consumes: `ModComponents.LINKED_STATION`, `LocatorArrow`, `StationNetwork`, `ActivatedStations`.
- Produces:
  - Sneak + right-click on a station (`useOn`): if activated or public → set component, message `msg.nexusgate.locator_linked` with name; else `msg.nexusgate.target_unknown`. Sneak-use must not open the selection screen (block `useWithoutItem` ignores when player sneaking with a Locator).
  - `inventoryTick` (server, every 10 ticks, when held in main/off hand): linked record missing → clear component, action bar `msg.nexusgate.signal_lost`; other dimension → `<name> — outra dimensão` key; else `→ <name>  <dist> m  <arrow>` using `LocatorArrow.arrow(dx, dz, player.getYRot())`.
  - Item tooltip shows linked station name (store name lookup client-side not needed: show "Vinculado" only).
  - No teleport behavior of any kind.

- [ ] **Step 1:** Implement. **Step 2:** `./gradlew build` → SUCCESS.
- [ ] **Step 3:** `runClient`: link, walk around, check arrow changes; break station → "Sinal perdido".
- [ ] **Step 4:** Commit `feat: locator item`.

---

### Task 12: Optional Xaero's Minimap waypoint

**Files:**
- Create: `client/XaeroCompat.java`
- Modify: `client/ClientPayloadHandlers.java`, `src/main/templates/META-INF/neoforge.mods.toml` (optional dependency `xaerominimap`, `type="optional"`)

**Interfaces:**
- Consumes: `XaeroWaypointPayload`.
- Produces: `XaeroCompat.addWaypoint(String name, int x, int y, int z)` — called only if `ModList.get().isLoaded("xaerominimap")` and payload dimension equals the client's current dimension. Implemented by **reflection** (no compile dependency), mirroring the call chain used by the reference addon: `Minecraft.getInstance().getConnection()` cast to `xaero.common.core.IXaeroMinimapClientPlayNetHandler` → `getXaero_minimapSession()` → `getWaypointsManager()` → `getWaypoints()` → `getList()`; skip if a waypoint already has the same x/y/z; else `new xaero.common.minimap.waypoints.Waypoint(x, y, z, name, name.substring(0,1), 11 /* cyan-ish colour index */, 0, false)` add to list; then `XaeroMinimap.instance.getSettings().saveWaypoints(manager.getCurrentWorld())`. Whole body in `try { } catch (Throwable t)` that logs a single warning and disables further attempts — Xaero API changes must never crash the game.

- [ ] **Step 1:** Implement. **Step 2:** `./gradlew build` → SUCCESS.
- [ ] **Step 3:** Run client **without** Xaero: activating a station works, no errors in log. (Testing with Xaero installed is on the user's checklist; if reflection fails on their Xaero version, the warning tells us and the feature stays inert.)
- [ ] **Step 4:** Commit `feat: optional Xaero minimap waypoint on activation`.

---

### Task 13: Release build and handoff

**Files:**
- Create: `nexus-gate/README.md` (pt-BR: what the mod does, rules, config keys, admin command, build instructions)
- Output: copy `nexus-gate/build/libs/nexusgate-1.0.0.jar` to the working directory root.

- [ ] **Step 1:** `./gradlew clean build` → SUCCESS, all tests PASS (paste summary).
- [ ] **Step 2:** Verify the jar contains `META-INF/neoforge.mods.toml` with `modId="nexusgate"`, both lang files, 5 textures, recipes; and contains **no** `net/blay09` or `waystones` paths (`unzip -l | grep -i -E "blay09|waystones"` → empty).
- [ ] **Step 3:** Copy jar; write README with a manual test checklist for the user: non-OP cannot set public / cannot break public station; Xaero waypoint appears; cross-dimension teleport costs 5 XP and charges 8 s.
- [ ] **Step 4:** Commit `docs: README and release build`.
