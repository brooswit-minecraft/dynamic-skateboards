bump: minor

### Added
- Geometry-derived grind detector (`GrindEdgeDetector`): reads world `VoxelShape` collision boxes
  near the player, not a block id list, and extracts straight/diagonal "height-profile" chords
  plus incrementally-turning "depth-profile" paths (curved pieces) via `GrindPath`/`GrindEdge`.
  No hardcoded grindable-block list anywhere in this diff.
- `GrindAcquisition`: generous-snap, approach-tolerance candidate selection with a deterministic
  tie-break (never iteration order or object identity).
- `GrindFollower`: pure per-tick following/release state machine (end of edge, Shift release, or
  Jump = release), mirroring `SkateController`'s own pure-core/Minecraft-glue split.
- `WorldGrindSeam`: the real `GrindSeam` implementation, wired into `SkateController` in place of
  `GrindSeam.NONE` per player; drives position/velocity directly while grinding and hands back to
  normal riding physics on release.
- All new tuning values (usable-length threshold, scan radius, snap radius, approach tolerance,
  smooth-step threshold, reacquire cooldown) added to the existing `SkateConstants`.
- Minimal grinding feedback: an action-bar message on acquisition.

### Tests
- `GrindEdgeDetectorTest`: length threshold both sides of the boundary; vanilla slab/stair edges
  with none of this mod's blocks; interior/occluded edge excluded; diagonal slope edge with
  rising/descending path; curved piece incremental direction change; every `SlopeKind` and curved
  height/side variant walked against docs/grindable-edges.md.
- `GrindFollowerTest`: every release path (end of edge in both directions, Shift release, Jump).
- `GrindAcquisitionTest`: snap radius, approach tolerance, deterministic tie-break, travel-sign.
