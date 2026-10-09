bump: minor

### Added
- Arcade riding controller: server-authoritative speed/acceleration/friction and W/A/S/D steering,
  ollie charge-and-release from the Jump key (monotonic charge curve, non-punishing zero-length
  tap), generously-biased air control, and forgiving landings.
- `InputBuffer`, a reusable pure-Java input recognition window, used here to buffer an early ollie
  press through LANDING; stories (c)/(e) can bind flips/grabs/manuals/grinds on the same class.
- Extended custom skate animation states (charged crouch, airborne extension, landing return);
  `SkatingStatePayload`/`SkateClientState` now carry the full `SkateState`, not just a boolean.

### Fixed
- Closed the inherited sync gap: a client that starts tracking an already-skating player now gets
  that state immediately, and a logout broadcasts GROUNDED so no tracker is left with a stale
  skating state. Corrected `SkateClientState`'s overclaiming "desync impossible by construction"
  javadoc to describe what the sync design actually relies on.
