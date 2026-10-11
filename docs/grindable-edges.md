# Grindable edges — cobblestone skatepark kit

Collision geometry is the source of grindability. `GrindEdgeDetector` derives exposed paths from
each block's `VoxelShape`; it does not keep a block-ID allow-list. Every slope's collision and
outline shape comes from the same precomputed `SlopeShapes` profile, so walking, skating, and
grind detection use the same geometry in every cardinal `FACING`.

## Current vertical transitions

All dimensions below are voxels in a block-local 0–16 frame. `FACING` points toward the high
(exit) edge. The render blockstate selects a matching model for each of the four facings.

| Current build piece | Entry → exit | Profile | Grindable edge |
|---|---:|---|---|
| `shallow_slope` | 0 → 8 | Eight voxels of rise across the full 16-voxel run | Exposed diagonal side edges, ≈ `sqrt(16² + 8²)` |
| `raised_shallow_slope` | 8 → 16 | The matching upper half of the shallow transition | Its entry face exactly matches `shallow_slope`'s exit face |
| `steep_slope` | 0 → 16 | Sixteen one-voxel steps across the full footprint (45°) | Exposed diagonal side edges, ≈ `sqrt(16² + 16²)` |
| `*_inverted` | mirrored about y=8 | The corresponding overhead profile | Same exposed side-edge geometry |

The shallow pair tiles exactly at the block seam for normal and inverted pieces, across north,
south, east, and west: `shallow_slope` exits at 8 and `raised_shallow_slope` begins at 8. The
full steep profile is deliberately different: columns have heights 1 through 16, so its first
tread begins one voxel high directly at the entry block boundary rather than being inset.

## Compatibility-only slope IDs

`regular_slope`, `raised_steep_slope`, and their inverted IDs remain registered with their block
items, blockstates, models, loot tables, and serialization names. This preserves existing worlds
and inventories. They are intentionally absent from current creative discovery and have no current
recipes; new builds use the canonical transition pieces above.

## Curved pieces

`curved_step` and `curved_wall` provide the kit's chamfered horizontal grind geometry. Their
collision shapes are similarly derived from `CurvedShapes`, and their exposed top paths are found
geometrically by `GrindEdgeDetector`. They remain separate from the vertical-transition profiles
described above.
