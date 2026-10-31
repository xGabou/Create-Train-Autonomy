# Create Train Automation 1.9

### New
- Named controller commands for remote status, start, retry, cancel, and return-to-base control, with persistent cross-dimension addresses and command suggestions.
- Optional station deployer protection with a configurable radius (100 blocks by default, disabled until enabled).
- Optional lava clearing around active mining drill faces, enabled by default, to reduce lava/water generators.

### Fixed
- Train drills break obsidian and crying obsidian faster, finishing before stuck recovery.
- Level train deployers no longer infer false track slopes from adjacent solid blocks, including fluid-generated stone.
- Direct-drive signal reservations publish train ownership immediately and release stale reservations before rescanning.

# Create Train Automation 1.8

### Fixed
- Mining trains finish unloading at the cargo threshold or after five minutes without cargo changes, allowing retained rails and supplies to stay aboard without blocking automatic resume. Cargo changes reset the timer, and its deadline survives reloads.
- Unloading completion respects the automatic resume setting and keeps trains sent home after repeated jams stopped at base.

# Create Train Automation 1.7

### Fixed
- Moving mechanical drills now break cobwebs, kelp, seagrass, vines, sculk veins and short grass, fixing [Create #8448](https://github.com/Creators-of-Create/Create/issues/8448). The fix applies to all moving contraptions, including trains without an automation controller, and preserves Create's normal drops, inventory collection, fluid replacement and protected-block rules.

# Create Train Automation 1.5

### Fixed
- Returning mining trains preserve their portable storage interface connection when holding at the station, allowing base-side cargo extraction. Unloading starts only after the train has stopped and navigation has finished.
- Added **Pause tools when not mining**, enabled by default, to pause drills and deployers during return and unloading while leaving storage interfaces active and preserving manual actor settings.
- The fourth consecutive mining jam sends a chat warning, pauses mining for ten minutes, then sends the train home. The jam count and deadline survive reloads; the train stays at base after unloading until manually restarted.

### New
- Active Train Automation Controllers keep their own chunk loaded and ticking, so automation continues when players travel away. Enabled profiles retain their chunk ticket across server restarts.
- A moving area around every controlled carriage stays loaded and entity-ticking, allowing drills and deployers to keep working far from players. Old train chunks are released as the train moves; the entire route is not kept loaded.
- Stopping, errors, player takeover, and block removal release the controller's ticket. Multiple controllers in one chunk keep independent tickets; stale tickets are cleaned up on world load.

# Create Train Automation 1.4

### Fixed
- Mining trains that stop making position progress for 5 seconds now back up about 10 blocks and retry automatically, even when the train reports speed while stuck inside blocks. Recovery works in either mining direction.
- Drills and deployers pause during the retreat and resume with their previous control settings when mining retries. Signal waits, unloaded chunks, slope tool pauses, and a zero mining speed do not trigger recovery.
- Recovery preserves its remaining retreat distance across controller reloads. If the retreat is also blocked, mining retries from the available position; full cargo still takes priority and returns to the station.

# Create Train Automation 1.3

### Fixed
- Compatibility with Create Threaded Trains 1.0.0: railway ticks stay on the server thread, preventing the "Train controls must run on the server thread" error during mining and slope-tool checks. This safety fallback disables parallel ticking for the entire railway network while both mods are installed and logs a one-time warning when active.
- Unselected train and automation selectors now show "Select a train" and "Select a profile" instead of appearing to select the first available option.
- The empty profile configuration panel now explains how to choose a profile. Clicking or scrolling can select Mining even when it is the only available profile.

# Create Train Automation 1.2

### New
- **Ponder scene** for the Train Automation Controller. Hold W over the block to see how to open the panel, start a profile, and read the status lamp. The controller is also listed under Create's Railway Equipment category.
- **Slope option for the Mining profile:** *Pause tools on slopes* stops the train's deployers and drills while the train is on a slope. It is on by default and can be turned off in the profile settings.
- **Slope lookahead (blocks)** sets how far ahead the controller looks for a slope before pausing the tools (default 8). The state shows *Slope: tools paused* while this is active.

### Changed
- Final textures for the controller block, replacing the temporary ones. They use Create-style andesite, brass and wood casing, and the screen shows a different icon for each state (inactive, running, waiting, error).
- Reworked block model with pixel-aligned textures and a 2×2 status lamp on the front of the top cap.
- New control panel background and icons to match Create's interface.

### Fixed
- The block's hitbox now matches the new model.

# Create Train Automation 1.0

Initial release for Minecraft 1.21.1, NeoForge, and Create 6.0.10.

- Train Automation Controller with train selection, configuration, status, and manual controls.
- Stationless forward/backward driving, speed control, safe stopping, and Create schedule navigation.
- Train inventory, station, and supported actor monitoring and control.
- Persistent profile configuration and train ownership for multiple controllers.
- Mining automation as the first profile on the general purpose framework.
- Optional CC:Tweaked control and Railways Additions drive integration.
