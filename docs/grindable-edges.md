# Grindable edges — cobblestone skatepark kit

This is story (e)'s specification for the geometry story MINECRAFT-97 (this story) authored.
Grindability is derived from collision geometry, not a hand-authored list — see the spec's
"Grinding" section — so this document states, per piece, which edges are *intended* to read as
grindable under that rule, and their exposed length against the spec's initial guideline of
"an exposed straight edge of sufficient usable length, approximately 4/16 of a block."

All lengths below are in 1/16ths of a block ("voxels"). All angles are measured from horizontal.
All model JSON under `assets/dynamicskateboards/models/` is machine-generated (a script mirroring
the exact geometry formulas in `SlopeShapes`/`CurvedShapes`), not hand-authored — one `elements`
box per 1/16-wide collision column, kept in lockstep with the collision shape on purpose.

## Vertical transition family (10 blocks)

Every piece is a `SlopeBlock` ([`SlopeShapes.java`](../src/main/java/io/github/brooswitminecraft/dynamicskateboards/SlopeShapes.java),
[`SlopeKind.java`](../src/main/java/io/github/brooswitminecraft/dynamicskateboards/SlopeKind.java)):
a stepped, 1/16-voxel approximation of a straight ramp from an **entry height** to an **exit
height** (both in the block's own 0–16 local frame — the block is solid from its own floor, y=0,
up to the ramp surface at every column), spanning the block's full 16-voxel run. `FACING` points
toward the **exit** (high) edge; rotating it just re-orients the same shape.

| Piece | Entry → Exit (voxels) | Rise | Angle | Grindable edges |
|---|---|---|---|---|
| `shallow_slope` | 0 → 4 | 4 | 14.0° | Both side edges, rising diagonally, length ≈ `sqrt(16²+4²)` ≈ 16.5 |
| `raised_shallow_slope` | 4 → 8 | 4 | 14.0° | Same as above; picks up exactly where `shallow_slope`'s exit (4) ends, so a chained pair reads as one continuous 16.5×2-voxel diagonal edge |
| `regular_slope` | 0 → 6 | 6 | 20.6° | Both side edges, length ≈ `sqrt(16²+6²)` ≈ 17.1 |
| `steep_slope` | 0 → 8 | 8 | 26.6° | Both side edges, length ≈ `sqrt(16²+8²)` ≈ 17.9 |
| `raised_steep_slope` | 8 → 16 | 8 | 26.6° | Same as above; picks up exactly where `steep_slope`'s exit (8) ends. Exit reaches 16 (the block ceiling), so it also exposes a flat 16-long top edge flush with the level above |
| `*_inverted` (5 more) | mirrored about y=8 (entry' = 16−exit, exit' = 16−entry) | same rise | same angle | Same side-edge geometry, mirrored to hang from the ceiling, for continuing a transition overhead |

Every side-edge length comfortably clears the ~4/16 guideline — slopes are exactly the geometry
the spec calls out for "rising and descending diagonal edges."

**Tiling invariant, verified on the actual `VoxelShape`s, exactly (not within a voxel), across
all four facings** (`SlopeShapesTest#slopeChainingTilesExactlyAcrossAllFacings`): the solid
Y-interval at `shallow_slope`'s exit face is bit-for-bit identical to the solid Y-interval at
`raised_shallow_slope`'s entry face, and likewise for `steep_slope`/`raised_steep_slope` and both
inverted counterparts. `SlopeShapes.stepHeight` deliberately uses `floor`, not `round`, so that
every rise value this family uses produces column 0 = exactly 0 and column 15 = exactly the full
rise — `round` would occasionally round column 0 up by a voxel (e.g. rise=8) and reintroduce the
exact seam this geometry exists to avoid. Placed side by side at the same world Y level, each pair
continues a single straight line with zero gap or overlap. `regular_slope` has no raised partner
— it's a standalone bank flush with flat ground at its entry, not part of a chained pair
(`SlopeShapesTest#regularSlopeIsFlushWithFlatGroundAtItsEntryAcrossAllFacings`).

