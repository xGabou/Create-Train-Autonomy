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
