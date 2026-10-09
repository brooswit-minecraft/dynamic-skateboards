# Grindable edges — cobblestone skatepark kit

This is story (e)'s specification for the geometry story MINECRAFT-97 (this story) authored.
Grindability is derived from collision geometry, not a hand-authored list — see the spec's
"Grinding" section — so this document states, per piece, which edges are *intended* to read as
grindable under that rule, and their exposed length against the spec's initial guideline of
"an exposed straight edge of sufficient usable length, approximately 4/16 of a block."

All lengths below are in 1/16ths of a block ("voxels"). All angles are measured from horizontal.

## Vertical transition family (10 blocks)

Every piece is a `SlopeBlock` ([`SlopeShapes.java`](../src/main/java/io/github/brooswitminecraft/dynamicskateboards/SlopeShapes.java),
[`SlopeKind.java`](../src/main/java/io/github/brooswitminecraft/dynamicskateboards/SlopeKind.java)):
a stepped, 1/16-voxel approximation of a straight ramp from an **entry height** to an **exit
height** (both in the block's own 0–16 local frame), spanning the block's full 16-voxel run.
`FACING` points toward the **exit** (high) edge; rotating it just re-orients the same shape.

| Piece | Entry → Exit (voxels) | Rise | Angle | Grindable edges |
|---|---|---|---|---|
| `shallow_slope` | 0 → 4 | 4 | 14.0° | Both side edges, rising diagonally, length ≈ `sqrt(16²+4²)` ≈ 16.5 |
| `raised_shallow_slope` | 4 → 8 | 4 | 14.0° | Same as above; picks up exactly where `shallow_slope`'s exit (4) ends, so a chained pair reads as one continuous 16.5×2-voxel diagonal edge |
| `regular_slope` | 0 → 6 | 6 | 20.6° | Both side edges, length ≈ `sqrt(16²+6²)` ≈ 17.1 |
| `steep_slope` | 0 → 8 | 8 | 26.6° | Both side edges, length ≈ `sqrt(16²+8²)` ≈ 17.9 |
| `raised_steep_slope` | 8 → 16 | 8 | 26.6° | Same as above; picks up exactly where `steep_slope`'s exit (8) ends. Exit reaches 16 (the block ceiling), so it also exposes a flat 16-long top edge flush with the level above |
| `*_inverted` (5 more) | mirrored about y=8 (entry' = 16−exit, exit' = 16−entry) | same rise | same angle | Same side-edge geometry, mirrored to hang from the ceiling, for continuing a transition overhead (full-pipe tops) |

Every side-edge length comfortably clears the ~4/16 guideline — slopes are exactly the geometry
the spec calls out for "rising and descending diagonal edges."

**Tiling invariant** (asserted by `SlopeShapesTest`): `shallow_slope.exitHeight() ==
raised_shallow_slope.entryHeight()` and `steep_slope.exitHeight() ==
raised_steep_slope.entryHeight()`, both exactly, and the built `VoxelShape`'s own exit-face AABB
reaches that height exactly (the entry face is within one voxel, the stepped approximation's only
rounding slack, since the straight-line formula doesn't always land on an integer voxel at column
0). Placed side by side at the same world Y level, the pair continues a single straight line with
no gap or overlap. `regular_slope` has no raised partner — it's a standalone bank, not part of a
chained pair.

**On the 4/16 guideline for this family:** it fits comfortably and I have no correction to
suggest. The binding constraint for slopes isn't edge length, it's that `riseVoxels` divides
cleanly enough into 16 steps to keep the stepped approximation visually smooth; that's an
implementation detail, not a product number worth relitigating here.

## Curved step / curved wall (2 blocks, 8 states each)

Both are `CurvedBlock` ([`CurvedShapes.java`](../src/main/java/io/github/brooswitminecraft/dynamicskateboards/CurvedShapes.java)):
instead of a flat 16×16 footprint, the block's own Z-depth tapers from 16 (full) down to 12 across
the full 16-voxel width, using the same stepped-line approximation as the slopes (reused at a
shallow 4-voxel taper). `curved_step` is 8 voxels tall (step height); `curved_wall` is a full
16-voxel-tall block. `CHAMFER_SIDE` (`left`/`right`) picks which corner of the leading edge carries
the taper, so a builder can mirror the curve; `FACING` orients it like the slopes.

| Piece | Grindable edge | Length | Per-piece turn |
|---|---|---|---|
| `curved_step` | The top perimeter edge following the taper, at y=8 | ≈ `sqrt(16²+4²)` ≈ 16.5 | ≈14.0° (`atan(4/16)`) |
| `curved_wall` | The top perimeter edge following the taper, at y=16 | ≈ `sqrt(16²+4²)` ≈ 16.5 | ≈14.0° (`atan(4/16)`) |

Both comfortably clear the 4/16 guideline. The per-piece turn (≈14°, asserted `<45°` by
`CurvedShapesTest`) is deliberately small and continuous across the block's full width — not a
sharp corner notch — so chaining several pieces of the same `ChamferSide` (rotating `FACING` to
follow the curve) approximates a smooth turn: about 6–7 pieces to carry a grind through a full
90-degree corner, versus the single 90-degree jog a vanilla stair/wall corner would force.

## What a quarter-pipe and a full-pipe are built from

- **Quarter-pipe** (flat ground curving up to vertical): one row of `steep_slope` (0→8, 26.6°)
  then `raised_steep_slope` (8→16, 26.6°) at the same world Y level, continuing the same straight
  line up to the block ceiling, capped with a flat cobblestone wall above for the vertical lip.
  `shallow_slope`/`raised_shallow_slope` or `regular_slope` give gentler banks for a shallower
  quarter-pipe.
- **Full-pipe** (a closed tube): two mirrored quarter-pipes (as above) facing each other, each
  continued past vertical using the `*_inverted` counterparts (mirrored about the ceiling) to
  curve back in overhead and meet at the top, closing the loop. `curved_step`/`curved_wall`
  sections let the pipe's run turn horizontally (e.g. a bent or circular full-pipe) instead of
  being a straight tube.