**On the 4/16 guideline for this family:** it fits comfortably and I have no correction to
suggest. The binding constraint for slopes isn't edge length, it's that `riseVoxels` stays under
16 so the stepped approximation's column-0 and column-15 values land exactly on the tiling
invariant above; that's an implementation detail, not a product number worth relitigating here.

## What a quarter-pipe and a full-pipe are built from — and what this kit cannot do alone

**Quarter-pipe (achievable):** one row of `steep_slope` (0→8) then `raised_steep_slope` (8→16) at
the same world Y level, continuing the same straight 26.6° line up to the block ceiling. The
*steepest angle this family reaches is 26.6° (`atan(8/16)`)* — nowhere near vertical — so the
actual vertical lip is a plain flat cobblestone block/wall placed above, per the spec's own "flat
vertical block faces can form the tips/ends where appropriate." A quarter-pipe from this kit is
therefore a 26.6° bank capped by a flat vertical wall, not a curve that reaches 90° on its own.
`shallow_slope`/`raised_shallow_slope` or `regular_slope` give gentler, shorter banks the same way.

**Full-pipe (NOT achievable with this piece set alone — flagging per review, not claiming it
works):** an earlier version of this document claimed the `*_inverted` counterparts let a
full-pipe "curve back in overhead and close the loop." That doesn't hold: the inverted pieces
mirror the same ≤26.6° angle, so continuing `raised_steep_slope`'s exit (flush with the block
ceiling) directly into an inverted piece does not produce a seamless curve toward horizontal
overhead — there is a real discontinuity there (the inverted piece's own entry face does not
expose the same interval `raised_steep_slope`'s exit does; I checked this on the actual shapes,
not just the angles). What this kit *can* build is a half-pipe/channel shape: two mirrored
26.6°-banked quarter-pipes (as above), each capped by a flat vertical wall shaft of whatever
height the build needs, with the matching `*_inverted` piece optionally mounted at the *top* of
that flat shaft (flush against its flat top, not against the lower bank directly) to add a short
overhanging lip on each side. Closing a true circular full-pipe tube needs a transition piece
genuinely steeper than 26.6° (ideally reaching toward vertical) that isn't in this family; I'm
raising that gap explicitly on MINECRAFT-97 rather than asserting this kit already does it.

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

Both comfortably clear the 4/16 guideline, and within one block the taper is genuinely
continuous — not a single abrupt step (`CurvedShapesTest#chamferDepthStepsBySingleVoxelsAcrossTheFullWidth`,
no more than one voxel of depth change between adjacent 1/16 slices).

**What chaining multiple pieces actually gives (NOT a smooth large-radius arc — flagging per
review, not claiming it works):** an earlier version of this document claimed "about 6–7 pieces
... carry a grind through a full 90-degree corner." That isn't right either. Each block authors
its taper in its *own* 0..16 local coordinate frame, which restarts at every block boundary — so
chaining several identical pieces straight along the run axis (same facing, same `ChamferSide`)
repeats the exact same bounded taper at every boundary rather than compounding into a larger
curve (`CurvedShapesTest#chainingIdenticalPiecesAlongTheRunDoesNotAccumulateATurn`: the global
depth profile is exactly periodic with period 16, and the excursion stays at `TAPER_VOXELS` = 4,
not growing with chain length). The "radius" this chain gives, honestly, is zero — a straight
chain does not turn at all beyond the one bounded ripple per block.

The piece's actual intended use is narrower: placed at a single 90-degree grid joint between two
differently-`FACING` neighbors (the kind of corner a vanilla stair/wall would turn abruptly), its
bevel shaves a small, continuous wedge out of the sharp outside corner, softening that one joint
rather than eliminating or redistributing the full 90 degrees across a chain. I have not modeled
or tested the exact combined silhouette of two such pieces meeting at a right angle (that requires
reasoning about both pieces' world-space corner alignment together, not just one piece's own
shape) — flagging this as open rather than asserting a specific result. A piece set that
genuinely approximates a long, smooth, large-radius curve would need a different design (e.g. a
per-piece rotation finer than the 4 cardinal `FACING`s, or several differently-angled chamfer
widths); that's a larger change than this story's "small explicit set" scope and I'm surfacing it
to MINECRAFT-97 rather than building it unasked.
