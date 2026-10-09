package io.github.brooswitminecraft.dynamicskateboards;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The only thing {@link GrindEdgeDetector} needs from the world: the collision shape at a block
 * position, in that block's own local 0..1 coordinates (same convention {@code
 * BlockState#getCollisionShape} uses). Kept this narrow so the detector can be unit-tested with a
 * handful of hand-built shapes instead of a running {@code Level} &mdash; the real adapter
 * (wired in {@code WorldGrindSeam}) just forwards to {@code Level#getBlockState(pos)
 * .getCollisionShape(level, pos)}.
 */
public interface GrindCollisionSource {
    VoxelShape collisionShapeAt(BlockPos pos);
}
