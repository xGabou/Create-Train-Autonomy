# Controller presentation plan

This layout was recorded before implementation. The reference is the **Create 6.0.10-280** source and assets resolved by this project, not a newer Create branch.

## Reference study

- `StationScreen` / `AbstractStationScreen`: 18 by 18 icon buttons, borderless text, compact railway information, the train's real `TrainIconType` and carriage `bogeySpacing` rendering.
- `ScheduleScreen`: `AbstractSimiContainerScreen` preserves the existing menu lifecycle while supplying Create widget ticks and tooltips. `Indicator` provides the existing gray/green/yellow/red status lamps.
- `AllGuiTextures`: `FONT_COLOR` is `0x575F7A`; `BUTTON`, `BUTTON_HOVER`, `BUTTON_DOWN`, `BUTTON_GREEN`, and `BUTTON_DISABLED` are supplied by `IconButton`.
- `ScrollInput` / `SelectionScrollInput`: wheel interaction, Shift acceleration, selection tooltips, exclusive upper bounds, and the existing Create scroll sound.
- `schedule_2.png`, `widgets.png`, railway casing, station, display link, and contraption controls: narrow bevels, muted metal, warm brass trim, rivets, and recessed controls. New mod textures are original pixel artwork; Create widgets and train sprites are referenced from Create at runtime.

## Window and positions

The window is **304 by 226 GUI pixels**, centered. Coordinates below are relative to its top-left corner. This fits Minecraft's normal minimum 320 by 240 scaled viewport, including Auto and scales 2, 3, and 4 when usable. Nothing is drawn outside the window except tooltips. The same frame remains in place across pages.

```text
  0                120                                  304
  +-------------------------------------------------------+
  | railway icon  TRAIN AUTOMATION CONTROLLER              | 22
  | +----------------+  Automation  [ Mining          <> ] |
  | | Train          |  Configuration                     |
  | | [ Miner    <> ]|  Return station   [ MINING      <> ]|
  | | real train strip| Outbound dir.    [ Forward     <> ]|
  | | Speed     20%  |  Speed            [ 20%         <> ]|
  | | Station        |  Return threshold [ 85%         <> ]|
  | | No station     |  Unload threshold [  5%         <> ]|
  | | Control mode   |  Auto. resume     [ Enabled     <> ]|
  | | Direct control |                                    |
  | | o MINING       |                                    | 178
  | +----------------+                                    |
  | [play][stop][manual][gear][refresh] [<] 1/1 [>] [done] | 204
  | Inventory 63%  Speed 20%  State MINING                 | 220
  +-------------------------------------------------------+
```

| Area / widget | Position and size | Purpose |
| --- | --- | --- |
| Header | `(10, 5)`, `284 x 16` | Compact title with railway icon |
| Train inset | `(12, 28)`, `102 x 150` | Persistent train information |
| Train selector | `(16, 45)`, `94 x 16` | Select any discovered train; tooltip contains full name and UUID |
| Train strip | `(16, 65)`, `94 x 14` | Real Create locomotive/carriage sprites; clipped inside the inset |
| Speed / station / mode | `(16, 86)` through `(16, 149)` | Current speed as a fraction of manual maximum, station, ownership mode; raw blocks/tick in tooltip |
| State lamp and label | `(16, 160)` | Create `Indicator`, with running, waiting, error, and stopped colors |
| Profile selector | `(198, 30)`, `94 x 16` | Generic registry-driven selection; never assumes Mining |
| Settings rows | `(124, 62 + row * 20)`, `168 x 16` | Six rows per page; labels use up to 98 pixels and controls 66 pixels |
| Action buttons | `(12 + index * 22, 186)`, `18 x 18` | Start (green), stop, manual page, advanced page, refresh |
| Page arrows | `(198, 186)` / `(246, 186)`, `18 x 18` | Page arbitrary profile schemas without overflow |
| Confirm / close | `(274, 186)`, `18 x 18` | Close the existing server-authorized menu |
| Status bar | `(12, 210)`, `280 x 10` | Inventory, speed, and state, or one compact error line with full tooltip |

Mining's fields come from the existing schema. The GUI creates controls by field type, range, and optional presentation metadata: station pickers, percentages, choices, toggles, and borderless text inputs. Label translations use a profile-specific key with the existing schema label as fallback. Other profiles receive the same frame and automatic paging.

The advanced page contains drive backend and fields marked advanced (including deployer actor control). No timeout, polling, or debug settings are invented. The manual page uses the existing forward/backward, stop-drive, and go-to-station commands. It does not add new profile state transitions. Navigation requires automation to be stopped, as before.

## Interaction and text safety

- Selection and number controls use Create scroll widgets. Click also advances a selector so the interaction is discoverable without a mouse wheel. Number controls retain wheel/Shift-wheel behavior.
- Profile, train, backend, and configuration edits retain existing server validation. Auto-updating status must not recreate a focused field or reset an in-progress scroll edit.
- Station fields list known stations on the selected train's graph. A custom entry mode preserves station-filter/text functionality. Percentages stay numeric on the wire; display adds the percent sign only.
- Names, labels, mode, state, and errors are ellipsized to their actual allocated width. Hover tooltips provide the full value and are wrapped within the viewport. All action icons have tooltips.
- Start/stop and low-level movement keep the existing ownership and active-profile rules. No controller, schedule, persistence, drive, or automation-engine changes are planned.

## Cabinet and item

Use an original 16-pixel texture palette of dark steel/andesite, warm brass, muted gray, and a small railway-track glyph. The shape has a full-width lower plinth, a slightly narrower mechanical body, a top cap, and a framed inset control face. Side vents and a few rivets survive inventory scale. The item uses the same 3D model.

Horizontal facing points the front panel at the placing player. Four lamp model variants (inactive, running, waiting, error), each rotated for four directions, provide the indicator without a block entity renderer. The existing server tick only derives the visual lamp state and sends a block update when it changes; it does not command trains.

Create's existing item-description formatting supplies a Shift tooltip. No Ponder scene or kinetic animation is added.

## Verification to record after implementation

Verify build and existing regression checks, all four facings, all four lamp variants, real train sprites, empty train/station lists, long names, long translated labels, error tooltips, scrolling, profile changes, advanced/manual pages, and overflow schemas. Capture the actual screen at Auto, 2, 3, and 4 where the viewport permits. Record the effective GUI dimensions because Minecraft can limit the requested scale on small windows.
