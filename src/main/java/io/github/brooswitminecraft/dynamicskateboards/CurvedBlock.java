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
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Shared base for {@code curved_step} and {@code curved_wall} ({@link CurvedShapes}): a block
 * that tapers its own depth across its full width, so its leading edge turns by a small,
 * consistent angle instead of jogging a hard 90 degrees. Subclasses only differ in height.
 */
public class CurvedBlock extends Block {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<ChamferSide> CHAMFER_SIDE =
            EnumProperty.create("chamfer_side", ChamferSide.class);

    private final Map<Direction, VoxelShape> rightShapesByFacing;
    private final Map<Direction, VoxelShape> leftShapesByFacing;
    private final MapCodec<CurvedBlock> codec;

    public CurvedBlock(Properties properties, int heightVoxels) {
        super(properties);
        this.rightShapesByFacing = CurvedShapes.buildFacingShapes(heightVoxels, ChamferSide.RIGHT);
        this.leftShapesByFacing = CurvedShapes.buildFacingShapes(heightVoxels, ChamferSide.LEFT);
        this.codec = simpleCodec(p -> new CurvedBlock(p, heightVoxels));
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.SOUTH).setValue(CHAMFER_SIDE, ChamferSide.RIGHT));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return codec;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, CHAMFER_SIDE);
    }

    private VoxelShape shapeFor(BlockState state) {
        Direction facing = state.getValue(FACING);
        return state.getValue(CHAMFER_SIDE) == ChamferSide.RIGHT
                ? rightShapesByFacing.get(facing)
                : leftShapesByFacing.get(facing);
    }

    @Override
    protected VoxelShape getShape(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapeFor(state);
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapeFor(state);
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
        BlockState rotated = state.setValue(FACING, mirror.mirror(state.getValue(FACING)));
        ChamferSide flipped = state.getValue(CHAMFER_SIDE) == ChamferSide.RIGHT ? ChamferSide.LEFT : ChamferSide.RIGHT;
        return rotated.setValue(CHAMFER_SIDE, flipped);
    }

    /** Exposed for tests: the precomputed shape for a given facing/side, with no world/level needed. */
    public VoxelShape shapeFor(Direction facing, ChamferSide side) {
        return side == ChamferSide.RIGHT ? rightShapesByFacing.get(facing) : leftShapesByFacing.get(facing);
    }
}
