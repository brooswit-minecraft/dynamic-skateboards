package io.github.brooswitminecraft.dynamicskateboards;

import java.util.Map;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * One piece of the vertical transition family ({@link SlopeKind}). The shape is hand-authored
 * voxel geometry ({@link SlopeShapes}), precomputed once per facing at construction time — there
 * is no runtime shape math, and no configurable angle: each {@code SlopeKind} is its own fixed
 * shape, per story MINECRAFT-97's "small explicit set, not a procedural slope generator"
 * requirement.
 */
public class SlopeBlock extends Block {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    private final Map<Direction, VoxelShape> shapesByFacing;
    private final MapCodec<SlopeBlock> codec;

    public SlopeBlock(Properties properties, SlopeKind kind) {
        super(properties);
        this.shapesByFacing = SlopeShapes.buildFacingShapes(kind.riseVoxels(), kind.verticalShift(), kind.inverted());
        this.codec = simpleCodec(p -> new SlopeBlock(p, kind));
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.SOUTH));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return codec;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    protected VoxelShape getShape(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapesByFacing.get(state.getValue(FACING));
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapesByFacing.get(state.getValue(FACING));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection());
    }

    @Override
    protected BlockState rotate(BlockState state, net.minecraft.world.level.block.Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, net.minecraft.world.level.block.Mirror mirror) {
        return state.setValue(FACING, mirror.mirror(state.getValue(FACING)));
    }

    /** Exposed for tests: the precomputed shape for a given facing, with no world/level needed. */
    public VoxelShape shapeForFacing(Direction facing) {
        return shapesByFacing.get(facing);
    }
}
