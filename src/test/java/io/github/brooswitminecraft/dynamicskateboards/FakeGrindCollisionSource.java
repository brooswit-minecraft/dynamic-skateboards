package io.github.brooswitminecraft.dynamicskateboards;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Hand-built world geometry for {@link GrindEdgeDetector} tests: no {@code Level} needed. */
final class FakeGrindCollisionSource implements GrindCollisionSource {
    private final Map<BlockPos, VoxelShape> shapes = new HashMap<>();

    FakeGrindCollisionSource put(BlockPos pos, VoxelShape shape) {
        shapes.put(pos, shape);
        return this;
    }

    FakeGrindCollisionSource putFullBlock(BlockPos pos) {
        return put(pos, Shapes.block());
    }

    @Override
    public VoxelShape collisionShapeAt(BlockPos pos) {
        return shapes.getOrDefault(pos, Shapes.empty());
    }
}
