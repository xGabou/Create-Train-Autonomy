# Create Train Automation

A general purpose train control and automation framework for **Minecraft 1.21.1, NeoForge, and Create 6.0.10**. Mining is the first registered automation profile; the controller, movement, inventory, schedules, events, and ownership APIs contain no mining rules.

The existing `createtrainmining` namespace is retained for compatibility. The mod and block are displayed as **Create Train Automation** and **Train Automation Controller**.

## Build and run

Java 21 is required; Gradle uses the configured Java toolchain.

```powershell
.\gradlew.bat build
.\gradlew.bat runClient
.\gradlew.bat runServer
.\gradlew.bat runGameTestServer
```

The default development runtime includes Create and its normal libraries. Addons are optional, with the supplied artifacts selectable for development:

```powershell
.\gradlew.bat runClient -PwithComputerCraft=true -PwithAdditionalLogistics=true -PwithRailwaysAdditions=true
.\gradlew.bat runGameTestServer -PwithComputerCraft=true -PwithAdditionalLogistics=true -PwithRailwaysAdditions=true
```

Create is resolved from its official Maven repository as `create-1.21.1:6.0.10-280:slim`, the 6.0.10 build at release commit `ac0c444d9828da3453ae8cc65338e8de063286fb`. It is not installed twice through CurseMaven. Optional runtimes use CC:Tweaked `maven.modrinth:gu7yAYhd:1ewzHZYg`, Additional Logistics `7460280`, and Railways Additions `8407753`. CC's APIs are compile-only; core train lookup, inventory and schedules require neither CC nor Additional Logistics.

The distributable jar is in `build/libs`. Development GameTests and their empty structure are excluded from the jar.

### Moving drill fix

