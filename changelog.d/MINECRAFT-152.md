bump: minor

### Added
- FLIP family (left click + W/A/S/D direction): KICKFLIP (neutral), POP_SHUVIT (forward),
  FAKIE_FLIP (back), HEELFLIP (left), VARIAL_KICKFLIP (right). The direction-to-trick table lives
  in `TrickTable`, the one place story (g) can revise it.
- GRAB family (right click, held, same directions): INDY, NOSE, TAIL, MUTE, CRAIL. Sustains while
  held, ends on release or on leaving AIRBORNE.
- MANUAL: Shift while riding on the ground balances the board on two wheels and keeps rolling;
  generous, no balance-failure mechanic. `SkateState` gains `MANUAL` alongside the existing states
  rather than forking a second state machine.
- Shift contextual dispatch: riding routes to MANUAL, airborne/approaching routes to `GrindSeam`
  (a named no-op stub story (e) implements for grind detection/acquisition/following).
- Tricks animate from AIRBORNE and resolve on landing; buffered via the existing `InputBuffer` so a
  press slightly before takeoff still lands the intended trick.
- Vanilla left/right click (attack/use/place) suppressed while skating and restored the instant
  skating ends; decision logic (`ClickSuppressionPolicy`) is pure and unit-tested, wired to
  `AttackEntityEvent`/`PlayerInteractEvent.LeftClickBlock`/`RightClickBlock`/`RightClickItem`.
- `SkateTrickInputPayload`: continuous client-to-server mirror of Shift/attack/use/WASD, same
  no-new-keybind pattern as `SkateJumpInputPayload`.

### Fixed
- Vanilla sneak no longer engages from the Shift key while skating: `MovementInputUpdateEvent`
  clears `Input.shiftKeyDown` client-side (after this mod's own payload has already read it)
  whenever the client's skate-state mirror is non-GROUNDED, restored automatically at GROUNDED.
  Decision is `SneakSuppressionPolicy`, pure and unit-tested, same shape as `ClickSuppressionPolicy`.
