# Native stationless Create drive: source trace

Inspected the source jar for `com.simibubi.create:create-1.21.1:6.0.10-280` before implementing the backend. Server startup confirms Create reports release commit `ac0c444d9828da3453ae8cc65338e8de063286fb`. The release source can be reviewed at [Create 6.0.10](https://github.com/Creators-of-Create/Create/tree/mc1.21.1-6.0.10). Source snapshots and bytecode inspection outputs used during development are under the ignored `build/reference` directory.

## Manual control path

[CarriageContraptionEntity](https://github.com/Creators-of-Create/Create/blob/mc1.21.1-6.0.10/src/main/java/com/simibubi/create/content/trains/entity/CarriageContraptionEntity.java), `startControlling` and `control`:

1. Starting player control cancels navigation, pauses ScheduleRuntime and clears waiting-for-signal state.
2. Held forward/backward inputs become signed throttle. Controls-block orientation can invert throttle and steering. The framework instead uses an explicit direction relative to the assembled train.
3. Manual steering is LEFT, RIGHT or NONE. Without a destination, movement uses the manual steering selector.
4. Manual maximum speed uses Create's configured manual multiplier and train throttle. A bogey on a turn imposes the turn limit. Reverse player driving uses a reduced maximum.
5. A direction opposing `speedBeforeStall` cancels actor stalling through `Train.cancelStall`.
6. Manual control sets `targetSpeed`, marks `manualTick`, then calls `approachTargetSpeed` once. It burns fuel when requesting movement. Counter-throttle player input can double braking; this framework uses ordinary Create acceleration to keep its braking envelope consistent.

## Motion and tick order

[Train](https://github.com/Creators-of-Create/Create/blob/mc1.21.1-6.0.10/src/main/java/com/simibubi/create/content/trains/entity/Train.java), `earlyTick`, `tick`, `tickPassiveSlowdown`, `approachTargetSpeed`:

- GlobalRailwayManager clears per-tick signal occupancy/reservations, runs every train's earlyTick, then runs every train's tick. Early ticks rebuild occupied and reserved signal groups before movement.
- Train.tick updates conductors, ticks ScheduleRuntime, ticks Navigation, applies passive slowdown, travels the carriage bogeys, and handles stalls/collisions/derailment/track ends.
- With no destination, an unmarked manual tick causes passive slowdown. That method clears `manualTick` each train tick. Setting a flag once cannot maintain direct drive.
- `approachTargetSpeed` moves actual speed toward target speed by Create's configured acceleration. Manual acceleration also leaves the current station through Create's lifecycle method.
- Positive speed travels from the leading carriage; negative speed reverses carriage iteration and uses the trailing end. Physics, stress and occupancy remain Create-owned.
- A blocked travelling point or incompatible track makes Create stop speed and cancel navigation. Excess stress derails the train. Actor stalls can preserve a speed that would otherwise be restored later; explicit stopping resets that intent through Create's cancellation API.

The framework injects immediately before the first `ScheduleRuntime.tick` invocation, after conductor updates. Its backend sets the target and calls Create acceleration once there. It never runs a competing second motion loop or writes speed every tick. A one-time speed reset is used for explicit stop/handoff/recovery.

## Track and switch selection

[Navigation](https://github.com/Creators-of-Create/Create/blob/mc1.21.1-6.0.10/src/main/java/com/simibubi/create/content/trains/entity/Navigation.java), `control`, and [TravellingPoint](https://github.com/Creators-of-Create/Create/blob/mc1.21.1-6.0.10/src/main/java/com/simibubi/create/content/trains/entity/TravellingPoint.java), `steer`/`travel`:

- Destination navigation consumes a discovered path at junctions.
- Without a destination, Navigation.control delegates to `TravellingPoint.steer(manualSteer, worldUp)`.
- Steering compares eligible outgoing edge trajectories to the requested lateral target. NONE selects the most straight-ahead eligible route. It operates only on connections that Create permits the travelling point to traverse.
- The scout uses the same direction and selector as actual direct movement. While reversing a moving train, it scouts the current leading end and brakes to zero before using the opposite end.
- At an endpoint, TravellingPoint reports the distance actually travelled and its blocked flag. The backend applies a discrete braking envelope with a buffer before the endpoint. A default request finishes there; a request with `stopAtTrackEnd=false` waits for graph extension and retries.

No destination station or fake station is created for direct drive. The selector controls vanilla Create junction choices, not arbitrary addon-specific switch machinery.

## Signals

Navigation.tick returns immediately when there is no destination, so its scheduled signal scout does not run during stationless manual drive. Player manual movement can pass red signals, and Navigation also treats manual ticks differently when testing occupied sections. Reusing manual acceleration alone would not provide scheduled-style signal protection.

The backend therefore scouts cloned travelling points along the requested route:

- Checks forced-red state and signal-group occupancy/intersections through Create's APIs.
- Brakes before an occupied signal and updates the waiting-for-signal state used by Create's front listener.
- Collects cross-signal chains and reserves a clear chain only after reaching its terminating entry signal. An occupied downstream section blocks at the cross-signal entry.
- Stops conservatively at unresolved or longer-than-bounded chains, rather than assuming a clear route.
- Rebuilds its reservations each tick, preserving actual occupied groups and keeping other trains from reserving the same route. It clears its reservation intent on stop/handoff.
- `SignalBehavior.IGNORE` is an explicit Java request option; GUI and basic CC calls use OBEY.

The scout's safety speed includes travel during the current tick. It is not a substitute for Create's collision simulation. Current track curves and curves in the braking envelope respect the turn-speed cap.

## Schedule handoff and persistence

[ScheduleRuntime](https://github.com/Creators-of-Create/Create/blob/mc1.21.1-6.0.10/src/main/java/com/simibubi/create/content/trains/schedule/ScheduleRuntime.java), `transitInterrupted`, `setSchedule`, `discardSchedule`:

- Entering direct control pauses the existing schedule and calls Navigation.cancelNavigation, which releases the destination reservation, clears its path and returns interrupted transit to PRE_TRANSIT.
- Applying a schedule stops the direct backend, suspends optional addon movement, resets manual tick/steering/target/stall intent and cancels old navigation before using `setSchedule`.
- Resuming a paused schedule uses the same stop/reset boundary. ScheduleRuntime then restarts its normal instruction, using ordinary conductor/path/signal rules.
- A one-entry station schedule has an empty condition column and is non-cyclic, so Create completes it after arrival. Profiles may pause at arrival to impose their own generic conditions.
- Ownership leases are scoped to server and controller identity and are persisted separately from block state. A persisted train whose controller is not attached is suspended before navigation, and player controls can clear that dormant lease.

## Optional Cruise Control adapter

Inspected the supplied Railways Additions `8407753` jar (`railwaysuntold_additions` 1.0.1a) with javap. Its public CruiseControlManager offers `engage(Train,double)`, `disengage(UUID)`, `isEngaged(UUID)` and `tick(MinecraftServer)`. The tick writes targetSpeed and manualTick and calls approachTargetSpeed outside Train.tick.

The adapter uses those public engagement methods. A conditional mixin suppresses the three duplicate motion writes/call only for framework-owned trains; the controller's single native tick applies direction, steering and safety. Schedule handoff disengages the manager. The hook is registered only when the addon class bytecode exists. The core knows a generic movement-suspension hook, not an addon class or cruise singleton.
