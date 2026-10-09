bump: minor

### Added
- Bail (story f): a badly missed landing &mdash; too much downward speed still carried into
  touchdown (`SkateConstants.BAIL_IMPACT_SPEED_THRESHOLD`) &mdash; now forces the player off the
  board instead of silently resolving into `LANDING`. Covers both a genuinely bad fall and a
  grind run off the end of a rail with nothing to land on, since both resolve through the same
  AIRBORNE-&gt;touchdown check (`SkateController#bail`).
- On bail, the equipped skateboard leaves the player's inventory (the only place in the mod this
  happens - `DynamicSkateboardsMod.ServerEvents#performBail`) and a loose `LooseSkateboardEntity`
  spawns, carrying the crash's velocity as a Sable rigid body: four passive wheel-contact points,
  no propulsion, no skating controller.
- Walk-over pickup: touching the loose board returns the skateboard item to the player's
  inventory, no keybind or interact prompt, via a race-safe first-touch-wins `LooseBoardClaim`.
- The player exits skating via the existing exit path (never a new one) and ends in a normal
  standing state; re-selecting the recovered board re-enters skating via the existing path too.
- All new tuning values (impact threshold, board mass/geometry, wheel suspension, tumble spin,
  pickup reach) added to the existing `SkateConstants`; no inline magic numbers.

### Tests
- `SkateControllerTest`: normal landings never bail; a landing at/above the threshold bails
  (state, the one-shot flag, and that an in-progress flip is dropped, not completed); a bail
  resets speed/charge/grab like any other exit; a grind-dismount fall into the same threshold
  bails on the following touchdown.
- `LooseBoardClaimTest`: first-touch-wins, a second same-tick claimant always loses, the latch
  never reopens.
- `LooseSkateboardPersistenceTest` / `BailRoundTripTest`: the loose board's orientation survives
  an NBT save/load round trip headlessly (chunk unload/reload persistence, without a running
  level), chained together with the full skating -&gt; bail -&gt; loose board -&gt; pickup ->
  re-enter-skating lifecycle built out of the same pure classes production uses.
