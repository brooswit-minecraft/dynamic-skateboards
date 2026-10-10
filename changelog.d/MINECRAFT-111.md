bump: minor

### Added
- Server-authoritative arcade riding inputs: periodic forward-foot kicks, back-foot braking to a
  complete stop (never reverse), and left/right heading steering without sideways walking.
- Grounded Shift now acquires a nearby geometry-derived grind before falling back to manual,
  preserving the controller's speed into the existing grind follower.

### Tests
- Pure controller and movement tests cover kick cadence, braking, strafe steering, and grounded
  Shift edge acquisition.