Moving drills can break blocks with no collision shape, including cobwebs, kelp, seagrass, vines, sculk veins and short grass ([Create #8448](https://github.com/Creators-of-Create/Create/issues/8448)). This applies to all moving Create contraptions, including trains without an automation controller. Create still handles breaking progress, drops, inventory collection and fluid replacement; air, fluids, tracks, unbreakable blocks and the `create:non_breakable` tag remain protected.

### Create Threaded Trains compatibility

When Create Threaded Trains 1.0.0 is installed, an optional compatibility hook runs its railway task immediately on the server thread. Railway ticks still run once per server tick, and its normal task/future lifecycle is preserved. This prevents automation callbacks, slope checks, and tool controls from accessing world state on the train worker. It disables parallel ticking for the entire railway network while both mods are installed, including trains without a controller; this avoids races when a controller selects or claims a train during the server tick. A one-time warning in the server log makes the fallback visible. Without the addon, no compatibility hook is loaded and Create's normal ticking is unchanged.

To test with the addon in an isolated development world:

```powershell
.\gradlew.bat runGameTestServer -PwithThreadedTrains=true -PgameTestDirectory=build/gametest-threaded-trains
```

## Publishing

ModPublisher is configured for Modrinth and CurseForge. Fill the project IDs in `gradle.properties`, set `MODRINTH_TOKEN` and `CURSEFORGE_TOKEN` in your environment, and edit `CHANGELOG.md` for the release. Upload previews are enabled by default. See [the publishing guide](docs/publishing.md) for build, preview, and upload commands.

## Controller block

Craft the controller using copper, redstone, two precision mechanisms, and a Create track station, or find it in the Redstone creative tab. Right-click it to open the controller menu.

The controller uses Create's GUI framework, icon buttons, scroll selectors, and status indicators. Select a discovered train and an automation profile by scrolling or clicking their fields. The train tooltip shows its full name and UUID, and the train strip uses Create's actual locomotive and carriage icons.

The train panel always shows speed, station, movement ownership, and automation state. Profile settings are generated from the schema and page automatically when needed. Station fields select known stations; the pencil button allows a name or schedule filter to be entered manually. Percentages use Create scroll inputs, with Shift scrolling faster. The gear opens advanced settings, including drive backend and deployer control. The lever opens manual forward/backward driving, stopping, and station navigation. Every action icon has a tooltip. The bottom strip shows inventory and status, or the full error on hover.

The block is a horizontally facing railway cabinet with original 16x16 steel/brass textures. Its control face points toward the placing player. A gray, green, yellow, or red lamp reflects inactive, moving, waiting, or error state using block models; no block entity renderer is needed. Hold Shift over the item for Create-style usage information.

See [the presentation plan and visual checks](docs/controller-presentation.md). The opt-in development preview renders the real screen and block models with representative status fixtures, without opening a world or issuing train commands:

```powershell
.\gradlew.bat runClient -PvisualPreview=true
```

It captures Auto/2/3/4, long names, errors, manual/advanced settings, extra profile pages, and all cabinet variants to `build/reference/presentation`, then closes the client. Preview code is excluded from the distributable jar.

Direct speed is a fraction from **0 to 1** of Create's configured manual maximum, subject to throttle, turn limits, reverse limits and safety braking. Observed train speed is reported in **blocks per tick**. Directions are relative to the assembled train; they do not represent a profile's outbound or return semantics.

Stop an active profile before using external low-level movement controls or changing its settings. Profile callbacks themselves operate through the same `TrainController` facade.

## Remote controller commands

Operators (permission level 2) can give a controller a unique station name and control it from anywhere, including another dimension. Look at the controller within eight blocks and run:

```mcfunction
/trainautomation name "Quarry One"
/trainautomation list
/trainautomation station "Quarry One" status
/trainautomation station "Quarry One" start
/trainautomation station "Quarry One" retry
/trainautomation station "Quarry One" cancel
/trainautomation station "Quarry One" return
```

`start` starts the configured profile. `retry` restarts it with fresh recovery counters while keeping its train and settings. `cancel` (or `stop`) stops automation and movement. `return` requires running automation; the mining profile immediately returns to its configured return station, unloads normally, then stays stopped at base even with automatic resume enabled. These commands retain normal train ownership, conductor, route, signal, and player-control checks.

Names identify controller blocks; they do not rename Create track stations or change the mining return destination. Names support spaces when quoted and appear in command suggestions. Naming the same block again renames it; duplicate names on other controllers are rejected. Names survive world restarts and inactive chunks are loaded on demand. Breaking a controller removes its name; a stale name cannot control a replacement block.

For a console or explicit coordinates in the command's dimension, use `/trainautomation name "Quarry One" at <x> <y> <z>` with a loaded controller. Use `/execute in <dimension> run ...` to name a controller in another dimension.

## Core API

- `api/AutomationProfile`, `AutomationContext`, `ConfigurationField`: independently instantiated profile lifecycle, callbacks, configuration and persistent state.
- `api/DriveRequest`, `DriveDirection`, `TrainDriveBackend`: extensible movement requests and the drive adapter SPI.
- `core/ManagedTrain`: a UUID-based view that resolves the current Create object rather than caching its motion state.
- `core/TrainController`: selection, direct controls, schedules, inventory, actors, state, errors and profile lifecycle. Mutations run on the server thread.
- `core/TrainAutomationManager`, `TrainControlManager`, `TrainControlSavedData`: server-scoped controllers and durable train ownership leases.
- `core/TrainInventoryView`: read-only cargo snapshots from Create mounted storage; usage is the mean occupied fraction of usable slots. A full non-stackable item, 16-stack item and 64-stack item each fill a slot. Empty slot capacity is an estimate.
- `core/TrainScheduleController`, `api/TrainSchedule`: typed schedules converted to Create schedules inside the adapter. Station matching follows Create's schedule filter rules. Normal conductor and route requirements remain in force.
- `core/TrainActorController`: loaded actor discovery and activation using `canBeDisabledVia`, `setActorsActive`, `disabledActors`, and Create's client synchronization packet. Unsupported actors are excluded.
- `core/TrainDiscovery`: train discovery and station UUID/name/present-train monitoring.
- `core/TrainConditions`, `TrainActions`: small reusable Java conditions and actions, with no visual scripting engine.
- `core/IntegrationRegistry`, `TrainMovementIntegrations`: optional services and hooks that suspend competing addon movement at ownership handoffs.

Addons register factories during mod initialization:

```java
AutomationProfileRegistry.register("myaddon:patrol", PatrolProfile::new);
DriveBackendRegistry.register("myaddon:drive", MyDriveBackend::new);
```

Each controller receives its own profile and backend instance. Profiles access `AutomationContext` and `TrainController`; they do not receive a raw Create `Train`. Raw train access is confined to the adapter SPI and implementation. Profile state is serialized through `serializeState`/`deserializeState`; profile-owned `persistentData` changes should call `context.markDirty()`.

`AutomationEvent` is published on the NeoForge event bus for selection, direct drive start/stop, schedule start, arrivals, cargo changes, profile state changes and errors. Cargo change detection uses Create's storage version, with a periodic content check for external storage handlers.

## Ownership and recovery

A controller must acquire the train's lease before changing its movement or actors. A second controller receives a clear conflict error. Player Train Controls take priority and stop automation.

Entering direct control pauses ScheduleRuntime and cancels its navigation/reservations. Entering schedule control removes direct driving, resets manual steering, manual tick and stall/movement state, suspends addon cruise, and gives ScheduleRuntime the new schedule. Paused schedules can be resumed through the facade.

Controller NBT saves its identity, selected train UUID, profile ID, validated configuration, backend ID, enabled state, profile state/data and last error. Chunk unloading suspends movement and releases the live lease; enabled profiles reconstruct their intent when reloaded. Removing a controller stops its train. Ownership is also saved at world level so a train cannot resume stale navigation ahead of its controller after a restart. Missing trains, invalid graphs, derailment and ownership conflicts stop automation with an error.

Active controller blocks keep their own chunk loaded and ticking, so walking away does not suspend automation. The controller also keeps a moving area around every carriage loaded and entity-ticking, including carriage anchors, bogeys, and loaded contraption bounds with a one-chunk margin. This keeps drills and deployers operating when the train is far from players, rather than only moving its virtual track position. New chunks load before old chunks are released; the entire railway route is not kept loaded. Each controller owns separate tickets. Enabled profiles retain the controller ticket across server shutdowns and resume after restart, rebuilding the train footprint from its current position; stale tickets for missing or inactive blocks and old train footprints are removed on world load. Stopping, player takeover, errors, and block removal release tickets. Manual direct driving and active station schedules also keep the controller and train loaded while running, but manual requests stop on shutdown and do not automatically restart. The server must be running and unpaused.

## Mining profile

Only `automation/mining` contains the mining workflow:

`STOPPED -> MINING -> RETURNING -> UNLOADING -> RESUMING -> MINING`

Select a train with cargo storage and a conductor, choose `mining`, and configure its return station. The profile drives stationlessly in its configured outbound direction. At the return threshold it switches to a normal Create station schedule. At the station it waits until cargo reaches the unload threshold **or** cargo remains unchanged for five minutes (6,000 server ticks), then resumes outbound direct motion when automatic resume is enabled. This allows rails and other retained supplies to stay aboard. Any change to cargo resets the inactivity timer; its deadline survives controller/world reloads. With automatic resume disabled, unloading completion stops automation instead.

Configuration keys are `return_station`, `return_threshold`, `unload_threshold`, `mining_speed`, `outbound_direction`, `automatic_resume`, `pause_tools_when_not_mining`, `track_deployer_control`, `pause_tools_on_slopes`, `slope_lookahead`, `pause_deployers_near_stations`, `station_radius`, and `clear_lava`. Thresholds and speed use fractions. The unload threshold must be below a positive return threshold. The return station is not hardcoded.

**Pause tools when not mining** is enabled by default in the profile settings. It temporarily disables drills and deployers while returning, unloading, and waiting to resume. Portable storage interfaces remain active. Tools resume when mining restarts, and manually disabled tools keep their saved settings. Stopping automation releases these temporary overrides. Arrival waits for a stopped train with finished navigation; holding at the station preserves Create's portable storage interface connection so the base can extract cargo.

The optional deployer setting controls the Create deployer actor type on loaded carriages, including all deployers that support that actor filter; it does not infer which deployers are holding track. It defaults to disabled. The profile remembers the previous aggregate enabled state for stop cleanup. Contraption-wide actor disabling remains authoritative. The profile waits at the current track end so actors can extend the route.

If active mining makes less than a quarter block of position progress for 5 seconds, the controller shows **Stuck: backing up**, pauses drills/deployers, retreats about 10 blocks in the opposite direction, and retries mining automatically. This works in either outbound direction and measures actual train position, even if the train reports speed while stuck. Signal waits, unloaded chunks, slope tool pauses, and a zero mining speed do not trigger recovery. Recovery still obeys signals and track safety. If the retreat itself cannot make progress for 5 seconds, mining retries from the available position. Full cargo takes priority and returns to the station. A controller reload preserves the remaining retreat distance; stopping or player takeover cancels recovery and restores tool settings.

On the fourth consecutive mining jam, the controller sends one chat warning, stops movement and pauses tools, then returns to the configured station after 10 minutes of running server game time (12,000 ticks). Full cargo can trigger an earlier return. Replaying the retreat does not reset the jam count; advancing beyond the obstruction does. The count and return deadline survive controller/world reloads. After this return and unloading, automation stops even if automatic resume is enabled; restart it manually at base after addressing the obstruction.

**Pause Tools on Slopes** is enabled by default in the mining profile's advanced settings (gear button). It temporarily pauses all Create deployers and mechanical drills when sloped track is ahead or under any carriage, while continuing to drive. **Slope Lookahead** defaults to 8 blocks and accepts 2–64 blocks, measured beyond the carriage/tool reach. Scouts sample track elevation, including curved slopes, in either mining direction. Tools resume after the rear carriage and its overhanging tools clear the slope. The controller shows **Slope: tools paused** during the crossing. No additional Contraption Controls block is required.

Slope pauses use Create's actor-disable behavior with separate client synchronization. They preserve saved Contraption Controls filters, so manually disabled tools stay disabled afterward. Stopping, unloading, player takeover, and errors release temporary overrides; resumed automation checks the track again. This protection operates during outbound mining on the existing track graph; it cannot anticipate slopes that have not been built yet, and it does not remove blocks already placed in the way.

## CC:Tweaked

CC:Tweaked is entirely optional. When present, a controller provides the `train_automation_controller` peripheral on every side. Calls that inspect or mutate world state run on the server thread and report failures as Lua errors.

```lua
local controller = peripheral.find("train_automation_controller")
controller.selectTrainByName("Miner") -- use selectTrain(uuid) for duplicate names
controller.setProfile("mining")
controller.setConfig("return_station", "MINING")
controller.setConfig("return_threshold", 0.85)
print(textutils.serialize(controller.getConfig()))
controller.start()
print(controller.getState(), controller.getControlMode())
controller.stop()

controller.startDrive("forward", 0.2)
controller.stopDrive()
controller.goToStation("MINING")
```

Other operations include `getTrains`, `getTrain`, `getProfiles`, `getConfigurationSchema`, `getStations`, `getInventory`, `getInventoryStats`, `setSpeed`, `setDirection`, `clearSchedule`, `pauseSchedule`, `resumeSchedule`, `getDriveBackends`, `setDriveBackend`, `getActorTypes`, `setActorEnabled`, `isActorEnabled`, and `getLastError`. Cargo is read-only; returned inventory slots use Lua's 1-based numbering.

Railways Additions 1.0.1a exposes `railways_additions` as an alternative backend. It engages the addon's Cruise Control manager and honors disengagement while retaining the native steering/signal/track-end envelope. An optional mixin prevents the addon manager from applying a second acceleration tick to owned trains. Unmanaged addon cruises retain their normal behavior. Controller speed/direction remain authoritative.

## Verification and current limits

Unit tests cover lease conflicts/isolation, strict configuration parsing, request validation, and the discrete braking envelope. Development GameTests exercise real Create Train.tick, travelling bogeys, graph edges, schedules, and inventory, using a test carriage that avoids spawning a physical contraption and supplies test conductors. They cover both directions, stopping, junction steering, signals and chains, track ends, stall reversal, chunk-wait policy, schedule handoff/resume, mixed stack capacities, mining state reload, orphan lease recovery, player takeover, block NBT and optional peripheral/cruise integration.

Run the GameTests both with the base runtime and with all optional dependencies. The tests do not replace gameplay validation of an assembled actor-equipped train, portal/chunk-boundary transitions, collision behavior under load, and visual GUI layout. Actor control currently operates on loaded contraptions. Cross-signal scouting is bounded; unresolved chains stop conservatively at their entry.

The native motion research and implementation boundaries are recorded in [docs/native-create-drive.md](docs/native-create-drive.md).

Mining can optionally pause deployers near any station in the same dimension. Enable **Pause deployers near stations** and set **Station radius** (default 100 blocks, range 0?1024). Deployers resume after every loaded carriage leaves the radius; manual contraption-control disables remain in effect. This option defaults to off.

**Clear lava around drills** defaults to on. While mining, the controller removes lava blocks within one block of active drill faces, including sources, without drops. Water and solid blocks remain; nearby lava can flow back and is cleared again as the miner advances. This reduces lava/water generators in the cutting path rather than draining entire lava lakes.

Train drills break obsidian and crying obsidian faster so the miner can finish before jam recovery starts. Level train deployers also place flat rails beside obstructions instead of letting Create infer an unwanted ramp. Actual sloping train placement keeps Create's normal behavior. Direct mining and recovery obey signals, including occupied groups and cross-signal chains.
